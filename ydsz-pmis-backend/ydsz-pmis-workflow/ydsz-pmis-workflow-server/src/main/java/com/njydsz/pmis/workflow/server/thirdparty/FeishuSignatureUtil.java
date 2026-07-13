package com.njydsz.pmis.workflow.server.thirdparty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 飞书回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK — 飞书回调签名验证。
 * <p>算法：SHA256(timestamp + nonce + encrypt + appSecret)，结果以十六进制小写编码后与回调签名比对。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class FeishuSignatureUtil {

    private static final Logger log = LoggerFactory.getLogger(FeishuSignatureUtil.class);

    private static final String SHA_256 = "SHA-256";

    private FeishuSignatureUtil() {
    }

    /**
     * 验证飞书回调签名
     *
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param encrypt   加密载荷
     * @param signature 回调签名（十六进制）
     * @param appSecret 应用 appSecret
     * @return 签名校验通过返回 true，否则 false
     */
    public static boolean verifySignature(String timestamp, String nonce, String encrypt,
                                          String signature, String appSecret) {
        if (signature == null || signature.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            return false;
        }
        try {
            String data = str(timestamp) + str(nonce) + str(encrypt) + appSecret;
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            String computed = toHexLower(digest);
            return constantTimeEquals(computed, signature.toLowerCase());
        } catch (Exception e) {
            log.warn("[FeishuSignatureUtil] 签名验证异常 timestamp={}: {}", timestamp, e.getMessage(), e);
            return false;
        }
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(hex[(b >> 4) & 0x0F]);
            sb.append(hex[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * 常量时间字符串比较，避免时序攻击
     */
    private static boolean constantTimeEquals(String a, String b) {
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
}
