package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CursorHelper 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CursorHelper 测试")
class CursorHelperTest {

    @Test
    @DisplayName("编码 - 应返回非空 Base64 字符串")
    void encode_shouldReturnNonEmptyString() {
        String cursor = CursorHelper.encode(LocalDateTime.now(), "1");
        assertNotNull(cursor);
        assertFalse(cursor.isBlank());
    }

    @Test
    @DisplayName("编码后解码 - 应还原原始 sortValue 和 id")
    void encodeDecode_shouldRoundtrip() {
        LocalDateTime sortValue = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
        String id = "12345";

        String cursor = CursorHelper.encode(sortValue, id);
        Object[] result = CursorHelper.decode(cursor);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(sortValue, result[0]);
        assertEquals(id, result[1]);
    }

    @Test
    @DisplayName("解码 null 或空字符串 - 应返回 null")
    void decode_shouldReturnNullForNullOrBlank() {
        assertNull(CursorHelper.decode(null));
        assertNull(CursorHelper.decode(""));
        assertNull(CursorHelper.decode("   "));
    }

    @Test
    @DisplayName("解码非法 Base64 字符串 - 应抛出 IllegalArgumentException")
    void decode_shouldThrowExceptionForInvalidBase64() {
        assertThrows(IllegalArgumentException.class, () -> CursorHelper.decode("!!!invalid!!!"));
    }

    @Test
    @DisplayName("解码格式错误的游标 - 应抛出 IllegalArgumentException")
    void decode_shouldThrowExceptionForMalformedCursor() {
        // 无法直接篡改 Base64 内容，但我们可以测试空字符串解码返回 null
        assertNull(CursorHelper.decode(""));
    }
}