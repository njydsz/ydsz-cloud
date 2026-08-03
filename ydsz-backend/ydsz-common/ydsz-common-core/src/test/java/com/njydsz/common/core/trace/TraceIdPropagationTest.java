package com.njydsz.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * {@link TraceIdPropagation} 单元测试
 *
 * <p>覆盖 traceId 读取、请求头生成、缺失时自动生成、MDC 写入等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("TraceIdPropagation 传播工具测试")
class TraceIdPropagationTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC 有 traceId 时读取成功")
    void currentTraceId_fromMdc() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "trace-abc");
        assertEquals("trace-abc", TraceIdPropagation.currentTraceId());
    }

    @Test
    @DisplayName("MDC 无 traceId 时返回 null")
    void currentTraceId_missing() {
        assertNull(TraceIdPropagation.currentTraceId());
    }

    @Test
    @DisplayName("traceHeader 生成单元素请求头")
    void traceHeader() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "trace-abc");
        Map<String, String> headers = TraceIdPropagation.traceHeader();
        assertEquals(1, headers.size());
        assertEquals("trace-abc", headers.get(HeaderConstants.TRACE_ID_HEADER));
        assertThrows(UnsupportedOperationException.class,
                () -> headers.put("X", "y"), "header map must be immutable");
    }

    @Test
    @DisplayName("MDC 无 traceId 时 traceHeader 返回空 Map")
    void traceHeader_emptyWhenMissing() {
        Map<String, String> headers = TraceIdPropagation.traceHeader();
        assertTrue(headers.isEmpty());
    }

    @Test
    @DisplayName("traceHeaderOrCreate 缺失时自动生成")
    void traceHeaderOrCreate_generates() {
        Map<String, String> headers = TraceIdPropagation.traceHeaderOrCreate();
        assertEquals(1, headers.size());
        String traceId = headers.get(HeaderConstants.TRACE_ID_HEADER);
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
    }

    @Test
    @DisplayName("traceHeaderOrCreate 优先使用 MDC 已有值")
    void traceHeaderOrCreate_usesMdc() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "trace-existing");
        Map<String, String> headers = TraceIdPropagation.traceHeaderOrCreate();
        assertEquals("trace-existing", headers.get(HeaderConstants.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("currentTraceIdOrCreate 缺失时生成并写入 MDC")
    void currentTraceIdOrCreate_writesMdc() {
        String traceId = TraceIdPropagation.currentTraceIdOrCreate();
        assertNotNull(traceId);
        assertEquals(traceId, MDC.get(HeaderConstants.MDC_TRACE_ID_KEY));
    }

    @Test
    @DisplayName("currentTraceIdOrCreate 已有值时直接返回")
    void currentTraceIdOrCreate_usesMdc() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "trace-keep");
        assertEquals("trace-keep", TraceIdPropagation.currentTraceIdOrCreate());
    }
}
