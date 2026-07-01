package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.AlertDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预警重试 Job Handler 测试
 */
@DisplayName("AlertDispatchRetryJobHandler 预警重试")
class AlertDispatchRetryJobHandlerTest {

    private AlertDispatchService alertDispatchService;
    private AlertDispatchRetryJobHandler handler;

    @BeforeEach
    void setUp() {
        alertDispatchService = mock(AlertDispatchService.class);
        handler = new AlertDispatchRetryJobHandler(alertDispatchService);
    }

    @Test
    @DisplayName("execute 默认 maxRetry=3 返回重试计数")
    void execute_default() {
        when(alertDispatchService.retryFailed(anyInt())).thenReturn(5);
        Object r = handler.execute(null);
        assertThat(r).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) r;
        assertThat(map.get("retried")).isEqualTo(5);
        assertThat(map.get("maxRetry")).isEqualTo(3);
        verify(alertDispatchService).retryFailed(3);
    }

    @Test
    @DisplayName("execute 解析 maxRetry=5")
    void execute_customMaxRetry() {
        when(alertDispatchService.retryFailed(anyInt())).thenReturn(2);
        Object r = handler.execute("{\"maxRetry\":5}");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) r;
        assertThat(map.get("maxRetry")).isEqualTo(5);
        verify(alertDispatchService).retryFailed(5);
    }

    @Test
    @DisplayName("execute maxRetry<=0 走默认 3")
    void execute_invalidMaxRetry() {
        when(alertDispatchService.retryFailed(anyInt())).thenReturn(0);
        handler.execute("{\"maxRetry\":0}");
        verify(alertDispatchService).retryFailed(3);
    }
}
