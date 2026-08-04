package com.njydsz.common.notify.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import com.njydsz.common.notify.signature.WeComSignatureUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * WeComSignatureUtil 企业微信回调签名验证工具单元测试
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("WeComSignatureUtil 企业微信签名验证工具测试")
class WeComSignatureUtilTest {

    /** SHA-1("abc") 的 NIST 标准测试向量 */
    private static final String SHA1_OF_ABC = "a9993e364706816aba3e25717850c26c9cd0d89d";

    @Nested
    @DisplayName("verifySignature() 签名验证")
    /**
     * 测试分组：verifySignature() 签名验证
     */
    /**
     * 测试分组：「正确签名验证通过（使用 NIST 标准测试向量）」等
     */
    class VerifySignatureTest {

        @Test
        @DisplayName("正确签名验证通过（使用 NIST 标准测试向量）")
        void shouldReturnTrueForCorrectSignature() {
            // arr = ["abc", "", "", ""]，排序后拼接 = "abc"
            // SHA-1("abc") 为已知 NIST 标准值
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, SHA1_OF_ABC);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("大写签名验证通过（大小写不敏感）")
        void shouldReturnTrueForUppercaseSignature() {
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, SHA1_OF_ABC.toUpperCase());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("错误签名验证失败")
        void shouldReturnFalseForWrongSignature() {
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, "wrong_signature_value");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 签名验证失败")
        void shouldReturnFalseForNullSignature() {
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, null);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空签名验证失败")
        void shouldReturnFalseForEmptySignature() {
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, "");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null token 验证失败")
        void shouldReturnFalseForNullToken() {
            boolean result = WeComSignatureUtil.verifySignature(
                    null, "123", "nonce", "encrypt", SHA1_OF_ABC);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("完整参数正确签名验证通过")
        void shouldReturnTrueForFullParameters() throws Exception {
            String token = "my-token";
            String timestamp = "1609459200";
            String nonce = "test-nonce";
            String encrypt = "encrypted-payload";

            // 独立计算正确签名
            String signature = computeWeComSignature(token, timestamp, nonce, encrypt);

            boolean result = WeComSignatureUtil.verifySignature(
                    token, timestamp, nonce, encrypt, signature);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("篡改任意参数后签名验证失败")
        void shouldReturnFalseForTamperedParameters() throws Exception {
            String token = "my-token";
            String timestamp = "1609459200";
            String nonce = "test-nonce";
            String encrypt = "encrypted-payload";

            String signature = computeWeComSignature(token, timestamp, nonce, encrypt);

            // 篡改 token
            assertThat(WeComSignatureUtil.verifySignature(
                    "wrong", timestamp, nonce, encrypt, signature)).isFalse();
            // 篡改 timestamp
            assertThat(WeComSignatureUtil.verifySignature(
                    token, "9999", nonce, encrypt, signature)).isFalse();
        }

        @Test
        @DisplayName("null 时间戳/随机串/加密载荷被安全处理为空串")
        void shouldHandleNullTimestampNonceEncryptAsEmptyString() {
            // arr = ["abc", "", "", ""]，排序后拼接 = "abc"
            boolean result = WeComSignatureUtil.verifySignature(
                    "abc", null, null, null, SHA1_OF_ABC);
            assertThat(result).isTrue();
        }

        /**
         * 独立计算企微签名（不依赖被测类的内部方法）
         */
        private String computeWeComSignature(String token, String timestamp,
                                              String nonce, String encrypt) throws Exception {
            String[] arr = new String[]{
                    nullToEmpty(token), nullToEmpty(timestamp),
                    nullToEmpty(nonce), nullToEmpty(encrypt)
            };
            Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
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
