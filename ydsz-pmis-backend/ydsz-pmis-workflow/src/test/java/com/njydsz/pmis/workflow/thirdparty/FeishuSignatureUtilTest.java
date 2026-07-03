package com.njydsz.pmis.workflow.thirdparty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FeishuSignatureUtil 单元测试
 *
 * <p>P0-2：覆盖飞书回调签名验证工具的核心场景。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正确签名验证通过</li>
 *   <li>错误签名验证失败</li>
 *   <li>签名或 appSecret 为空时返回 false</li>
 *   <li>签名不区分大小写（十六进制 toLowerCase 比较）</li>
 *   <li>非法参数不抛异常并返回 false</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class FeishuSignatureUtilTest {

    private static final String TIMESTAMP = "1690000000123";
    private static final String NONCE = "feishu-nonce-abc-123";
    private static final String ENCRYPT = "feishu-encrypt-payload-xyz";
    private static final String APP_SECRET = "feishu-app-secret-demo";

    @Test
    @DisplayName("正确签名验证通过")
    void verifySignatureShouldReturnTrueWhenSignatureMatches() {
        String signature = computeFeishuSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);

        boolean result = FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, signature, APP_SECRET);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("错误签名验证失败")
    void verifySignatureShouldReturnFalseWhenSignatureMismatch() {
        String signature = computeFeishuSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);
        String tampered = tamperLastHexChar(signature);

        boolean result = FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, tampered, APP_SECRET);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("签名或 appSecret 为空时返回 false")
    void verifySignatureShouldReturnFalseWhenNullOrEmptyInputs() {
        String valid = computeFeishuSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);

        assertThat(FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, null, APP_SECRET)).isFalse();
        assertThat(FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, "", APP_SECRET)).isFalse();
        assertThat(FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, valid, null)).isFalse();
        assertThat(FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, valid, "")).isFalse();
    }

    @Test
    @DisplayName("签名不区分大小写（十六进制 toLowerCase 比较）")
    void verifySignatureShouldBeCaseInsensitive() {
        String signature = computeFeishuSignature(TIMESTAMP, NONCE, ENCRYPT, APP_SECRET);
        String upper = signature.toUpperCase();
        // 校验确实存在字母位，保证测试有意义
        assertThat(upper).isNotEqualTo(signature);

        boolean result = FeishuSignatureUtil.verifySignature(
                TIMESTAMP, NONCE, ENCRYPT, upper, APP_SECRET);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("非法参数不抛异常并返回 false")
    void verifySignatureShouldNotThrowOnInvalidInput() {
        assertThatCode(() -> FeishuSignatureUtil.verifySignature(
                null, null, null, "deadbeef", APP_SECRET)).doesNotThrowAnyException();
        assertThat(FeishuSignatureUtil.verifySignature(
                null, null, null, "deadbeef", APP_SECRET)).isFalse();
    }

    // ============ 辅助方法：在测试中复现飞书签名算法 ============

    /**
     * 复现飞书签名算法：SHA-256(timestamp + nonce + encrypt + appSecret)，结果以十六进制小写编码。
     */
    private String computeFeishuSignature(String timestamp, String nonce, String encrypt, String appSecret) {
        try {
            String data = str(timestamp) + str(nonce) + str(encrypt) + appSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return toHexLower(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String str(String s) {
        return s == null ? "" : s;
    }

    private String toHexLower(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(hex[(b >> 4) & 0x0F]);
            sb.append(hex[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * 篡改十六进制签名的末位字符，保证与原签名不同。
     */
    private String tamperLastHexChar(String signature) {
        char last = signature.charAt(signature.length() - 1);
        char replacement = (last == 'a') ? 'b' : 'a';
        return signature.substring(0, signature.length() - 1) + replacement;
    }
}
