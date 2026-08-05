package com.remisoft.common.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IOUtils} 关键路径单元测试 — 流复制正确性、缓冲区安全。
 *
 * @author remi-team
 * @since 1.4.0
 */
@DisplayName("IOUtils 关键路径测试")
class IOUtilsSecurityTest {

    @Test
    @DisplayName("流复制字节一致")
    void copyShouldPreserveContent() throws Exception {
        String content = "hello-io-utils-security-test-12345";
        byte[] input = content.getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copy(bis, bos);
        assertThat(copied).isEqualTo(input.length);
        assertThat(bos.toByteArray()).isEqualTo(input);
    }

    @Test
    @DisplayName("空流复制返回 0")
    void copyEmptyStreamShouldReturnZero() throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copy(bis, bos);
        assertThat(copied).isZero();
        assertThat(bos.toByteArray()).isEmpty();
    }

    @Test
    @DisplayName("大流复制（64KB）字节一致")
    void copyLargeStreamShouldPreserveContent() throws Exception {
        byte[] input = new byte[64 * 1024];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }

        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copy(bis, bos);
        assertThat(copied).isEqualTo(input.length);
        assertThat(bos.toByteArray()).isEqualTo(input);
    }

    @Test
    @DisplayName("copyFast NIO 复制字节一致")
    void copyFastShouldPreserveContent() throws Exception {
        String content = "nio-fast-copy-test-data";
        byte[] input = content.getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copyFast(bis, bos);
        assertThat(copied).isEqualTo(input.length);
        assertThat(bos.toByteArray()).isEqualTo(input);
    }

    @Test
    @DisplayName("空流 copyFast 返回 0")
    void copyFastEmptyStreamShouldReturnZero() throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copyFast(bis, bos);
        assertThat(copied).isZero();
        assertThat(bos.toByteArray()).isEmpty();
    }

    @Test
    @DisplayName("指定缓冲区大小复制字节一致")
    void copyWithCustomBufferShouldPreserveContent() throws Exception {
        String content = "custom-buffer-1024-test";
        byte[] input = content.getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        long copied = IOUtils.copy(bis, bos, 1024);
        assertThat(copied).isEqualTo(input.length);
        assertThat(bos.toByteArray()).isEqualTo(input);
    }

    @Test
    @DisplayName("null 输入流抛 NullPointerException")
    void nullInputStreamShouldThrow() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> IOUtils.copy(null, bos))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null 输出流抛 NullPointerException")
    void nullOutputStreamShouldThrow() {
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> IOUtils.copy(bis, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("非正缓冲区大小抛 IllegalArgumentException")
    void nonPositiveBufferSizeShouldThrow() {
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> IOUtils.copy(bis, bos, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}