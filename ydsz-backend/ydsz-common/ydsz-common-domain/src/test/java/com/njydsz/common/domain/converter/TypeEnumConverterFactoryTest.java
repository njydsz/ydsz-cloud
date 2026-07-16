package com.njydsz.common.domain.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.core.enums.TypeEnum;

/**
 * TypeEnumConverterFactory 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("TypeEnumConverterFactory 枚举转换器测试")
class TypeEnumConverterFactoryTest {

    @Test
    @DisplayName("fromCode 应根据 code 返回正确的枚举实例")
    void shouldReturnCorrectEnumFromCode() {
        TestStatus status = TypeEnumConverterFactory.fromCode(TestStatus.class, 1);
        assertEquals(TestStatus.ACTIVE, status);
    }

    @Test
    @DisplayName("fromCode code 不存在时应抛出 IllegalArgumentException")
    void shouldThrowWhenCodeNotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                TypeEnumConverterFactory.fromCode(TestStatus.class, 999));
    }

    @Test
    @DisplayName("fromCodeOrNull code 不存在时应返回 null")
    void shouldReturnNullWhenCodeNotFound() {
        TestStatus status = TypeEnumConverterFactory.fromCodeOrNull(TestStatus.class, 999);
        assertNull(status);
    }

    @Test
    @DisplayName("fromCode code 为 null 时应返回 null")
    void shouldReturnNullWhenCodeIsNull() {
        TestStatus status = TypeEnumConverterFactory.fromCodeOrNull(TestStatus.class, null);
        assertNull(status);
    }

    @Test
    @DisplayName("toCode 应返回枚举的 code 值")
    void shouldReturnCodeFromEnum() {
        Integer code = TypeEnumConverterFactory.toCode(TestStatus.ACTIVE);
        assertEquals(1, code);
    }

    @Test
    @DisplayName("toDesc 应返回枚举的描述信息")
    void shouldReturnDescFromEnum() {
        String desc = TypeEnumConverterFactory.toDesc(TestStatus.DISABLED);
        assertEquals("禁用", desc);
    }

    @Test
    @DisplayName("options 应返回所有枚举选项")
    void shouldReturnAllOptions() {
        Map<Integer, String> options = TypeEnumConverterFactory.options(TestStatus.class);
        assertEquals(2, options.size());
        assertEquals("启用", options.get(1));
        assertEquals("禁用", options.get(0));
    }

    enum TestStatus implements TypeEnum<Integer> {
        DISABLED(0, "禁用"),
        ACTIVE(1, "启用");

        private final Integer code;
        private final String desc;

        TestStatus(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @Override
        public Integer getCode() { return code; }

        @Override
        public String getDesc() { return desc; }
    }
}
