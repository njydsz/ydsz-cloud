package com.njydsz.pmis.execution.listener;

import com.njydsz.pmis.common.event.ProjectChangeExecutedEvent;
import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.service.AlertDispatchService;
import com.njydsz.pmis.execution.service.EvmMeasureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectChangeExecutedEventListener 测试
 *
 * <p>覆盖: 基线重算成功路径, 基线重算失败→告警路径, 空事件短路,
 * 告警发布失败主流程不中断等场景.
 */
@DisplayName("ProjectChangeExecutedEventListener")
class ProjectChangeExecutedEventListenerTest {

    private EvmMeasureService evmMeasureService;
    private AlertDispatchService alertDispatchService;
    private ProjectChangeExecutedEventListener listener;

    @BeforeEach
    void setUp() {
        evmMeasureService = mock(EvmMeasureService.class);
        alertDispatchService = mock(AlertDispatchService.class);
        listener = new ProjectChangeExecutedEventListener(evmMeasureService, alertDispatchService);
    }

    @Test
    @DisplayName("null 事件静默处理")
    void onEvent_null() {
        listener.onProjectChangeExecuted(null);
        verify(evmMeasureService, never()).recalculateBaseline(anyLong(), anyString());
        verify(alertDispatchService, never()).submit(any());
    }

    @Test
    @DisplayName("initiationId 为空 → 静默处理")
    void onEvent_nullInitiation() {
        listener.onProjectChangeExecuted(ProjectChangeExecutedEvent.builder()
                .changeId(1L)
                .changeCode("CHG-001")
                .build());
        verify(evmMeasureService, never()).recalculateBaseline(anyLong(), anyString());
        verify(alertDispatchService, never()).submit(any());
    }

    @Test
    @DisplayName("基线重算成功 → 不发告警")
    void onEvent_recalcSuccess() {
        when(evmMeasureService.recalculateBaseline(anyLong(), anyString()))
                .thenReturn(new HashMap<>());
        listener.onProjectChangeExecuted(buildEvent());
        verify(evmMeasureService).recalculateBaseline(anyLong(), anyString());
        verify(alertDispatchService, never()).submit(any());
    }

    @Test
    @DisplayName("基线重算失败 → 发布 RED 级 EVM 告警")
    void onEvent_recalcFailure_publishAlert() {
        when(evmMeasureService.recalculateBaseline(anyLong(), anyString()))
                .thenThrow(new RuntimeException("evm mapper down"));
        when(alertDispatchService.submit(any())).thenReturn(101L);

        listener.onProjectChangeExecuted(buildEvent());

        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        AlertDispatchDTO dto = cap.getValue();
        assertThat(dto.getAlertType()).isEqualTo("EVM");
        assertThat(dto.getAlertLevel()).isEqualTo("RED");
        assertThat(dto.getSourceType()).isEqualTo("execution");
        assertThat(dto.getSourceId()).isEqualTo("777");
        assertThat(dto.getTitle()).contains("EVM基线重算失败").contains("CHG-001");
        assertThat(dto.getContent()).contains("CHG-001").contains("evm mapper down");
        assertThat(dto.getDispatchedBy()).isEqualTo("ProjectChangeExecutedEventListener");
    }

    @Test
    @DisplayName("基线重算失败 + 告警发布也失败 → 主流程不抛异常")
    void onEvent_recalcAndAlertBothFail() {
        when(evmMeasureService.recalculateBaseline(anyLong(), anyString()))
                .thenThrow(new RuntimeException("evm down"));
        doThrow(new RuntimeException("alert service down"))
                .when(alertDispatchService).submit(any());

        // 不应抛出
        listener.onProjectChangeExecuted(buildEvent());
        verify(alertDispatchService).submit(any());
    }

    @Test
    @DisplayName("重大变更触发的失败告警在内容中标记 major=true")
    void onEvent_majorChange() {
        when(evmMeasureService.recalculateBaseline(anyLong(), anyString()))
                .thenThrow(new RuntimeException("boom"));
        when(alertDispatchService.submit(any())).thenReturn(1L);

        listener.onProjectChangeExecuted(ProjectChangeExecutedEvent.builder()
                .changeId(2L)
                .changeCode("CHG-MAJOR-001")
                .initiationId(888L)
                .changeType("SCOPE")
                .majorFlag(Boolean.TRUE)
                .finalStatusCode("EXECUTED")
                .timestamp(System.currentTimeMillis())
                .build());

        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        assertThat(cap.getValue().getContent()).contains("major=true");
        assertThat(cap.getValue().getSourceId()).isEqualTo("888");
    }

    @Test
    @DisplayName("changeCode 为空时 sourceId 仍取 initiationId")
    void onEvent_changeCodeEmpty() {
        when(evmMeasureService.recalculateBaseline(anyLong(), anyString()))
                .thenThrow(new RuntimeException("x"));
        when(alertDispatchService.submit(any())).thenReturn(1L);

        listener.onProjectChangeExecuted(ProjectChangeExecutedEvent.builder()
                .changeId(3L)
                .initiationId(42L)
                .build());

        ArgumentCaptor<AlertDispatchDTO> cap = ArgumentCaptor.forClass(AlertDispatchDTO.class);
        verify(alertDispatchService).submit(cap.capture());
        assertThat(cap.getValue().getSourceId()).isEqualTo("42");
    }

    private ProjectChangeExecutedEvent buildEvent() {
        return ProjectChangeExecutedEvent.builder()
                .changeId(1L)
                .changeCode("CHG-001")
                .changeTitle("范围调整")
                .initiationId(777L)
                .changeType("SCOPE")
                .majorFlag(Boolean.FALSE)
                .finalStatusCode("EXECUTING")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
