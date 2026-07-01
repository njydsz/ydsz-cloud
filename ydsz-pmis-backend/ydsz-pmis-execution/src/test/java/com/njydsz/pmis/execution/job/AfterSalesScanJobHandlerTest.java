package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.OpsTicketService;
import com.njydsz.pmis.execution.service.WarrantyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后巡检 Job Handler 测试
 */
@DisplayName("AfterSalesScanJobHandler 售后巡检")
class AfterSalesScanJobHandlerTest {

    private WarrantyService warrantyService;
    private OpsTicketService opsTicketService;
    private AfterSalesScanJobHandler handler;

    @BeforeEach
    void setUp() {
        warrantyService = mock(WarrantyService.class);
        opsTicketService = mock(OpsTicketService.class);
        handler = new AfterSalesScanJobHandler(warrantyService, opsTicketService);
    }

    @Test
    @DisplayName("execute 默认 noticeDays=30 同时扫描三项")
    void execute_default() {
        when(warrantyService.scanExpiring(any(LocalDate.class), anyInt())).thenReturn(2);
        when(warrantyService.scanOverdue(any(LocalDate.class))).thenReturn(1);
        when(opsTicketService.scanSlaBreaches(any(LocalDate.class))).thenReturn(3);

        Object r = handler.execute(null);
        assertThat(r).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) r;
        assertThat(map.get("expiringCount")).isEqualTo(2);
        assertThat(map.get("expiredCount")).isEqualTo(1);
        assertThat(map.get("slaBreachCount")).isEqualTo(3);
        assertThat(map.get("noticeDays")).isEqualTo(30);

        verify(warrantyService).scanExpiring(any(LocalDate.class), anyInt());
        verify(warrantyService).scanOverdue(any(LocalDate.class));
        verify(opsTicketService).scanSlaBreaches(any(LocalDate.class));
    }

    @Test
    @DisplayName("execute 解析自定义 noticeDays=60")
    void execute_customNoticeDays() {
        when(warrantyService.scanExpiring(any(LocalDate.class), anyInt())).thenReturn(0);
        when(warrantyService.scanOverdue(any(LocalDate.class))).thenReturn(0);
        when(opsTicketService.scanSlaBreaches(any(LocalDate.class))).thenReturn(0);
        handler.execute("{\"noticeDays\":60}");
        verify(warrantyService).scanExpiring(any(LocalDate.class), org.mockito.ArgumentMatchers.eq(60));
    }

    @Test
    @DisplayName("execute 子任务异常被吞掉，主任务不失败")
    void execute_subTaskException() {
        when(warrantyService.scanExpiring(any(LocalDate.class), anyInt()))
                .thenThrow(new RuntimeException("warranty down"));
        when(warrantyService.scanOverdue(any(LocalDate.class))).thenReturn(0);
        when(opsTicketService.scanSlaBreaches(any(LocalDate.class))).thenReturn(0);

        Object r = handler.execute(null);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) r;
        assertThat(map.get("expiringCount")).isEqualTo(0);
        assertThat(map.get("expiredCount")).isEqualTo(0);
        assertThat(map.get("slaBreachCount")).isEqualTo(0);
    }
}
