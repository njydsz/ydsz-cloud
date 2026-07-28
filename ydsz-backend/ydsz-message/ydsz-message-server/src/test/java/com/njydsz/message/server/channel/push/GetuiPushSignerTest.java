package com.njydsz.message.server.channel.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * GetuiPushSigner 个推推送签名工具单元测试
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("GetuiPushSigner 个推推送签名工具测试")
class GetuiPushSignerTest {

    /** SHA-256("") 空字符串的标准哈希值 */
    private static final String SHA256_OF_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Nested
    @DisplayName("sign() 签名计算")
    class SignTest {

        @Test
        @DisplayName("已知 appKey/timestamp/masterSecret 生成确定性签名")
        void shouldGenerateDeterministicSignature() {
            String appKey = "test-app-key";
            String timestamp = "1609459200000";
            String masterSecret = "test-master-secret";

            String sig1 = GetuiPushSigner.sign(appKey, timestamp, masterSecret);
            String sig2 = GetuiPushSigner.sign(appKey, timestamp, masterSecret);

            assertThat(sig1).isEqualTo(sig2);
            assertThat(sig1).isNotBlank();
        }

        @Test
        @DisplayName("签名结果与独立计算的 SHA-256 一致")
        void shouldBeConsistentWithIndependentComputation() throws Exception {
            String appKey = "myappkey";
            String timestamp = "1234567890";
            String masterSecret = "mysecret";

            // 独立计算期望值，使用 Character.forDigit 而非被测类的 bytesToHex
            String raw = appKey + timestamp + masterSecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            String expected = bytesToHexIndependent(digest);

            String actual = GetuiPushSigner.sign(appKey, timestamp, masterSecret);
            assertThat(actual).isEqualTo(expected);
        }

        @Test
        @DisplayName("空字符串参数生成已知签名（SHA-256 空串标准值）")
        void shouldGenerateKnownSignatureForEmptyStrings() {
            // raw = "" + "" + "" = ""，SHA-256("") 为已知标准值
            String signature = GetuiPushSigner.sign("", "", "");
            assertThat(signature).isEqualTo(SHA256_OF_EMPTY);
        }

        @Test
        @DisplayName("不同输入生成不同签名")
        void shouldGenerateDifferentSignatureForDifferentInputs() {
            String sig1 = GetuiPushSigner.sign("key1", "ts", "secret");
            String sig2 = GetuiPushSigner.sign("key2", "ts", "secret");

            assertThat(sig1).isNotEqualTo(sig2);
        }

        /**
         * 独立的 hex 转换（使用 Character.forDigit，区别于被测类的 String.format）
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

    @Nested
    @DisplayName("bytesToHex() 字节数组转十六进制")
    class BytesToHexTest {

        @Test
        @DisplayName("已知字节数组正确转换")
        void shouldConvertKnownBytesToHex() {
            byte[] bytes = {0x00, 0x0f, 0x10, (byte) 0xff};
            assertThat(GetuiPushSigner.bytesToHex(bytes)).isEqualTo("000f10ff");
        }

        @Test
        @DisplayName("空字节数组返回空字符串")
        void shouldReturnEmptyStringForEmptyArray() {
            assertThat(GetuiPushSigner.bytesToHex(new byte[]{})).isEmpty();
        }

        @Test
        @DisplayName("负字节值正确转换")
        void shouldConvertNegativeBytesCorrectly() {
            byte[] bytes = {(byte) 0x80, (byte) 0xab, (byte) 0xff};
            assertThat(GetuiPushSigner.bytesToHex(bytes)).isEqualTo("80abff");
        }

        @Test
        @DisplayName("单字节正确转换")
        void shouldConvertSingleByte() {
            assertThat(GetuiPushSigner.bytesToHex(new byte[]{0x0a})).isEqualTo("0a");
        }
    }
}
