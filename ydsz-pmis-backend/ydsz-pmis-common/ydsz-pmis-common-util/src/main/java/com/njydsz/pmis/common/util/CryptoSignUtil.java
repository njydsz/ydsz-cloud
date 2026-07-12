package com.njydsz.pmis.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 签名验证工具类（兼容旧 com.njydsz.pmis.common.util.CryptoSignUtil）。
 *
 * <p>提供 HMAC-SHA256 签名计算与验签功能，用于第三方回调验签等场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class CryptoSignUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private CryptoSignUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 签名编码格式。
     */
    public enum SignatureEncoding {
        /** Base64 编码 */
        BASE64,
        /** Hex 编码 */
        HEX
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 编码的签名。
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Base64 编码的签名
     */
    public static String hmacSha256Base64(String data, String secret) {
        byte[] hmac = computeHmacSha256(data, secret);
        return Base64.getEncoder().encodeToString(hmac);
    }

    /**
     * 计算 HMAC-SHA256 并返回 Hex 编码的签名。
     *
     * @param data   原始数据
     * @param secret 密钥
     * @return Hex 编码的签名
     */
    public static String hmacSha256Hex(String data, String secret) {
        byte[] hmac = computeHmacSha256(data, secret);
        return HexFormat.of().formatHex(hmac);
    }

    /**
     * 验证签名（常量时间比较，防止时序攻击）。
     *
     * @param data      原始数据
     * @param secret    密钥
     * @param signature 待验证的签名
     * @param encoding  签名编码格式
     * @return 验证通过返回 true
     */
    public static boolean verifySignature(String data, String secret, String signature, SignatureEncoding encoding) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String computed = encoding == SignatureEncoding.BASE64
                ? hmacSha256Base64(data, secret)
                : hmacSha256Hex(data, secret);
        return constantTimeEquals(computed, signature);
    }

    /**
     * 计算 HMAC-SHA256。
     */
    private static byte[] computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * 常量时间字符串比较（防止时序攻击）。
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
