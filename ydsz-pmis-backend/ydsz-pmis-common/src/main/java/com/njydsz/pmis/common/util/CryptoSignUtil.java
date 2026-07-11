package com.njydsz.pmis.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 通用加密签名工具（P1-1 架构优化）。
 *
 * <p>统一项目内 6+ 处独立实现的 HmacSHA256 签名逻辑，提供：
 * <ul>
 *   <li>{@link #hmacSha256Base64} — HMAC-SHA256 + Base64 编码（钉钉机器人加签等）</li>
 *   <li>{@link #hmacSha256Hex} — HMAC-SHA256 + Hex 编码（Webhook 签名等）</li>
 *   <li>{@link #constantTimeEquals} — 常量时间字符串比较（防时序攻击）</li>
 *   <li>{@link #verifySignature} — 签名验证（支持 Base64/Hex 两种编码）</li>
 * </ul>
 *
 * <h3>替代的位置</h3>
 * <ul>
 *   <li>{@code workflow/thirdparty/DingTalkSignatureUtil} — 钉钉回调签名验证</li>
 *   <li>{@code message/channel/impl/DingTalkChannel#appendSign()} — 钉钉机器人加签</li>
 *   <li>{@code common/webhook/WebhookDispatcher#hmacSha256()} — Webhook HMAC 签名</li>
 *   <li>{@code message/channel/sms/AliyunSmsSigner} — 阿里云短信签名</li>
 *   <li>{@code workflow/thirdparty/FeishuSignatureUtil} — 飞书签名</li>
 *   <li>{@code workflow/thirdparty/WeComSignatureUtil} — 企微签名</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P1-1)
 */
public final class CryptoSignUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private CryptoSignUtil() {
    }

    // ==================== HMAC-SHA256 签名计算 ====================

    /**
     * 计算 HMAC-SHA256 签名，返回 Base64 编码字符串。
     *
     * <p>适用于钉钉机器人加签（timestamp + "\n" + secret）。
     *
     * @param data   待签名数据
     * @param secret 密钥
     * @return Base64 编码的签名；异常时返回空字符串
     */
    public static String hmacSha256Base64(String data, String secret) {
        byte[] hash = computeHmacSha256(data, secret);
        return hash != null ? Base64.getEncoder().encodeToString(hash) : "";
    }

    /**
     * 计算 HMAC-SHA256 签名，返回 Hex 编码字符串。
     *
     * <p>适用于 Webhook 签名（X-Webhook-Signature 头）。
     *
     * @param data   待签名数据
     * @param secret 密钥
     * @return Hex 编码的签名；异常时返回空字符串
     */
    public static String hmacSha256Hex(String data, String secret) {
        byte[] hash = computeHmacSha256(data, secret);
        return hash != null ? HexFormat.of().formatHex(hash) : "";
    }

    /**
     * 计算 HMAC-SHA256 签名字节数组。
     *
     * @param data   待签名数据
     * @param secret 密钥
     * @return 签名字节数组；异常时返回 null
     */
    private static byte[] computeHmacSha256(String data, String secret) {
        if (data == null || secret == null || secret.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 签名验证 ====================

    /**
     * 验证签名（常量时间比较，防时序攻击）。
     *
     * @param data            原始数据
     * @param secret          密钥
     * @param signature       待验证的签名
     * @param encoding        签名编码方式
     * @return true 表示签名验证通过
     */
    public static boolean verifySignature(String data, String secret,
                                           String signature, SignatureEncoding encoding) {
        if (signature == null || signature.isEmpty() || secret == null || secret.isEmpty()) {
            return false;
        }
        String computed = switch (encoding) {
            case BASE64 -> hmacSha256Base64(data, secret);
            case HEX -> hmacSha256Hex(data, secret);
        };
        return constantTimeEquals(computed, signature);
    }

    /**
     * 验证签名（自动推断编码：Base64 或 Hex）。
     *
     * @param data      原始数据
     * @param secret    密钥
     * @param signature 待验证的签名
     * @return true 表示签名验证通过
     */
    public static boolean verifySignature(String data, String secret, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        // Hex 编码只包含 0-9a-f，Base64 可能包含 +/= 等字符
        boolean looksLikeHex = signature.matches("[0-9a-fA-F]+");
        SignatureEncoding encoding = looksLikeHex ? SignatureEncoding.HEX : SignatureEncoding.BASE64;
        return verifySignature(data, secret, signature, encoding);
    }

    // ==================== 常量时间比较 ====================

    /**
     * 常量时间字符串比较，防止时序攻击。
     *
     * <p>无论字符是否匹配，比较耗时恒定，避免攻击者通过响应时间推断正确字符。
     *
     * @param a 字符串 A
     * @param b 字符串 B
     * @return true 表示两个字符串相等
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    // ==================== 签名编码枚举 ====================

    /**
     * 签名编码方式。
     */
    public enum SignatureEncoding {
        /** Base64 编码 */
        BASE64,
        /** Hex 编码（小写） */
        HEX
    }
}
