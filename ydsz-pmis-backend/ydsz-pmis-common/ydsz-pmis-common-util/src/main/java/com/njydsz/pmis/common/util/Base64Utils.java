package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编解码工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class Base64Utils {

    private Base64Utils() {
    }

    public static String encode(String input) {
        if (input == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String encodeURLSafe(String input) {
        if (input == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String input) {
        if (input == null) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(input);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public static byte[] decodeToBytes(String input) {
        if (input == null) {
            return null;
        }
        return Base64.getDecoder().decode(input);
    }

    public static String decodeURLSafe(String input) {
        if (input == null) {
            return null;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(input);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
