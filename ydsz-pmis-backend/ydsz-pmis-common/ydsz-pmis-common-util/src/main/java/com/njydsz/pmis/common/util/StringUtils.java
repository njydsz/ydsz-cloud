package com.njydsz.pmis.common.util;

/**
 * 字符串工具类
 *
 * <p>补充 Apache Commons Lang / Hutool 未覆盖的场景。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * 判断字符串是否为空或 null
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 判断字符串是否非空且非空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 驼峰转下划线（camelCase → snake_case）
     */
    public static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) return camelCase;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 下划线转驼峰（snake_case → camelCase）
     */
    public static String snakeToCamel(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) return snakeCase;
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < snakeCase.length(); i++) {
            char c = snakeCase.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 脱敏处理（保留首尾指定字符数）
     */
    public static String mask(String str, int prefixKeep, int suffixKeep) {
        if (str == null || str.length() <= prefixKeep + suffixKeep) {
            return str;
        }
        int maskLen = str.length() - prefixKeep - suffixKeep;
        return str.substring(0, prefixKeep) + "*".repeat(Math.max(1, maskLen)) +
                str.substring(str.length() - suffixKeep);
    }

    /**
     * 生成指定长度的随机字符串
     */
    public static String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
