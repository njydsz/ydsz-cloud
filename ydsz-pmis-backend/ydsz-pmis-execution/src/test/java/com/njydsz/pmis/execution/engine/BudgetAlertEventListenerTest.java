package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.service.AlertDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BudgetAlertEventListener 测试
 */
@DisplayName("BudgetAlertEventListener 预算告警事件")
class BudgetAlertEventListenerTest {

    private AlertDispatchService alertDispatchService;
    private BudgetAlertEventListener listener;

    @BeforeEach
    void setUp() {
        alertDispatchService = mock(AlertDispatchService.class);
        listener = new BudgetAlertEventListener(alertDispatchService);
    }

    @Test
    @DisplayName("null 事件静默处理")
    void onEvent_null() {
        listener.onBudgetAlert(null);
        verify(alertDispatchService, never()).submit(any());
    }

    @Test
    @DisplayName("黄色事件 → AlertDispatchService.submit YELLOW")
    void onEvent_yellow() {
        when(alertDispatchService.submit(any())).thenReturn(1L);
        BudgetAlertEvent e = BudgetAlertEvent.builder()
                .initiationId(10L)
                .projectCode("P001")
                .projectName("数字孪生")
                .bizType("PURCHASE")
                .delta(new BigDecimal("10000"))
                .usedAfter(new BigDecimal("85000"))
                .budget(new BigDecimal("100000"))
                .ratio(new BigDecimal("0.85"))
                .level(BudgetAlertEvent.Level.YELLOW)
                .timestamp(System.currentTimeMillis())
                .build();
        listener.onBudgetAlert(e);

        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        AlertDispatchDTO dto = cap.getValue();
        assertThat(dto.getAlertType()).isEqualTo("BUDGET");
        assertThat(dto.getAlertLevel()).isEqualTo("YELLOW");
        assertThat(dto.getSourceId()).isEqualTo("10");
        assertThat(dto.getTitle()).contains("P001").contains("PURCHASE");
        assertThat(dto.getContent()).contains("85.00%");
        assertThat(dto.getDispatchedBy()).isEqualTo("BudgetGuard");
    }

    @Test
    @DisplayName("红色事件 → AlertDispatchService.submit RED")
    void onEvent_red() {
        when(alertDispatchService.submit(any())).thenReturn(2L);
        BudgetAlertEvent e = BudgetAlertEvent.builder()
                .initiationId(11L)
                .projectCode("P002")
                .projectName("云数中台")
                .bizType("EXPENSE")
                .delta(new BigDecimal("5000"))
                .usedAfter(new BigDecimal("97000"))
                .budget(new BigDecimal("100000"))
                .ratio(new BigDecimal("0.97"))
                .level(BudgetAlertEvent.Level.RED)
                .timestamp(System.currentTimeMillis())
                .build();
        listener.onBudgetAlert(e);
        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        assertThat(cap.getValue().getAlertLevel()).isEqualTo("RED");
        assertThat(cap.getValue().getContent()).contains("97.00%");
    }

    @Test
    @DisplayName("service.submit 异常时主流程不中断")
    void onEvent_serviceException() {
        doThrow(new RuntimeException("db down")).when(alertDispatchService).submit(any());
        BudgetAlertEvent e = BudgetAlertEvent.builder()
                .initiationId(11L)
                .bizType("EXPENSE")
                .level(BudgetAlertEvent.Level.YELLOW)
                .ratio(new BigDecimal("0.85"))
                .build();
        // 不抛异常
        listener.onBudgetAlert(e);
        verify(alertDispatchService).submit(any());
    }

    @Test
    @DisplayName("projectCode 为空时使用 initiationId 作为 sourceId")
    void onEvent_noProjectCode() {
        when(alertDispatchService.submit(any())).thenReturn(1L);
        BudgetAlertEvent e = BudgetAlertEvent.builder()
                .initiationId(99L)
                .bizType("PURCHASE")
                .level(BudgetAlertEvent.Level.YELLOW)
                .ratio(new BigDecimal("0.85"))
                .build();
        listener.onBudgetAlert(e);
        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        assertThat(cap.getValue().getSourceId()).isEqualTo("99");
    }
}
