package com.njydsz.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
/**
 * {@link DigestUtils} 单元测试 — 覆盖 SHA-256 / MD5 / HMAC 等关键摘要算法的稳定性与一致性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DigestUtils 摘要工具测试")
class DigestUtilsTest {

    @Test
    @DisplayName("SHA-256 相同输入产生相同摘要")
    void sha256Stable() {
        String h1 = DigestUtils.sha256Hex("ydsz-common");
        String h2 = DigestUtils.sha256Hex("ydsz-common");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // 32 byte = 64 hex char
    }

    @Test
    @DisplayName("SHA-256 不同输入产生不同摘要")
    void sha256DifferentForDifferentInput() {
        assertThat(DigestUtils.sha256Hex("a"))
                .isNotEqualTo(DigestUtils.sha256Hex("b"));
    }

    @Test
    @DisplayName("SHA-256 已知向量校验（空字符串）")
    void sha256EmptyString() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(DigestUtils.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("MD5 已知向量校验")
    @SuppressWarnings("deprecation") // 验证 MD5 已知向量，确认兼容旧数据场景
    void md5KnownVector() {
        // MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertThat(DigestUtils.md5Hex("abc"))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");
    }

    @Test
    @DisplayName("HMAC-SHA256 已知向量校验（RFC 4231 Test Case 1）")
    void hmacSha256KnownVector() {
        // RFC 4231 Test Case 1: key=0x0b*20, data="Hi There"
        byte[] key = new byte[20];
        for (int i = 0; i < 20; i++) {
            key[i] = 0x0b;
        }
        byte[] data = "Hi There".getBytes(StandardCharsets.UTF_8);
        String hmac = DigestUtils.hmacSha256Hex(data, key);
        // RFC 4231 expected: b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7
        assertThat(hmac).isEqualTo("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
    }

    @Test
    @DisplayName("字节数组与字符串重载结果一致")
    void byteAndStringOverloadConsistent() {
        String input = "ydsz-digest";
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        assertThat(DigestUtils.sha256Hex(input)).isEqualTo(DigestUtils.sha256Hex(bytes));
    }
}
