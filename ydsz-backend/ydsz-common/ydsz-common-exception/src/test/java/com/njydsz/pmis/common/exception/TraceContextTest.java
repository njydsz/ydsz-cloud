package com.njydsz.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.observability.TraceContext;

import org.slf4j.MDC;

/**
 * {@link TraceContext} 单元测试
 *
 * <p>覆盖 traceId 生成、提取、设置、清理、sanitize 等行为。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@DisplayName("TraceContext 分布式追踪上下文测试")
class TraceContextTest {

    @Test
    @DisplayName("generate() 返回 32 位无连字符 UUID")
    void testGenerate() {
        String traceId = TraceContext.generate();
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertFalse(traceId.contains("-"));
    }

    @Test
    @DisplayName("generate() 每次返回不同值")
    void testGenerateUnique() {
        String id1 = TraceContext.generate();
        String id2 = TraceContext.generate();
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("setTraceId + getTraceId 正确存取")
    void testSetAndGetTraceId() {
        try {
            TraceContext.setTraceId("test-trace-123");
            assertEquals("test-trace-123", TraceContext.getTraceId());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    @DisplayName("setTraceId(null) 不设置")
    void testSetNullTraceId() {
        TraceContext.setTraceId(null);
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("setTraceId(\"\") 不设置")
    void testSetEmptyTraceId() {
        TraceContext.setTraceId("");
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("clear() 清除 traceId 和 spanId")
    void testClear() {
        TraceContext.setContext("trace-1", "span-1");
        TraceContext.clear();
        assertNull(TraceContext.getTraceId());
        assertNull(TraceContext.getSpanId());
    }

    @Test
    @DisplayName("extractOrGenerate() 优先从 MDC 读取")
    void testExtractOrGenerateFromMdc() {
        try {
            MDC.put(TraceContext.TRACE_ID_KEY, "mdc-trace-id");
            String result = TraceContext.extractOrGenerate("header-trace-id");
            assertEquals("mdc-trace-id", result);
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("extractOrGenerate() MDC 为空时从 header 读取")
    void testExtractOrGenerateFromHeader() {
        try {
            MDC.clear();
            String result = TraceContext.extractOrGenerate("header-trace-id");
            assertEquals("header-trace-id", result);
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("extractOrGenerate() MDC 和 header 都为空时生成新 UUID")
    void testExtractOrGenerateNew() {
        try {
            MDC.clear();
            String result = TraceContext.extractOrGenerate(null);
            assertNotNull(result);
            assertEquals(32, result.length());
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("sanitize() 过滤特殊字符（防日志注入）")
    void testSanitize() {
        try {
            TraceContext.setTraceId("trace<script>alert(1)</script>");
            String traceId = TraceContext.getTraceId();
            assertNotNull(traceId);
            assertFalse(traceId.contains("<"));
            assertFalse(traceId.contains(">"));
            assertFalse(traceId.contains("("));
            assertFalse(traceId.contains(")"));
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    @DisplayName("withContext() 自动恢复之前的 traceId")
    void testWithContext() {
        try {
            TraceContext.setTraceId("original-trace");
            String result = TraceContext.withContext("temp-trace", () -> {
                assertEquals("temp-trace", TraceContext.getTraceId());
                return "done";
            });
            assertEquals("done", result);
            assertEquals("original-trace", TraceContext.getTraceId());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    @DisplayName("withContext() 在无 prior traceId 时清理")
    void testWithContextNoPrior() {
        try {
            TraceContext.clear();
            TraceContext.withContext("temp-trace", () -> "done");
            assertNull(TraceContext.getTraceId());
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    @DisplayName("setContext() 同时设置 traceId 和 spanId")
    void testSetContext() {
        try {
            TraceContext.setContext("trace-abc", "span-xyz");
            assertEquals("trace-abc", TraceContext.getTraceId());
            assertEquals("span-xyz", TraceContext.getSpanId());
        } finally {
            TraceContext.clear();
        }
    }
}
