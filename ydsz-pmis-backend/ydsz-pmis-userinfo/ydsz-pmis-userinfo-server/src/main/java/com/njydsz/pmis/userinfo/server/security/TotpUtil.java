package com.njydsz.pmis.userinfo.server.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

/**
 * TOTP（Time-based One-Time Password）工具（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-pmis-common-security 包，因 common 重构后该工具类已迁移到各业务模块本地化。
 * 实现 RFC 6238，30 秒时间窗，6 位数字验证码，HMAC-SHA1 算法。
 *
 * <p>使用示例：
 * <pre>{@code
 * String secret = TotpUtil.generateSecret();
 * String otp = TotpUtil.currentOtp(secret);
 * boolean ok = TotpUtil.verify(secret, otp);
 * String[] backups = TotpUtil.generateBackupCodes(8);
 * String uri = TotpUtil.otpAuthUri("alice@pmis.io", "PMIS", secret);
 * }</pre>
 *
 * <p>依赖：{@code commons-codec}（用于 Base32 编码）。
 *
 * @since 1.0.0
 */
@Slf4j
public final class TotpUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 时间窗 30 秒 */
    private static final long TIME_STEP_SECONDS = 30L;
    /** TOTP 容忍 ±1 个时间窗（30 秒）以兼容客户端时钟漂移 */
    private static final int TOLERANCE_STEPS = 1;
    /** 验证码位数 */
    private static final int DIGITS = 6;
    /** RFC 4648 Base32 字母表 */
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 Base32 编码的 TOTP 密钥（160 bit = 32 字符）
     */
    public static String generateSecret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * 生成一次性备份码（每组 8 位十六进制字符串）
     *
     * @param count 备份码数量
     */
    public static String[] generateBackupCodes(int count) {
        String[] codes = new String[count];
        for (int i = 0; i < count; i++) {
            int val = RANDOM.nextInt(0x10000000); // 7 位十六进制
            codes[i] = String.format("%07x", val);
        }
        return codes;
    }

    /**
     * 校验用户输入的 OTP
     *
     * @param secret 用户密钥
     * @param otp    用户输入的 6 位验证码
     */
    public static boolean verify(String secret, String otp) {
        if (secret == null || secret.isBlank() || otp == null || otp.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        for (int i = -TOLERANCE_STEPS; i <= TOLERANCE_STEPS; i++) {
            String candidate = generateOtp(secret, now + i * TIME_STEP_SECONDS);
            if (constantTimeEquals(candidate, otp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成 otpauth:// URI，用于 Google Authenticator / Authy 等扫码
     */
    public static String otpAuthUri(String account, String issuer, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8);
        String params = "secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1"
                + "&digits=" + DIGITS
                + "&period=" + TIME_STEP_SECONDS;
        return "otpauth://totp/" + label + "?" + params;
    }

    private static String generateOtp(String secret, long timeSeconds) {
        long timeStep = timeSeconds / TIME_STEP_SECONDS;
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (timeStep & 0xff);
            timeStep >>= 8;
        }
        byte[] key = decodeBase32(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            log.error("[TotpUtil] HMAC-SHA1 失败: {}", e.getMessage());
            return "000000";
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * RFC 4648 Base32 编码（无 padding）
     */
    private static String encodeBase32(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1f;
                sb.append(BASE32_ALPHABET.charAt(idx));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1f;
            sb.append(BASE32_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    /**
     * RFC 4648 Base32 解码（容许缺 padding 与小写）
     */
    private static byte[] decodeBase32(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }
        String s = input.toUpperCase().replace("=", "").trim();
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) {
                continue; // 跳过非法字符
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
