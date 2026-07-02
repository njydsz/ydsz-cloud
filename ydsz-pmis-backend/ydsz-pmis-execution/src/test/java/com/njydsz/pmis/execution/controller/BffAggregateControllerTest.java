package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.service.CockpitReportService;
import com.njydsz.pmis.execution.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BffAggregateController 单元测试。
 *
 * <p>验证 BFF 聚合接口真正调用了注入的 Service，并返回真实聚合数据，
 * 同时验证单维度异常时优雅降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class BffAggregateControllerTest {

    @Mock
    private CockpitReportService cockpitReportService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private BffAggregateController controller;

    @Test
    void projectDetailAggregate_shouldReturnRealServiceData() {
        // given
        Long initiationId = 1L;
        Map<String, Object> lifecycle = Map.of("initiationId", initiationId, "stage", "EXECUTION");
        Map<String, Object> profit = Map.of("cpi", 0.95, "spi", 1.05);
        Map<String, Object> payment = Map.of("totalPaid", new BigDecimal("100000"));
        Map<String, Object> cost = Map.of("laborCost", new BigDecimal("50000"));

        when(reportService.projectLifecycleReport(initiationId)).thenReturn(lifecycle);
        when(reportService.projectProfitReport(eq(initiationId), isNull())).thenReturn(profit);
        when(reportService.paymentLedgerReport(initiationId)).thenReturn(payment);
        when(reportService.costDetailReport(eq(initiationId), isNull())).thenReturn(cost);

        // when
        Map<String, Object> result = controller.projectDetailAggregate(initiationId);

        // then
        assertThat(result).containsKeys("initiation", "evm", "contracts", "wbsOverview");
        assertThat(result.get("initiation")).isEqualTo(lifecycle);
        assertThat(result.get("evm")).isEqualTo(profit);
        assertThat(result.get("contracts")).isEqualTo(payment);
        assertThat(result.get("wbsOverview")).isEqualTo(cost);

        // 验证真实调用了注入的 Service
        verify(reportService).projectLifecycleReport(initiationId);
        verify(reportService).projectProfitReport(eq(initiationId), isNull());
        verify(reportService).paymentLedgerReport(initiationId);
        verify(reportService).costDetailReport(eq(initiationId), isNull());
    }

    @Test
    void projectDetailAggregate_shouldDegradeGracefullyWhenServiceThrows() {
        // given
        Long initiationId = 2L;
        when(reportService.projectLifecycleReport(initiationId))
                .thenThrow(new RuntimeException("db timeout"));
        when(reportService.projectProfitReport(eq(initiationId), isNull()))
                .thenThrow(new RuntimeException("evm timeout"));
        when(reportService.paymentLedgerReport(initiationId))
                .thenThrow(new RuntimeException("payment timeout"));
        when(reportService.costDetailReport(eq(initiationId), isNull()))
                .thenThrow(new RuntimeException("cost timeout"));

        // when
        Map<String, Object> result = controller.projectDetailAggregate(initiationId);

        // then - 单维度异常不影响整体结构返回
        assertThat(result).containsKeys("initiation", "evm", "contracts", "wbsOverview");
        assertThat((Map<?, ?>) result.get("initiation")).containsKey("error");
        assertThat((Map<?, ?>) result.get("evm")).containsKey("error");
        assertThat((List<?>) result.get("contracts")).isEmpty();
        assertThat((Map<?, ?>) result.get("wbsOverview")).containsKey("error");
    }

    @Test
    void dashboardSummary_shouldReturnRealKpiAndAlerts() {
        // given
        CockpitKpiVO kpi = new CockpitKpiVO();
        kpi.setActiveProjects(12);
        kpi.setTotalContractAmount(new BigDecimal("5000000"));
        kpi.setConfirmedRevenue(new BigDecimal("3000000"));
        kpi.setTotalCost(new BigDecimal("2000000"));

        CockpitAlertSummaryVO alerts = CockpitAlertSummaryVO.builder()
                .redCount(2)
                .yellowCount(5)
                .infoCount(8)
                .totalCount(15)
                .events(List.of())
                .build();

        when(cockpitReportService.overview(isNull(), isNull())).thenReturn(kpi);
        when(cockpitReportService.alertSummary(isNull(), isNull())).thenReturn(alerts);

        // when
        Map<String, Object> result = controller.dashboardSummary(1L);

        // then
        assertThat(result).containsKeys("kpi", "alerts", "todos");
        assertThat(result.get("kpi")).isSameAs(kpi);
        assertThat(result.get("alerts")).isSameAs(alerts);
        assertThat((List<?>) result.get("todos")).isEmpty();

        // 验证真实调用了注入的 Service
        verify(cockpitReportService).overview(isNull(), isNull());
        verify(cockpitReportService).alertSummary(isNull(), isNull());
    }

    @Test
    void dashboardSummary_shouldDegradeGracefullyWhenServiceThrows() {
        // given
        when(cockpitReportService.overview(any(), any())).thenThrow(new RuntimeException("overview down"));
        when(cockpitReportService.alertSummary(any(), any())).thenThrow(new RuntimeException("alert down"));

        // when
        Map<String, Object> result = controller.dashboardSummary(99L);

        // then
        assertThat(result).containsKeys("kpi", "alerts", "todos");
        assertThat((Map<?, ?>) result.get("kpi")).containsKey("error");
        assertThat((List<?>) result.get("alerts")).isEmpty();
        assertThat((List<?>) result.get("todos")).isEmpty();
    }
}
