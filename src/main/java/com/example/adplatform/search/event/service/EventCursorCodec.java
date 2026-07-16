package com.example.adplatform.search.event.service;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * ES {@code search_after} 排序值与外部游标之间的编解码器。
 *
 * <p>游标内容是 {@code [eventTime, eventId]} 两个排序值的 URL-safe Base64 JSON。
 * API 使用者只需原样回传；服务端校验元素数量，避免无效游标生成不可预测查询。</p>
 */
@Component
@RequiredArgsConstructor
public class EventCursorCodec {

    private static final TypeReference<List<Object>> VALUES_TYPE = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    /** 将最后一条命中的 ES sortValues 编码为 URL 可传输字符串。 */
    public String encode(List<Object> sortValues) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(sortValues);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot encode event search cursor", ex);
        }
    }

    /** 空游标表示第一页，格式非法时转换为统一业务参数异常。 */
    public List<Object> decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return List.of();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.UTF_8));
            List<Object> values = objectMapper.readValue(json, VALUES_TYPE);
            if (values.size() != 2) {
                throw new IllegalArgumentException("Unexpected cursor values");
            }
            return values;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "事件检索游标无效");
        }
    }
}
