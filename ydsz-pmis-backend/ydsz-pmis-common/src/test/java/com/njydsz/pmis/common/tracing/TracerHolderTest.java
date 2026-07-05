package com.njydsz.pmis.common.tracing;

import brave.Tracer;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TracerHolder} 单元测试（P1-6）
 *
 * <p>验证 Brave Tracer 持有器在以下场景的行为：
 * <ul>
 *   <li>未初始化时 {@link TracerHolder#get()} 返回 null</li>
 *   <li>注入 Tracer 后能正确读取</li>
 *   <li>{@link TracerHolder#currentTraceId()} 优先返回当前 span traceId</li>
 *   <li>无当前 span 时返回 null</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@DisplayName("TracerHolder 单元测试")
class TracerHolderTest {

    @AfterEach
    void cleanUp() {
        // 每个测试后清理静态状态，避免污染其他测试
        ReflectionTestUtils.invokeMethod(TracerHolder.class, "resetForTest");
    }

    @Test
    @DisplayName("未初始化时 get 应返回 null")
    void get_shouldReturnNullWhenNotInitialized() {
        assertNull(TracerHolder.get());
    }

    @Test
    @DisplayName("未初始化时 isInitialized 应返回 false")
    void isInitialized_shouldReturnFalseWhenNotInitialized() {
        assertFalse(TracerHolder.isInitialized());
    }

    @Test
    @DisplayName("注入 Tracer 后 get 应返回该实例")
    void get_shouldReturnTracerAfterInjection() {
        Tracer tracer = mock(Tracer.class);
        TracerHolder holder = new TracerHolder();
        holder.setTracer(tracer);

        assertSame(tracer, TracerHolder.get());
        assertTrue(TracerHolder.isInitialized());
    }

    @Test
    @DisplayName("注入 null Tracer 后 isInitialized 应返回 true（区分未初始化与未配置）")
    void isInitialized_shouldReturnTrueEvenWhenTracerIsNull() {
        TracerHolder holder = new TracerHolder();
        holder.setTracer(null);

        assertNull(TracerHolder.get());
        assertTrue(TracerHolder.isInitialized(), "Spring 已注入（即使为 null），isInitialized 应为 true");
    }

    @Test
    @DisplayName("currentTraceId - 有当前 span 时应返回 traceId")
    void currentTraceId_shouldReturnTraceIdWhenSpanExists() {
        Tracer tracer = mock(Tracer.class);
        brave.Span span = mock(brave.Span.class);
        TraceContext context = mock(TraceContext.class);

        when(context.traceIdString()).thenReturn("a1b2c3d4e5f6a7b8");
        when(span.context()).thenReturn(context);
        when(tracer.currentSpan()).thenReturn(span);

        TracerHolder holder = new TracerHolder();
        holder.setTracer(tracer);

        assertEquals("a1b2c3d4e5f6a7b8", TracerHolder.currentTraceId());
    }

    @Test
    @DisplayName("currentTraceId - 无当前 span 时应返回 null")
    void currentTraceId_shouldReturnNullWhenNoCurrentSpan() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        TracerHolder holder = new TracerHolder();
        holder.setTracer(tracer);

        assertNull(TracerHolder.currentTraceId());
    }

    @Test
    @DisplayName("currentTraceId - 未注入 Tracer 时应返回 null")
    void currentTraceId_shouldReturnNullWhenTracerNotConfigured() {
        assertNull(TracerHolder.currentTraceId());
    }
}
