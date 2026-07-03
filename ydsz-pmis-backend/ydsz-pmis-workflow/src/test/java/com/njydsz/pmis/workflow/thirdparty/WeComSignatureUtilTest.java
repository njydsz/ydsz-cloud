package com.njydsz.pmis.workflow.thirdparty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WeComSignatureUtil 单元测试
 *
 * <p>P0-2：覆盖企业微信回调签名验证工具的核心场景。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正确签名验证通过</li>
 *   <li>错误签名验证失败</li>
 *   <li>signature 为空或 token 为 null 时返回 false</li>
 *   <li>签名不区分大小写（十六进制 toLowerCase 比较）</li>
 *   <li>非法参数不抛异常并返回 false</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class WeComSignatureUtilTest {

    private static final String TOKEN = "wecom-callback-token-demo";
    private static final String TIMESTAMP = "1690000000123";
    private static final String NONCE = "wecom-nonce-abc-123";
    private static final String ENCRYPT = "wecom-encrypt-payload-xyz";

    @Test
    @DisplayName("正确签名验证通过")
    void verifySignatureShouldReturnTrueWhenSignatureMatches() {
        String signature = computeWeComSignature(TOKEN, TIMESTAMP, NONCE, ENCRYPT);

        boolean result = WeComSignatureUtil.verifySignature(
                TOKEN, TIMESTAMP, NONCE, ENCRYPT, signature);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("错误签名验证失败")
    void verifySignatureShouldReturnFalseWhenSignatureMismatch() {
        String signature = computeWeComSignature(TOKEN, TIMESTAMP, NONCE, ENCRYPT);
        String tampered = tamperLastHexChar(signature);

        boolean result = WeComSignatureUtil.verifySignature(
                TOKEN, TIMESTAMP, NONCE, ENCRYPT, tampered);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("signature 为空或 token 为 null 时返回 false")
    void verifySignatureShouldReturnFalseWhenNullOrEmptyInputs() {
        String valid = computeWeComSignature(TOKEN, TIMESTAMP, NONCE, ENCRYPT);

        // signature 为 null/空 → false
        assertThat(WeComSignatureUtil.verifySignature(
                TOKEN, TIMESTAMP, NONCE, ENCRYPT, null)).isFalse();
        assertThat(WeComSignatureUtil.verifySignature(
                TOKEN, TIMESTAMP, NONCE, ENCRYPT, "")).isFalse();
        // token 为 null → false（注意：源码守卫仅校验 token==null，不校验空串）
        assertThat(WeComSignatureUtil.verifySignature(
                null, TIMESTAMP, NONCE, ENCRYPT, valid)).isFalse();
    }

    @Test
    @DisplayName("签名不区分大小写（十六进制 toLowerCase 比较）")
    void verifySignatureShouldBeCaseInsensitive() {
        String signature = computeWeComSignature(TOKEN, TIMESTAMP, NONCE, ENCRYPT);
        String upper = signature.toUpperCase();
        // 校验确实存在字母位，保证测试有意义
        assertThat(upper).isNotEqualTo(signature);

        boolean result = WeComSignatureUtil.verifySignature(
                TOKEN, TIMESTAMP, NONCE, ENCRYPT, upper);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("非法参数不抛异常并返回 false")
    void verifySignatureShouldNotThrowOnInvalidInput() {
        // timestamp/nonce/encrypt 为 null 时通过 str() 转 ""，不会抛异常
        assertThatCode(() -> WeComSignatureUtil.verifySignature(
                TOKEN, null, null, null, "deadbeef")).doesNotThrowAnyException();
        assertThat(WeComSignatureUtil.verifySignature(
                TOKEN, null, null, null, "deadbeef")).isFalse();
    }

    // ============ 辅助方法：在测试中复现企微签名算法 ============

    /**
     * 复现企微签名算法：SHA-1(sort(token, timestamp, nonce, encrypt))，结果以十六进制小写编码。
     */
    private String computeWeComSignature(String token, String timestamp, String nonce, String encrypt) {
        try {
            String[] arr = new String[]{token, str(timestamp), str(nonce), str(encrypt)};
            Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
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
