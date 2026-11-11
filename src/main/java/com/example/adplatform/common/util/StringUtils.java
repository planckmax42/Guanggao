package com.example.adplatform.common.util;

public final class StringUtils {

    private StringUtils() {
    }

    /** 将空白字符串转换为 null，其他字符串原样保留。 */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
