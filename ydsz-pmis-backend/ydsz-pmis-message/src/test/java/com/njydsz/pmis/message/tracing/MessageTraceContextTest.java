package com.njydsz.pmis.message.tracing;

import com.njydsz.pmis.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1-3: {@link MessageTraceContext} 单元测试。
 *
 * <p>验证 traceId 写入 MDC / 恢复 / 清除 / 自动生成逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("MessageTraceContext 追踪上下文测试")
class MessageTraceContextTest {

    @BeforeEach
    void setUp() {
        // 每个测试前彻底清空 MDC，避免上游测试残留的 Brave/snowflake traceId 干扰
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TraceIdUtil.clear();
    }

    @Test
    @DisplayName("enter 指定 traceId 后 MDC 中可读取")
    void enterShouldSetTraceIdToMdc() {
        try (MessageTraceContext ctx = MessageTraceContext.enter("trace-abc")) {
            assertEquals("trace-abc", MDC.get(TraceIdUtil.TRACE_ID_KEY));
            assertEquals("trace-abc", TraceIdUtil.get());
        }
    }

    @Test
    @DisplayName("close 后 MDC traceId 被清除（原无 traceId）")
    void closeShouldClearMdcWhenNoPreviousTraceId() {
        try (MessageTraceContext ctx = MessageTraceContext.enter("trace-1")) {
            assertEquals("trace-1", MDC.get(TraceIdUtil.TRACE_ID_KEY));
        }
        // 退出后 MDC 应清除（TraceIdUtil.get 可能返回 Brave fallback，故直接断言 MDC）
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("enter null traceId 时自动生成非空 traceId")
    void enterShouldAutoGenerateWhenTraceIdNull() {
        try (MessageTraceContext ctx = MessageTraceContext.enter(null)) {
            String traceId = TraceIdUtil.get();
            assertNotNull(traceId);
            assertFalse(traceId.isEmpty());
        }
    }

    @Test
    @DisplayName("enter 空白 traceId 时自动生成非空 traceId")
    void enterShouldAutoGenerateWhenTraceIdBlank() {
        try (MessageTraceContext ctx = MessageTraceContext.enter("   ")) {
            String traceId = TraceIdUtil.get();
            assertNotNull(traceId);
            assertFalse(traceId.isEmpty());
        }
    }

    @Test
    @DisplayName("嵌套 enter/close 恢复外层 traceId")
    void nestedEnterShouldRestoreOuterTraceId() {
        try (MessageTraceContext outer = MessageTraceContext.enter("outer-trace")) {
            assertEquals("outer-trace", MDC.get(TraceIdUtil.TRACE_ID_KEY));
            try (MessageTraceContext inner = MessageTraceContext.enter("inner-trace")) {
                assertEquals("inner-trace", MDC.get(TraceIdUtil.TRACE_ID_KEY));
            }
            // 内层退出后应恢复外层
            assertEquals("outer-trace", MDC.get(TraceIdUtil.TRACE_ID_KEY));
        }
        // 外层退出后 MDC 不再持有 outer-trace（Brave 可能注入独立 traceId,仅验证本上下文已清理）
        assertNotEquals("outer-trace", MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("已有 traceId 时 enter 新 traceId，close 后恢复原值")
    void enterShouldRestorePreviousTraceId() {
        TraceIdUtil.set("original");
        try (MessageTraceContext ctx = MessageTraceContext.enter("temp")) {
            assertEquals("temp", TraceIdUtil.get());
        }
        // 恢复原值
        assertEquals("original", TraceIdUtil.get());
    }

    @Test
    @DisplayName("多次 enter/close 不会泄漏 MDC")
    void repeatedEnterCloseShouldNotLeak() {
        for (int i = 0; i < 10; i++) {
            try (MessageTraceContext ctx = MessageTraceContext.enter("trace-" + i)) {
                assertEquals("trace-" + i, MDC.get(TraceIdUtil.TRACE_ID_KEY));
            }
        }
        assertNull(MDC.get(TraceIdUtil.TRACE_ID_KEY));
    }
}
