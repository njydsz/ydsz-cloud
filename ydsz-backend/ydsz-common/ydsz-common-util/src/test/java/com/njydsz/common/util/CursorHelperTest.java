package com.njydsz.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CursorHelper 单元测试
 *
 * <p>覆盖核心方法：create/parse/encode/decode/isValid/getSortValue/getId。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("CursorHelper 游标分页工具测试")
class CursorHelperTest {

    // ==================== String-based cursor ====================

    @Nested
    @DisplayName("String 游标")
    class StringCursorTest {

        @Test
        @DisplayName("create - 正常创建")
        void create_normal() {
            String cursor = CursorHelper.create("sort_value_001", "record_id_123");
            assertThat(cursor).isNotNull();
            assertThat(cursor).isNotEmpty();
        }

        @Test
        @DisplayName("create + parse - 往返测试")
        void create_parse_roundtrip() {
            String cursor = CursorHelper.create("2024-01-15", "abc123");
            Map<String, String> parsed = CursorHelper.parse(cursor);
            assertThat(parsed).isNotNull();
            assertThat(parsed.get("sv")).isEqualTo("2024-01-15");
            assertThat(parsed.get("id")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("getSortValue - 提取排序值")
        void getSortValue() {
            String cursor = CursorHelper.create("sort_value", "id_001");
            assertThat(CursorHelper.getSortValue(cursor)).isEqualTo("sort_value");
        }

        @Test
        @DisplayName("getId - 提取记录 ID")
        void getId() {
            String cursor = CursorHelper.create("sort_value", "id_001");
            assertThat(CursorHelper.getId(cursor)).isEqualTo("id_001");
        }

        @Test
        @DisplayName("isValid - 有效游标")
        void isValid_valid() {
            String cursor = CursorHelper.create("sv", "id");
            assertThat(CursorHelper.isValid(cursor)).isTrue();
        }

        @Test
        @DisplayName("isValid - 无效游标")
        void isValid_invalid() {
            assertThat(CursorHelper.isValid(null)).isFalse();
            assertThat(CursorHelper.isValid("")).isFalse();
            assertThat(CursorHelper.isValid("invalid_base64!!!")).isFalse();
        }
    }

    // ==================== LocalDateTime-based cursor ====================

    @Nested
    @DisplayName("LocalDateTime 游标")
    class DateTimeCursorTest {

        @Test
        @DisplayName("encode + decode - 往返测试")
        void encode_decode_roundtrip() {
            LocalDateTime sortValue = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            String cursor = CursorHelper.encode(sortValue, "record_001");
            assertThat(cursor).isNotNull();

            Object[] decoded = CursorHelper.decode(cursor);
            assertThat(decoded).isNotNull();
            assertThat(decoded[0]).isEqualTo(sortValue);
            assertThat(decoded[1]).isEqualTo("record_001");
        }

        @Test
        @DisplayName("encode - null sortValue")
        void encode_nullSortValue() {
            String cursor = CursorHelper.encode(null, "record_001");
            assertThat(cursor).isNotNull();
            Object[] decoded = CursorHelper.decode(cursor);
            assertThat(decoded).isNotNull();
            assertThat(decoded[0]).isNull();
            assertThat(decoded[1]).isEqualTo("record_001");
        }

        @Test
        @DisplayName("decode - 无效游标返回 null")
        void decode_invalid() {
            assertThat(CursorHelper.decode(null)).isNull();
            assertThat(CursorHelper.decode("")).isNull();
            assertThat(CursorHelper.decode("invalid!!!")).isNull();
        }
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("parse - null 返回 null")
    void parse_null() {
        assertThat(CursorHelper.parse(null)).isNull();
    }

    @Test
    @DisplayName("parse - 空字符串返回 null")
    void parse_empty() {
        assertThat(CursorHelper.parse("")).isNull();
    }

    @Test
    @DisplayName("getSortValue - 无效游标返回 null")
    void getSortValue_invalid() {
        assertThat(CursorHelper.getSortValue(null)).isNull();
        assertThat(CursorHelper.getSortValue("invalid")).isNull();
    }

    @Test
    @DisplayName("getId - 无效游标返回 null")
    void getId_invalid() {
        assertThat(CursorHelper.getId(null)).isNull();
        assertThat(CursorHelper.getId("invalid")).isNull();
    }
}
