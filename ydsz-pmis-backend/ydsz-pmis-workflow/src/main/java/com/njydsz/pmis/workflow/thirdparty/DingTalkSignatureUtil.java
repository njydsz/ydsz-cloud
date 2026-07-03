package com.njydsz.pmis.workflow.thirdparty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钉钉回调签名验证工具
 *
 * <p>P0-2: 三方审批 SDK — 钉钉回调签名验证。
 * <p>算法：HmacSHA256，密钥为 appSecret，签名内容为 timestamp + nonce + encrypt，
 * 计算结果经 Base64 编码后与回调签名比对。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class DingTalkSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private DingTalkSignatureUtil() {
    }

    /**
     * 验证钉钉回调签名
     *
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param encrypt   加密载荷
     * @param signature 回调签名（Base64）
     * @param appSecret 应用 appSecret（作为 HmacSHA256 密钥）
     * @return 签名校验通过返回 true，否则 false
     */
    public static boolean verifySignature(String timestamp, String nonce, String encrypt,
                                          String signature, String appSecret) {
        if (signature == null || signature.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            return false;
        }
        try {
            String data = str(timestamp) + str(nonce) + str(encrypt);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(signData);
            return constantTimeEquals(computed, signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(String s) {
        return s == null ? "" : s;
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
