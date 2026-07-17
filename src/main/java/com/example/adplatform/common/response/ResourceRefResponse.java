package com.example.adplatform.common.response;

/**
 * 创建资源后的轻量返回值。
 * id 用于后台管理继续操作资源，bizKey 用于返回 slotCode 等更稳定的业务标识。
 */
public record ResourceRefResponse(Long id, String bizKey) {
}
