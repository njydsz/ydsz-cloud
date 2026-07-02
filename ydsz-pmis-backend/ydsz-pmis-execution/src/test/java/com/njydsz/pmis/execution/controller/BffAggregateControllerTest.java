package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.execution.service.CockpitReportService;
import com.njydsz.pmis.execution.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BffAggregateController 单元测试。
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
    void projectDetailAggregate_shouldReturnAllSections() {
        Map<String, Object> result = controller.projectDetailAggregate(1L);
        assertThat(result).containsKeys("initiation", "evm", "contracts", "wbsOverview");
    }

    @Test
    void dashboardSummary_shouldReturnKpiAlertsTodos() {
        Map<String, Object> result = controller.dashboardSummary(1L);
        assertThat(result).containsKeys("kpi", "alerts", "todos");
    }
}
