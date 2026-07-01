package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.DailyReconcileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 每日对账 Job Handler 测试
 */
@DisplayName("DailyReconcileJobHandler 每日对账")
class DailyReconcileJobHandlerTest {

    private DailyReconcileService service;
    private DailyReconcileJobHandler handler;

    @BeforeEach
    void setUp() {
        service = mock(DailyReconcileService.class);
        handler = new DailyReconcileJobHandler(service);
    }

    @Test
    @DisplayName("execute 默认前一天对账")
    void execute_default() throws Exception {
        when(service.runDaily(any(LocalDate.class))).thenReturn(6);
        Object r = handler.execute(null);
        assertThat(r).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) r;
        assertThat(map.get("recordCount")).isEqualTo(6);

        ArgumentCaptor<LocalDate> cap = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).runDaily(cap.capture());
        assertThat(cap.getValue()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    @DisplayName("execute 解析 date 参数")
    void execute_customDate() throws Exception {
        when(service.runDaily(any(LocalDate.class))).thenReturn(6);
        Object r = handler.execute("{\"date\":\"2026-05-01\"}");
        ArgumentCaptor<LocalDate> cap = ArgumentCaptor.forClass(LocalDate.class);
        verify(service).runDaily(cap.capture());
        assertThat(cap.getValue()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("execute service 抛异常时包装抛出")
    void execute_serviceException() throws Exception {
        when(service.runDaily(any(LocalDate.class)))
                .thenThrow(new RuntimeException("DB down"));
        assertThatThrownBy(() -> handler.execute(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }
}
