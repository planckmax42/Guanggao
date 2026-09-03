package com.example.adplatform.common.response;

/**
 * 创建资源后的轻量返回值。
 * publicId 是服务端生成的不可变公开标识，bizKey 用于返回可读的业务标识。
 */
public record ResourceRefResponse(String publicId, String bizKey) {
}
