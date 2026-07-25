package com.njydsz.common.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 摘要计算工具类。
 *
 * <p>统一提供 SHA-256 摘要计算方法，消除 {@code TokenBlacklistService}、
 * {@code TokenBlacklistBloomFilter}、{@code DefaultCacheKeyStrategy} 等类中的重复实现。
 *
 * @author ydsz-team
 * @since 1.0.0

 */
public final class AuthDigestUtils {

    private AuthDigestUtils() {
    }

    /**
     * 计算字符串的 SHA-256 摘要并返回十六进制编码。
     *
     * @param input 输入字符串
     * @return 64 字符的十六进制摘要字符串
     */
    public static String sha256Hex(String input) {
        byte[] hashBytes = sha256Bytes(input);
        return HexFormat.of().formatHex(hashBytes);
    }

    /**
     * 计算字符串的 SHA-256 摘要并返回字节数组。
     *
     * @param input 输入字符串
     * @return 32 字节的摘要
     */
    public static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
