package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 密码工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class PwdUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_SALT_LENGTH = 16;
    private static final int DEFAULT_ITERATIONS = 10000;
    private static final int DEFAULT_KEY_LENGTH = 256;

    private PwdUtils() {
    }

    /**
     * 生成随机盐值
     *
     * @return 盐值（十六进制字符串）
     */
    public static String generateSalt() {
        return generateSalt(DEFAULT_SALT_LENGTH);
    }

    /**
     * 生成随机盐值
     *
     * @param length 长度
     * @return 盐值（十六进制字符串）
     */
    public static String generateSalt(int length) {
        byte[] salt = new byte[length];
        RANDOM.nextBytes(salt);
        return HexUtils.toHex(salt);
    }

    /**
     * 生成密码哈希（PBKDF2）
     *
     * @param password   密码
     * @param salt       盐值
     * @return 哈希值（十六进制字符串）
     */
    public static String hash(String password, String salt) {
        return hash(password, salt, DEFAULT_ITERATIONS, DEFAULT_KEY_LENGTH);
    }

    /**
     * 生成密码哈希（PBKDF2）
     *
     * @param password   密码
     * @param salt       盐值
     * @param iterations 迭代次数
     * @param keyLength  密钥长度
     * @return 哈希值（十六进制字符串）
     */
    public static String hash(String password, String salt, int iterations, int keyLength) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(),
                    HexUtils.fromHex(salt),
                    iterations,
                    keyLength
            );
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexUtils.toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hash failed", e);
        }
    }

    /**
     * 验证密码
     *
     * @param password     密码
     * @param salt         盐值
     * @param expectedHash 期望的哈希值
     * @return true 如果密码正确
     */
    public static boolean verify(String password, String salt, String expectedHash) {
        String actualHash = hash(password, salt);
        return constantTimeEquals(actualHash, expectedHash);
    }

    /**
     * 生成随机密码
     *
     * @param length 长度
     * @return 随机密码
     */
    public static String generateRandomPassword(int length) {
        return RandomUtils.randomAlphanumeric(length);
    }

    /**
     * 评估密码强度
     *
     * @param password 密码
     * @return 强度等级：WEAK, MEDIUM, STRONG
     */
    public static PasswordStrength evaluateStrength(String password) {
        if (password == null || password.length() < 8) {
            return PasswordStrength.WEAK;
        }
        int score = 0;
        if (password.length() >= 12) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        if (score >= 4) return PasswordStrength.STRONG;
        if (score >= 3) return PasswordStrength.MEDIUM;
        return PasswordStrength.WEAK;
    }

    /**
     * 常量时间比较（防时序攻击）
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 密码强度等级
     */
    public enum PasswordStrength {
        WEAK, MEDIUM, STRONG
    }
}
