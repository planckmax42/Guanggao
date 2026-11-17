package com.example.adplatform.infra.redis.delivery.slot.MQ;

public record RedisOperationResult(RedisOperationStatus status ,Long slotId) {
    public static RedisOperationResult success(){
        return new RedisOperationResult(RedisOperationStatus.SUCCESS,null);
    }
    public static RedisOperationResult success(Long slotId){
        return new RedisOperationResult(RedisOperationStatus.SUCCESS,slotId);
    }
    public static RedisOperationResult cacheUnfound(){
        return new RedisOperationResult(RedisOperationStatus.CACHE_UNFOUND,null);
    }
    public static RedisOperationResult cacheValueInvalid(){
        return new RedisOperationResult(RedisOperationStatus.CACHE_VALUE_INVALID,null);
    }
}
