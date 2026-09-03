package com.example.adplatform.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "成功", HttpStatus.OK),
    PARAM_VALIDATION_FAILED(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    PARAM_TYPE_MISMATCH(40002, "参数类型不匹配", HttpStatus.BAD_REQUEST),
    REQUEST_BODY_INVALID(40003, "请求体格式错误", HttpStatus.BAD_REQUEST),
    BUSINESS_ERROR(50001, "业务处理失败", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(50002, "资源不存在", HttpStatus.NOT_FOUND),
    TOO_MANY_REQUESTS(50003, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    DUPLICATE_RESOURCE(50004, "资源已存在", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION(50005, "状态流转不合法", HttpStatus.BAD_REQUEST),
    INVALID_TIME_RANGE(50006, "时间范围不合法", HttpStatus.BAD_REQUEST),
    INVALID_BUDGET(50007, "预算配置不合法", HttpStatus.BAD_REQUEST),
    MYSQL_CONNECTION_FAILED(50010, "MySQL 连接失败", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_CONNECTION_FAILED(50011, "Redis 连接失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DEPENDENCY_SERVICE_UNAVAILABLE(50012, "依赖服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_ERROR(99999, "系统异常", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;//todo:如果对外提供Kafka服务，可以加上单独转换层

}
