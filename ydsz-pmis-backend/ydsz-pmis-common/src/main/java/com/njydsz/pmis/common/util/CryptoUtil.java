package com.njydsz.pmis.common.util;

import cn.hutool.core.util.StrUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CryptoUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private CryptoUtil() {
    }

    /**
     * MD5 加密
     */
    public static String md5(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 密码加盐 (MD5 + 随机盐)
     *
     * @return [加密密码, 盐]
     */
    public static String[] encryptPassword(String rawPassword) {
        String salt = randomSalt(8);
        String encrypted = md5(rawPassword + salt);
        return new String[]{encrypted, salt};
    }

    /**
     * 验证密码
     */
    public static boolean verifyPassword(String rawPassword, String encrypted, String salt) {
        if (StrUtil.hasBlank(rawPassword, encrypted, salt)) {
            return false;
        }
        return md5(rawPassword + salt).equals(encrypted);
    }

    /**
     * 生成随机盐
     */
    public static String randomSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SALT_CHARS.charAt(RANDOM.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Base64 编码
     */
    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Base64 解码
     */
    public static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }
}
