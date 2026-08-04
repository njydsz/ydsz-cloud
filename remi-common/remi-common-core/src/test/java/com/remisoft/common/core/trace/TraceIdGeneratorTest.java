package com.remisoft.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * {@link TraceIdGenerator} 单元测试。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("TraceIdGenerator 测试")
class TraceIdGeneratorTest {

    @Test
    @DisplayName("generateTraceId() 生成 32 位十六进制字符串")
    void format_32hex() {
        String id = TraceIdGenerator.generateTraceId();
        assertEquals(32, id.length());
        assertTrue(id.matches("^[0-9a-f]{32}$"), "must be 32 lowercase hex: " + id);
    }

    @Test
    @DisplayName("generateTraceId() 连续生成唯一")
    void unique() {
        String a = TraceIdGenerator.generateTraceId();
        String b = TraceIdGenerator.generateTraceId();
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("generateSpanId() 生成 16 位十六进制字符串")
    void generateSpanId_format16hex() {
        String id = TraceIdGenerator.generateSpanId();
        assertEquals(16, id.length());
        assertTrue(id.matches("^[0-9a-f]{16}$"), "must be 16 lowercase hex: " + id);
    }

    @Test
    @DisplayName("traceparentHeader() 无参版本格式正确")
    void traceparentHeader_format() {
        String header = TraceIdGenerator.traceparentHeader();
        String[] parts = header.split("-");
        assertEquals(4, parts.length);
        assertEquals("00", parts[0]);
        assertEquals(32, parts[1].length());
        assertEquals(16, parts[2].length());
        assertEquals("01", parts[3]);
    }

    @Test
    @DisplayName("traceparentHeader(traceId,spanId) 参数传递正确")
    void traceparentHeader_withParams() {
        String traceId = "abcdef1234567890abcdef1234567890";
        String spanId = "1234567890abcdef";
        String header = TraceIdGenerator.traceparentHeader(traceId, spanId);
        assertEquals("00-" + traceId + "-" + spanId + "-01", header);
    }

    @Test
    @DisplayName("工具类不可实例化")
    void utilityClass() throws Exception {
        java.lang.reflect.Constructor<TraceIdGenerator> ctor =
                TraceIdGenerator.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        try {
            ctor.newInstance();
            fail("should have thrown");
        } catch (InvocationTargetException e) {
            assertInstanceOf(UnsupportedOperationException.class, e.getCause());
        }
    }

    @RepeatedTest(10)
    @DisplayName("SpanId 不重复（重复 10 次）")
    void spanId_noCollision() {
        assertNotEquals(TraceIdGenerator.generateSpanId(), TraceIdGenerator.generateSpanId());
    }
}
