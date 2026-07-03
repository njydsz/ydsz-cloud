package com.njydsz.pmis.workflow.thirdparty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DingTalkSignatureUtil 单元测试
 *
 * <p>P0-2：覆盖钉钉回调签名验证工具的核心场景。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正确签名验证通过</li>
 *   <li>错误签名验证失败</li>
 *   <li>签名或 appSecret 为空时返回 false</li>
 *   <li>签名区分大小写（Base64 原样比较）</li>
 *   <li>非法参数不抛异常并返回 false</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class DingTalkSignatureUtilTest {

    private static final String TIMESTAMP = "1690000000456";
    private static final String NONCE = "dingtalk-nonce-def-456";
    private static final String ENCRYPT = "ding-encrypt-payload-uvw";
    private static final String APP_SECRET = "dingtalk-app-secret-demo";

    @Test
    @DisplayName("正确签名验证通过")
    void verifySignatureShouldReturnTrueWhenSignatureMatches() {
        String signature = computeDingTalkSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);

        boolean result = DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, signature, APP_SECRET);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("错误签名验证失败")
    void verifySignatureShouldReturnFalseWhenSignatureMismatch() {
        String signature = computeDingTalkSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);
        String tampered = tamperLastChar(signature);

        boolean result = DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, tampered, APP_SECRET);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("签名或 appSecret 为空时返回 false")
    void verifySignatureShouldReturnFalseWhenNullOrEmptyInputs() {
        String valid = computeDingTalkSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);

        assertThat(DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, null, APP_SECRET)).isFalse();
        assertThat(DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, "", APP_SECRET)).isFalse();
        assertThat(DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, valid, null)).isFalse();
        assertThat(DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, valid, "")).isFalse();
    }

    @Test
    @DisplayName("签名区分大小写（Base64 原样比较）")
    void verifySignatureShouldBeCaseSensitive() {
        String signature = computeDingTalkSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);
        String swapped = swapCase(signature);
        // 校验确实存在字母位，保证测试有意义
        assertThat(swapped).isNotEqualTo(signature);

        boolean result = DingTalkSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, swapped, APP_SECRET);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("非法参数不抛异常并返回 false")
    void verifySignatureShouldNotThrowOnInvalidInput() {
        assertThatCode(() -> DingTalkSignatureUtil.verifySignature(
                null, null, null, "invalid-sig", APP_SECRET)).doesNotThrowAnyException();
        assertThat(DingTalkSignatureUtil.verifySignature(
                null, null, null, "invalid-sig", APP_SECRET)).isFalse();
    }

    // ============ 辅助方法：在测试中复现钉钉签名算法 ============

    /**
     * 复现钉钉签名算法：HmacSHA256（密钥=appSecret），签名内容 timestamp+nonce+encrypt，Base64 编码。
     */
    private String computeDingTalkSignature(String timestamp, String nonce, String encrypt, String appSecret) {
        try {
            String data = str(timestamp) + str(nonce) + str(encrypt);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String str(String s) {
        return s == null ? "" : s;
    }

    /**
     * 翻转字符串中字母的大小写，用于验证 Base64 签名的大小写敏感性。
     */
    private String swapCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLowerCase(c)) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 篡改签名的末位字符，保证与原签名不同。
     */
    private String tamperLastChar(String signature) {
        char last = signature.charAt(signature.length() - 1);
        char replacement = (last == 'A') ? 'B' : 'A';
        return signature.substring(0, signature.length() - 1) + replacement;
    }
}
