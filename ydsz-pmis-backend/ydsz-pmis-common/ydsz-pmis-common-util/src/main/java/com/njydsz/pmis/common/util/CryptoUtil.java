package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类（兼容旧 com.njydsz.pmis.common.util.CryptoUtil）。
 *
 * <p>提供 BCrypt 和 SHA-256+Salt 两种密码哈希与验证功能。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CryptoUtil {

    /** BCrypt 格式前缀正则 */
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /** Spring Security BCrypt 编码器（线程安全） */
    private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder(12);

    private CryptoUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 使用 BCrypt 哈希密码。
     *
     * @param rawPassword 原始密码
     * @return BCrypt 哈希值
     */
    public static String hashPasswordBCrypt(String rawPassword) {
        return BCRYPT_ENCODER.encode(rawPassword);
    }

    /**
     * 验证 BCrypt 密码。
     *
     * @param rawPassword 原始密码
     * @param hashedPassword BCrypt 哈希值
     * @return 匹配返回 true
     */
    public static boolean verifyPasswordBCrypt(String rawPassword, String hashedPassword) {
        return BCRYPT_ENCODER.matches(rawPassword, hashedPassword);
    }

    /**
     * 判断密码是否为 BCrypt 格式。
     *
     * @param password 密码字符串
     * @return 是 BCrypt 格式返回 true
     */
    public static boolean isBCryptFormat(String password) {
        return password != null && BCRYPT_PATTERN.matcher(password).matches();
    }

    /**
     * 使用 SHA-256 + Salt 验证密码（兼容旧版密码）。
     *
     * @param rawPassword 原始密码
     * @param hashedPassword 已存储的哈希值（Hex 编码）
     * @param salt 盐值
     * @return 匹配返回 true
     */
    public static boolean verifyPassword(String rawPassword, String hashedPassword, String salt) {
        if (rawPassword == null || hashedPassword == null || salt == null) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().equalsIgnoreCase(hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Base64 URL-safe 编码（无填充）。
     *
     * @param data 待编码的字节数组
     * @return Base64 URL-safe 字符串
     */
    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Base64 URL-safe 解码。
     *
     * @param value Base64 URL-safe 字符串
     * @return 解码后的字节数组
     */
    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /**
     * 恒定时间字符串比较（防止计时攻击）。
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return 相等返回 true
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * HMAC-SHA256 签名（返回 Base64 URL-safe 编码）。
     *
     * @param data 待签名数据
     * @param key  密钥字节数组
     * @return Base64 URL-safe 编码的签名
     */
    public static String hmacSha256(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败: " + e.getMessage(), e);
        }
    }
}
