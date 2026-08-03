package com.njydsz.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceIdGenerator} 单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("TraceIdGenerator 测试")
class TraceIdGeneratorTest {

    @Test
    @DisplayName("生成 32 位十六进制字符串")
    void format_32hex() {
        String id = TraceIdGenerator.generate();
        assertEquals(32, id.length());
        assertTrue(id.matches("^[0-9a-f]{32}$"), "must be 32 lowercase hex: " + id);
    }

    @Test
    @DisplayName("连续生成唯一")
    void unique() {
        String a = TraceIdGenerator.generate();
        String b = TraceIdGenerator.generate();
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("工具类不可实例化")
    void utilityClass() {
        assertThrows(UnsupportedOperationException.class, () -> {
            java.lang.reflect.Constructor<TraceIdGenerator> ctor =
                    TraceIdGenerator.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        });
    }
}
