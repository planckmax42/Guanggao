package com.example.adplatform.common.util;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class StringUtils {

    /** 将空白字符串转换为 null，其他字符串原样保留。 */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static boolean hasText(String string) {
        return string != null && !string.isBlank();
    }
}
