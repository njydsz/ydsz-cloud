package com.njydsz.common.util.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link EncodingUtils} 单元测试 — 覆盖 Base64 / Base32 / Base16 Hex / URL 编解码关键路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("EncodingUtils 编码工具测试")
class EncodingUtilsTest {

    @Nested
    @DisplayName("Base64")
    /**
     * 测试分组：Base64
     */
    class Base64 {

        @Test
        @DisplayName("标准 Base64 编解码往返一致")
        void standardRoundtrip() {
            byte[] data = "Hello, Base64!".getBytes(StandardCharsets.UTF_8);
            String encoded = EncodingUtils.encodeBase64(data);
            assertThat(EncodingUtils.decodeBase64(encoded)).isEqualTo(data);
        }

        @Test
        @DisplayName("URL 安全 Base64 编解码 — 不含 +/= 字符")
        void urlSafeHasNoPlusSlashEqual() {
            byte[] data = new byte[]{(byte) 0xFB, (byte) 0xFF, (byte) 0xBF};
            String encoded = EncodingUtils.encodeBase64Url(data);
            assertThat(encoded).doesNotContain("+", "/", "=");
            assertThat(EncodingUtils.decodeBase64Url(encoded)).isEqualTo(data);
        }

        @Test
        @DisplayName("空字节数组 Base64 编码为空字符串")
        void emptyArrayEncodedAsEmptyString() {
            assertThat(EncodingUtils.encodeBase64(new byte[0])).isEmpty();
            assertThat(EncodingUtils.decodeBase64("")).isEmpty();
        }

        @Test
        @DisplayName("null 输入抛 NullPointerException")
        void nullInputThrows() {
            assertThatThrownBy(() -> EncodingUtils.encodeBase64(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> EncodingUtils.decodeBase64(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("非法 Base64 抛 IllegalArgumentException")
        void illegalBase64Throws() {
            assertThatThrownBy(() -> EncodingUtils.decodeBase64("这不是 Base64!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Base32")
    /**
     * 测试分组：Base32
     */
    class Base32 {

        @Test
        @DisplayName("RFC 4648 标准 Base32 向量")
        void rfc4648Vectors() {
            assertThat(EncodingUtils.encodeBase32("".getBytes())).isEqualTo("");
            assertThat(EncodingUtils.encodeBase32("f".getBytes())).isEqualTo("MY======");
            assertThat(EncodingUtils.encodeBase32("fo".getBytes())).isEqualTo("MZXQ====");
            assertThat(EncodingUtils.encodeBase32("foo".getBytes())).isEqualTo("MZXW6===");
            assertThat(EncodingUtils.encodeBase32("foob".getBytes())).isEqualTo("MZXW6YQ=");
            assertThat(EncodingUtils.encodeBase32("fooba".getBytes())).isEqualTo("MZXW6YTB");
            assertThat(EncodingUtils.encodeBase32("foobar".getBytes())).isEqualTo("MZXW6YTBOI======");
        }

        @Test
        @DisplayName("Base32 编解码往返一致")
        void roundtrip() {
            for (String s : new String[]{"", "a", "ab", "abc", "abcd", "abcde", "abcdef"}) {
                byte[] data = s.getBytes(StandardCharsets.UTF_8);
                String encoded = EncodingUtils.encodeBase32(data);
                assertThat(EncodingUtils.decodeBase32(encoded)).isEqualTo(data);
            }
        }

        @Test
        @DisplayName("Base32 大小写不敏感")
        void caseInsensitive() {
            byte[] data = "ydsz-base32".getBytes(StandardCharsets.UTF_8);
            String upper = EncodingUtils.encodeBase32(data);
            String lower = upper.toLowerCase();
            assertThat(EncodingUtils.decodeBase32(lower)).isEqualTo(data);
            assertThat(EncodingUtils.decodeBase32(upper)).isEqualTo(data);
        }

        @Test
        @DisplayName("Base32 容忍缺失填充")
        void tolerateMissingPadding() {
            byte[] data = "foobar".getBytes(StandardCharsets.UTF_8);
            String withPad = EncodingUtils.encodeBase32(data);
            String noPad = withPad.replace("=", "");
            assertThat(EncodingUtils.decodeBase32(noPad)).isEqualTo(data);
        }

        @Test
        @DisplayName("非法字符抛 IllegalArgumentException")
        void illegalCharThrows() {
            assertThatThrownBy(() -> EncodingUtils.decodeBase32("INVALID!@#"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal Base32 character");
        }
    }

    @Nested
    @DisplayName("Base16 / Hex")
    /**
     * 测试分组：Base16 / Hex
     */
    class Hex {

        @Test
        @DisplayName("大写 Hex 编码 RFC 4648 向量")
        void upperHexRfc4648() {
            assertThat(EncodingUtils.encodeHex(new byte[]{(byte) 0x66, (byte) 0x6F, (byte) 0x6F}))
                    .isEqualTo("666F6F");
        }

        @Test
        @DisplayName("小写 Hex 编码")
        void lowerHex() {
            byte[] data = new byte[]{(byte) 0xCA, (byte) 0xFE};
            assertThat(EncodingUtils.encodeHex(data, false)).isEqualTo("cafe");
            assertThat(EncodingUtils.encodeHex(data, true)).isEqualTo("CAFE");
        }

        @Test
        @DisplayName("Hex 编解码往返一致")
        void roundtrip() {
            byte[] data = "ydsz-hex".getBytes(StandardCharsets.UTF_8);
            String encoded = EncodingUtils.encodeHex(data);
            assertThat(EncodingUtils.decodeHex(encoded)).isEqualTo(data);
            assertThat(EncodingUtils.decodeHex(encoded.toLowerCase())).isEqualTo(data);
        }

        @Test
        @DisplayName("奇数长度 Hex 字符串抛 IllegalArgumentException")
        void oddLengthThrows() {
            assertThatThrownBy(() -> EncodingUtils.decodeHex("ABC"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("even length");
        }

        @Test
        @DisplayName("非法 Hex 字符抛 IllegalArgumentException")
        void illegalHexCharThrows() {
            assertThatThrownBy(() -> EncodingUtils.decodeHex("XY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal hex character");
        }
    }

    @Nested
    @DisplayName("URL 编码")
    /**
     * 测试分组：URL 编码
     */
    class UrlEncoding {

        @Test
        @DisplayName("中文 + 空格 + 特殊字符 URL 编解码往返")
        void chineseAndSpecialCharsRoundtrip() {
            String input = "a b&c=中文 中文?#/!";
            String encoded = EncodingUtils.encodeUrl(input);
            assertThat(encoded).doesNotContain(" ", "中");
            assertThat(EncodingUtils.decodeUrl(encoded)).isEqualTo(input);
        }

        @Test
        @DisplayName("空字符串 URL 编解码为空")
        void emptyStringRoundtrip() {
            assertThat(EncodingUtils.encodeUrl("")).isEmpty();
            assertThat(EncodingUtils.decodeUrl("")).isEmpty();
        }

        @Test
        @DisplayName("指定字符集编解码一致")
        void explicitCharsetRoundtrip() {
            String input = "测试";
            String encoded = EncodingUtils.encodeUrl(input, StandardCharsets.UTF_8);
            assertThat(EncodingUtils.decodeUrl(encoded, StandardCharsets.UTF_8)).isEqualTo(input);
        }

        @Test
        @DisplayName("null 输入抛 NullPointerException")
        void nullInputThrows() {
            assertThatThrownBy(() -> EncodingUtils.encodeUrl(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> EncodingUtils.decodeUrl(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
