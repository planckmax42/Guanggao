package com.example.adplatform.infra.redis.delivery.slot.MQ;

import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.portMQ.DeliverySlotCachePort;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static com.example.adplatform.common.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements DeliverySlotCachePort {

    private static final String READ_METRIC = "ad.redis.slot.read";
    private static final String DELETE_METRIC = "ad.redis.slot.delete";
    private static final String WRITE_METRIC = "ad.redis.slot.write";
    private static final String RESULT_TAG = "result";
    private static final Duration TTL = Duration.ofMinutes(30);

    private RedisCircuitBreakerPort circuitBreaker;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    public RedisOperationResult getSlotIdFromRedis(String slotCode){
        long startTime = System.nanoTime();
        try {
            String slotId = circuitBreaker.executeSupplier(() -> stringRedisTemplate.opsForValue()
                    .get(DeliveryRedisKeys.slotCodeToId(slotCode)));
            if (hasText(slotId)){
                recordMetrics(RedisOperationStatus.SUCCESS.toString(),READ_METRIC, startTime);
                return RedisOperationResult.success(Long.valueOf(slotId));
            }else {
                recordMetrics(RedisOperationStatus.CACHE_UNFOUND.toString(),READ_METRIC, startTime);
                return RedisOperationResult.cacheUnfound();
            }
        }
        catch (NumberFormatException exception){
            recordMetrics(RedisOperationStatus.CACHE_VALUE_INVALID.toString(),READ_METRIC, startTime);
            return RedisOperationResult.cacheValueInvalid();
        }
        catch (CallNotPermittedException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"熔断器已熔断");
        }
        catch (DataAccessException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"缓存服务不可用");
        }
    }

    public RedisOperationResult deleteSlotIdFromRedis(String slotCode){
        long startTime = System.nanoTime();
        try {
            if(circuitBreaker.executeSupplier(() ->
                    stringRedisTemplate.delete(DeliveryRedisKeys.slotCodeToId(slotCode))))
            {
                recordMetrics(RedisOperationStatus.SUCCESS.toString(),DELETE_METRIC,startTime);
                return RedisOperationResult.success();
            }
            else{
                recordMetrics(RedisOperationStatus.CACHE_UNFOUND.toString(),DELETE_METRIC,startTime);
                return RedisOperationResult.cacheUnfound();
            }
        }
        catch (CallNotPermittedException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"熔断器已熔断");
        }
        catch (DataAccessException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"缓存服务不可用");
        }
    }
    public RedisOperationResult writeSlotIdFromRedis(String slotCode,Long slotId){
        long startTime = System.nanoTime();
        try {
            circuitBreaker.executeRunnable(() ->
                stringRedisTemplate.opsForValue().set(
                        DeliveryRedisKeys.slotCodeToId(slotCode),
                        String.valueOf(slotId),
                        TTL));
            recordMetrics(RedisOperationStatus.SUCCESS.toString(),WRITE_METRIC,startTime);
            return RedisOperationResult.success();
        }
        catch (CallNotPermittedException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"熔断器已熔断");
        }
        catch (DataAccessException exception){
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,"缓存服务不可用");
        }
    }
    private void recordMetrics(String result ,String operation,long startTime){
        meterRegistry.timer(operation, RESULT_TAG, result)
                .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

}
