package com.njydsz.pmis.common.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP（基于时间的一次性密码）工具
 *
 * <p>遵循 RFC 6238，使用 HMAC-SHA1，时间步长 30 秒，6 位密码。
 *
 * <p>用法：
 * <pre>
 *   String secret = TotpUtil.generateSecret();
 *   String otp = TotpUtil.generate(secret, Instant.now().getEpochSecond());
 *   boolean ok = TotpUtil.verify(secret, otp); // 允许 ±1 步漂移
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public final class TotpUtil {

    private static final int SECRET_BYTES = 20;
    private static final int TIME_STEP = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1; // 前后允许 1 个时间步

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /**
     * 生成 20 字节 Base32 编码的密钥
     */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    /**
     * 生成 8 组一次性备份码（每组 8 位十六进制）
     */
    public static String[] generateBackupCodes(int count) {
        String[] codes = new String[count];
        for (int i = 0; i < count; i++) {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            codes[i] = sb.toString();
        }
        return codes;
    }

    /**
     * 生成指定时间点的 OTP
     */
    public static String generate(String base32Secret, long unixSeconds) {
        long counter = unixSeconds / TIME_STEP;
        return generateWithCounter(base32Secret, counter);
    }

    /**
     * 生成默认时间点的 OTP
     */
    public static String generate(String base32Secret) {
        return generate(base32Secret, Instant.now().getEpochSecond());
    }

    private static String generateWithCounter(String base32Secret, long counter) {
        try {
            byte[] keyBytes = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] hmac = mac.doFinal(data);
            int offset = hmac[hmac.length - 1] & 0x0F;
            int binary = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            log.error("[TOTP] 生成 OTP 失败", e);
            return "000000";
        }
    }

    /**
     * 校验 OTP（允许前后各 WINDOW 个时间步漂移）
     */
    public static boolean verify(String base32Secret, String otp) {
        if (base32Secret == null || otp == null || otp.length() != DIGITS) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        for (int i = -WINDOW; i <= WINDOW; i++) {
            String expected = generate(base32Secret, now + i * TIME_STEP);
            if (constantTimeEquals(expected, otp)) {
                return true;
            }
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 校验备用码
     */
    public static boolean verifyBackupCode(String input, String[] storedCodes) {
        if (input == null || storedCodes == null) return false;
        for (String code : storedCodes) {
            if (constantTimeEquals(code.toLowerCase(), input.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Base32 编码
     */
    public static String encodeBase32(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        int buffer = data[0] & 0xff;
        int bitsLeft = 8;
        int idx = 1;
        while (bitsLeft > 0 || idx < data.length) {
            if (bitsLeft < 5) {
                if (idx < data.length) {
                    buffer <<= 8;
                    buffer |= data[idx++] & 0xff;
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = (buffer >> (bitsLeft - 5)) & 0x1F;
            bitsLeft -= 5;
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Base32 解码
     */
    public static byte[] decodeBase32(String base32) {
        if (base32 == null) return new byte[0];
        String s = base32.toUpperCase().replace("=", "").replaceAll("\\s", "");
        int outLen = s.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;
        for (char c : s.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Invalid Base32 char: " + c);
            }
            buffer <<= 5;
            buffer |= v;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    /**
     * 生成 otpauth:// URI（用于 Google Authenticator 等）
     */
    public static String otpAuthUri(String account, String issuer, String secret) {
        return "otpauth://totp/" + issuer + ":" + account
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP;
    }

    public static int getDigits() {
        return DIGITS;
    }

    public static int getTimeStep() {
        return TIME_STEP;
    }
}
