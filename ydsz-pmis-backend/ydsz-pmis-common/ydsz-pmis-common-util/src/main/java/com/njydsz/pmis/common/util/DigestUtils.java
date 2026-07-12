package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具类
 *
 * <p>提供 MD5、SHA-1、SHA-256 等摘要算法。
 * 对标 remi-comm DigestUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class DigestUtils {

    private DigestUtils() {
    }

    /**
     * MD5 摘要
     *
     * @param input 输入字符串
     * @return 32位十六进制字符串
     */
    public static String md5Hex(String input) {
        return digestHex("MD5", input);
    }

    /**
     * SHA-1 摘要
     *
     * @param input 输入字符串
     * @return 40位十六进制字符串
     */
    public static String sha1Hex(String input) {
        return digestHex("SHA-1", input);
    }

    /**
     * SHA-256 摘要
     *
     * @param input 输入字符串
     * @return 64位十六进制字符串
     */
    public static String sha256Hex(String input) {
        return digestHex("SHA-256", input);
    }

    /**
     * SHA-512 摘要
     *
     * @param input 输入字符串
     * @return 128位十六进制字符串
     */
    public static String sha512Hex(String input) {
        return digestHex("SHA-512", input);
    }

    /**
     * 计算字节数组的摘要
     *
     * @param algorithm 算法名
     * @param bytes     字节数组
     * @return 十六进制字符串
     */
    public static String digestHex(String algorithm, byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(bytes);
            return HexUtils.toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Digest algorithm not found: " + algorithm, e);
        }
    }

    /**
     * 计算字符串的摘要
     *
     * @param algorithm 算法名
     * @param input     输入字符串
     * @return 十六进制字符串
     */
    public static String digestHex(String algorithm, String input) {
        if (input == null) {
            return null;
        }
        return digestHex(algorithm, input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * MD5 摘要（字节数组）
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    public static String md5Hex(byte[] bytes) {
        return digestHex("MD5", bytes);
    }

    /**
     * SHA-256 摘要（字节数组）
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    public static String sha256Hex(byte[] bytes) {
        return digestHex("SHA-256", bytes);
    }
}
