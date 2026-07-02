package com.njydsz.pmis.common.filter;

import com.njydsz.pmis.common.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TraceIdFilter 单元测试
 *
 * <p>覆盖 traceId 自动生成、请求头透传与异常情况下 MDC 清理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TraceIdFilter 链路追踪过滤器测试")
class TraceIdFilterTest {

    @Test
    @DisplayName("请求头无 traceId 时应自动生成")
    void generateWhenMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(CommonConstants.HEADER_TRACE_ID)).thenReturn(null);

        filter.doFilter(req, resp, chain);

        // 请求中应写入 traceId
        ArgumentCaptor<String> traceIdCap = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(eq(CommonConstants.HEADER_TRACE_ID), traceIdCap.capture());
        assertThat(traceIdCap.getValue()).hasSize(16);

        verify(chain).doFilter(req, resp);
        // 调用后 MDC 应清空
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("请求头有 traceId 时应透传")
    void passThrough() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(CommonConstants.HEADER_TRACE_ID)).thenReturn("incoming-trace-123");

        filter.doFilter(req, resp, chain);

        verify(resp).setHeader(CommonConstants.HEADER_TRACE_ID, "incoming-trace-123");
        verify(chain).doFilter(req, resp);
    }

    @Test
    @DisplayName("异常情况下 MDC 也应被清空")
    void clearOnException() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(CommonConstants.HEADER_TRACE_ID)).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(chain).doFilter(any(), any());

        try {
            filter.doFilter(req, resp, chain);
        } catch (Exception ignored) {
        }
        assertThat(MDC.get("traceId")).isNull();
    }
}
