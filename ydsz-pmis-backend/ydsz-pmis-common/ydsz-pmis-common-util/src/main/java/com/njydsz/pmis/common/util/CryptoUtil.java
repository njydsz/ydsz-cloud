package com.njydsz.pmis.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.njydsz.pmis.common.util.security.DigestUtils;
import com.njydsz.pmis.common.util.security.PwdUtils;

/**
 * 密码加密工具类（已废弃，请使用 {@link PwdUtils} 和 {@link DigestUtils}）。
 *
 * <p>BCrypt 相关方法请使用 {@link PwdUtils}，HMAC/签名相关方法请使用 {@link DigestUtils}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请使用 {@link PwdUtils}（BCrypt/密码哈希）和 {@link DigestUtils}（HMAC/签名/常量时间比较）
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public final class CryptoUtil {

    private CryptoUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 使用 BCrypt 哈希密码。
     *
     * @param rawPassword 原始密码
     * @return BCrypt 哈希值
     * @deprecated 请使用 {@link PwdUtils#hashPasswordBCrypt(String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String hashPasswordBCrypt(String rawPassword) {
        return PwdUtils.hashPasswordBCrypt(rawPassword);
    }

    /**
     * 验证 BCrypt 密码。
     *
     * @param rawPassword 原始密码
     * @param hashedPassword BCrypt 哈希值
     * @return 匹配返回 true
     * @deprecated 请使用 {@link PwdUtils#verifyPasswordBCrypt(String, String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static boolean verifyPasswordBCrypt(String rawPassword, String hashedPassword) {
        return PwdUtils.verifyPasswordBCrypt(rawPassword, hashedPassword);
    }

    /**
     * 判断密码是否为 BCrypt 格式。
     *
     * @param password 密码字符串
     * @return 是 BCrypt 格式返回 true
     * @deprecated 请使用 {@link PwdUtils#isBCryptFormat(String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static boolean isBCryptFormat(String password) {
        return PwdUtils.isBCryptFormat(password);
    }

    /**
     * 使用 SHA-256 + Salt 验证密码（兼容旧版密码）。
     *
     * @param rawPassword 原始密码
     * @param hashedPassword 已存储的哈希值（Hex 编码）
     * @param salt 盐值
     * @return 匹配返回 true
     * @deprecated 请使用 {@link PwdUtils#verifyPasswordWithSha256Salt(String, String, String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static boolean verifyPassword(String rawPassword, String hashedPassword, String salt) {
        return PwdUtils.verifyPasswordWithSha256Salt(rawPassword, hashedPassword, salt);
    }

    /**
     * Base64 URL-safe 编码（无填充）。
     *
     * @param data 待编码的字节数组
     * @return Base64 URL-safe 字符串
     * @deprecated 请直接使用 {@code Base64.getUrlEncoder().withoutPadding().encodeToString(data)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Base64 URL-safe 解码。
     *
     * @param value Base64 URL-safe 字符串
     * @return 解码后的字节数组
     * @deprecated 请直接使用 {@code Base64.getUrlDecoder().decode(value)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /**
     * 恒定时间字符串比较（防止计时攻击）。
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return 相等返回 true
     * @deprecated 请使用 {@link DigestUtils#constantTimeEquals(String, String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static boolean constantTimeEquals(String a, String b) {
        return DigestUtils.constantTimeEquals(a, b);
    }

    /**
     * HMAC-SHA256 签名（返回 Base64 URL-safe 编码）。
     *
     * @param data 待签名数据
     * @param key  密钥字节数组
     * @return Base64 URL-safe 编码的签名
     * @deprecated 请使用 {@link DigestUtils#hmacSha256UrlSafe(String, byte[])}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String hmacSha256(String data, byte[] key) {
        return DigestUtils.hmacSha256UrlSafe(data, key);
    }
}
