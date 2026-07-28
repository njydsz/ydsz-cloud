package com.njydsz.workflow.server.thirdparty;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FeishuSignatureUtil 飞书回调签名验证工具单元测试
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("FeishuSignatureUtil 飞书签名验证工具测试")
class FeishuSignatureUtilTest {

    /** SHA-256("abc") 的 NIST 标准测试向量 */
    private static final String SHA256_OF_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Nested
    @DisplayName("verifySignature() 签名验证")
    class VerifySignatureTest {

        @Test
        @DisplayName("正确签名验证通过（使用 NIST 标准测试向量）")
        void shouldReturnTrueForCorrectSignature() {
            // data = str(null) + str(null) + str(null) + "abc" = "abc"
            // SHA-256("abc") 为已知 NIST 标准值
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, SHA256_OF_ABC, "abc");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("大写签名验证通过（大小写不敏感）")
        void shouldReturnTrueForUppercaseSignature() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, SHA256_OF_ABC.toUpperCase(), "abc");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("错误签名验证失败")
        void shouldReturnFalseForWrongSignature() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, "wrong_signature_value", "abc");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 签名验证失败")
        void shouldReturnFalseForNullSignature() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, null, "abc");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空签名验证失败")
        void shouldReturnFalseForEmptySignature() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, "", "abc");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null appSecret 验证失败")
        void shouldReturnFalseForNullAppSecret() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    "123", "nonce", "encrypt", SHA256_OF_ABC, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空 appSecret 验证失败")
        void shouldReturnFalseForEmptyAppSecret() {
            boolean result = FeishuSignatureUtil.verifySignature(
                    "123", "nonce", "encrypt", SHA256_OF_ABC, "");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("完整参数正确签名验证通过")
        void shouldReturnTrueForFullParameters() throws Exception {
            String timestamp = "1609459200";
            String nonce = "test-nonce";
            String encrypt = "encrypted-payload";
            String appSecret = "app-secret";

            // 独立计算正确签名
            String signature = computeFeishuSignature(timestamp, nonce, encrypt, appSecret);

            boolean result = FeishuSignatureUtil.verifySignature(
                    timestamp, nonce, encrypt, signature, appSecret);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("篡改任意参数后签名验证失败")
        void shouldReturnFalseForTamperedParameters() throws Exception {
            String timestamp = "1609459200";
            String nonce = "test-nonce";
            String encrypt = "encrypted-payload";
            String appSecret = "app-secret";

            String signature = computeFeishuSignature(timestamp, nonce, encrypt, appSecret);

            // 篡改 timestamp
            assertThat(FeishuSignatureUtil.verifySignature(
                    "9999", nonce, encrypt, signature, appSecret)).isFalse();
            // 篡改 appSecret
            assertThat(FeishuSignatureUtil.verifySignature(
                    timestamp, nonce, encrypt, signature, "wrong")).isFalse();
        }

        @Test
        @DisplayName("null 时间戳/随机串/加密载荷被安全处理为空串")
        void shouldHandleNullTimestampNonceEncryptAsEmptyString() {
            // data = "" + "" + "" + "abc" = "abc"
            boolean result = FeishuSignatureUtil.verifySignature(
                    null, null, null, SHA256_OF_ABC, "abc");
            assertThat(result).isTrue();
        }

        /**
         * 独立计算飞书签名（不依赖被测类的内部方法）
         */
        private String computeFeishuSignature(String timestamp, String nonce,
                                               String encrypt, String appSecret) throws Exception {
            String data = nullToEmpty(timestamp) + nullToEmpty(nonce)
                    + nullToEmpty(encrypt) + appSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHexIndependent(digest);
        }

        private String nullToEmpty(String s) {
            return s == null ? "" : s;
        }

        /**
         * 独立的 hex 转换（使用 Character.forDigit）
         */
        private String bytesToHexIndependent(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
    }
}
