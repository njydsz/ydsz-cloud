package com.njydsz.pmis.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 随机工具类
 *
 * <p>提供随机数、随机字符串、UUID 等生成方法。
 * 对标 remi-comm RandomUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class RandomUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789";
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHANUMERIC = DIGITS + LETTERS;
    private static final String HEX_DIGITS = "0123456789abcdef";

    private RandomUtils() {
    }

    /**
     * 生成不带横线的 UUID
     *
     * @return UUID 字符串（32位）
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成带横线的 UUID
     *
     * @return UUID 字符串（36位）
     */
    public static String uuidWithHyphen() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成随机数字字符串
     *
     * @param length 长度
     * @return 随机数字字符串
     */
    public static String randomDigits(int length) {
        return randomString(DIGITS, length);
    }

    /**
     * 生成随机字母字符串
     *
     * @param length 长度
     * @return 随机字母字符串
     */
    public static String randomLetters(int length) {
        return randomString(LETTERS, length);
    }

    /**
     * 生成随机字母数字字符串
     *
     * @param length 长度
     * @return 随机字母数字字符串
     */
    public static String randomAlphanumeric(int length) {
        return randomString(ALPHANUMERIC, length);
    }

    /**
     * 生成随机十六进制字符串
     *
     * @param length 长度
     * @return 随机十六进制字符串
     */
    public static String randomHex(int length) {
        return randomString(HEX_DIGITS, length);
    }

    /**
     * 从指定字符集中生成随机字符串
     *
     * @param chars   字符集
     * @param length  长度
     * @return 随机字符串
     */
    public static String randomString(String chars, int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成随机 byte 数组
     *
     * @param length 长度
     * @return 随机 byte 数组
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成随机整数（指定范围）
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        return min + SECURE_RANDOM.nextInt(max - min + 1);
    }

    /**
     * 生成随机 long
     *
     * @return 随机 long
     */
    public static long randomLong() {
        return SECURE_RANDOM.nextLong();
    }

    /**
     * 生成验证码（默认6位数字）
     *
     * @return 验证码
     */
    public static String verificationCode() {
        return randomDigits(6);
    }

    /**
     * 生成验证码
     *
     * @param length 长度
     * @return 验证码
     */
    public static String verificationCode(int length) {
        return randomDigits(length);
    }
}
