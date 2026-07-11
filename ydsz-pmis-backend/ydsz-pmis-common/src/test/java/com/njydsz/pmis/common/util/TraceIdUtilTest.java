package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TraceIdUtil} 链路追踪 ID 工具测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TraceIdUtil 链路追踪工具测试")
class TraceIdUtilTest {

    @Test
    @DisplayName("generate() 返回非空 16 位 hex 字符串")
    void shouldGenerateNonEmptyTraceId() {
        String traceId = TraceIdUtil.generate();
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
        // 雪花算法生成的 traceId 为 16 位 hex
        assertEquals(16, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{16}"));
    }

    @Test
    @DisplayName("连续调用 generate() 返回不同值")
    void shouldGenerateDifferentTraceIds() {
        String id1 = TraceIdUtil.generate();
        String id2 = TraceIdUtil.generate();
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("set() + get() 正确读取 MDC 中的 traceId")
    void shouldSetAndGetTraceId() {
        try {
            TraceIdUtil.set("test-trace-id");
            assertEquals("test-trace-id", TraceIdUtil.get());
        } finally {
            TraceIdUtil.clear();
        }
    }

    @Test
    @DisplayName("get() 未设置时返回空字符串")
    void shouldReturnEmptyWhenNotSet() {
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        assertEquals("", TraceIdUtil.get());
    }

    @Test
    @DisplayName("getOrCreate() 未设置时自动生成并写入 MDC")
    void shouldCreateWhenNotSet() {
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        try {
            String traceId = TraceIdUtil.getOrCreate();
            assertNotNull(traceId);
            assertFalse(traceId.isEmpty());
            assertEquals(traceId, MDC.get(TraceIdUtil.TRACE_ID_KEY));
        } finally {
            TraceIdUtil.clear();
        }
    }

    @Test
    @DisplayName("getOrCreate() 已设置时返回已有值")
    void shouldReturnExistingWhenSet() {
        try {
            TraceIdUtil.set("existing-id");
            assertEquals("existing-id", TraceIdUtil.getOrCreate());
        } finally {
            TraceIdUtil.clear();
        }
    }

    @Test
    @DisplayName("set(null) 不写入 MDC")
    void shouldNotSetNull() {
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        TraceIdUtil.set(null);
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("set(\"\") 不写入 MDC")
    void shouldNotSetEmpty() {
        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        TraceIdUtil.set("");
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("clear() 清除 MDC 中的 traceId")
    void shouldClearTraceId() {
        TraceIdUtil.set("to-be-cleared");
        TraceIdUtil.clear();
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }
}
