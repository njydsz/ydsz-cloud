package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CursorHelper 游标编解码测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CursorHelper 游标编解码测试")
class CursorHelperTest {

    @Test
    @DisplayName("encode 应返回 Base64 URL 安全编码字符串")
    void encode_shouldReturnBase64UrlString() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 2, 14, 30, 0);
        String cursor = CursorHelper.encode(time, 123L);

        assertThat(cursor).isNotBlank();
        // 应能被 Base64 URL decoder 解码
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        String raw = new String(decoded);
        assertThat(raw).contains("2026-07-02T14:30:00");
        assertThat(raw).contains("|123");
    }

    @Test
    @DisplayName("encode + decode 应能往返还原原始值")
    void encodeDecode_roundTrip() {
        LocalDateTime originalTime = LocalDateTime.of(2026, 6, 15, 9, 45, 30, 123456789);
        Long originalId = 999L;

        String cursor = CursorHelper.encode(originalTime, originalId);
        Object[] decoded = CursorHelper.decode(cursor);

        assertThat(decoded).isNotNull();
        assertThat(decoded[0]).isEqualTo(originalTime);
        assertThat(decoded[1]).isEqualTo(originalId);
    }

    @Test
    @DisplayName("decode null 或空字符串应返回 null")
    void decode_nullOrBlank_shouldReturnNull() {
        assertThat(CursorHelper.decode(null)).isNull();
        assertThat(CursorHelper.decode("")).isNull();
        assertThat(CursorHelper.decode("   ")).isNull();
    }

    @Test
    @DisplayName("decode 非法 Base64 应抛 IllegalArgumentException")
    void decode_invalidBase64_shouldThrow() {
        assertThatThrownBy(() -> CursorHelper.decode("!!!invalid!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor 解码失败");
    }

    @Test
    @DisplayName("decode 缺少分隔符应抛 IllegalArgumentException")
    void decode_missingSeparator_shouldThrow() {
        // 编码一个没有 | 的字符串
        String badCursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("nodelimiter".getBytes());
        assertThatThrownBy(() -> CursorHelper.decode(badCursor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor 格式非法");
    }

    @Test
    @DisplayName("decode ID 部分非数字应抛 IllegalArgumentException")
    void decode_nonNumericId_shouldThrow() {
        String badCursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-07-02T14:30:00|notanumber".getBytes());
        assertThatThrownBy(() -> CursorHelper.decode(badCursor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor 解码失败");
    }

    @Test
    @DisplayName("相同入参 encode 结果应一致（幂等）")
    void encode_sameInput_sameOutput() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        String c1 = CursorHelper.encode(time, 1L);
        String c2 = CursorHelper.encode(time, 1L);
        assertThat(c1).isEqualTo(c2);
    }

    @Test
    @DisplayName("不同 ID encode 结果应不同")
    void encode_differentId_differentOutput() {
        LocalDateTime time = LocalDateTime.now();
        String c1 = CursorHelper.encode(time, 1L);
        String c2 = CursorHelper.encode(time, 2L);
        assertThat(c1).isNotEqualTo(c2);
    }
}
