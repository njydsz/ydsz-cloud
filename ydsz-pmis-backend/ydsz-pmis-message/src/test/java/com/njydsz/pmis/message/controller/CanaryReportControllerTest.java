package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.CanaryReportVO;
import com.njydsz.pmis.message.service.CanaryReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CanaryReportController} 单元测试（P1-6 灰度 A/B 报表）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CanaryReportController 灰度A/B报表测试")
@ExtendWith(MockitoExtension.class)
class CanaryReportControllerTest {

    @Mock
    private CanaryReportService canaryReportService;

    @InjectMocks
    private CanaryReportController canaryReportController;

    @Test
    @DisplayName("getReport 委托 service 并返回成功")
    void getReportShouldDelegateToService() {
        CanaryReportVO vo = new CanaryReportVO();
        vo.setCanaryKey("TPL_ORDER");
        when(canaryReportService.getReport("TPL_ORDER", null, null)).thenReturn(vo);

        Result<CanaryReportVO> result = canaryReportController.getReport("TPL_ORDER", null, null);

        assertTrue(result.isSuccess());
        assertEquals("TPL_ORDER", result.getData().getCanaryKey());
        verify(canaryReportService).getReport("TPL_ORDER", null, null);
    }

    @Test
    @DisplayName("getReport 带时间参数正确传递")
    void getReportShouldPassTimeParams() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        CanaryReportVO vo = new CanaryReportVO();
        when(canaryReportService.getReport("TPL_TEST", start, end)).thenReturn(vo);

        Result<CanaryReportVO> result = canaryReportController.getReport("TPL_TEST", start, end);

        assertTrue(result.isSuccess());
        verify(canaryReportService).getReport("TPL_TEST", start, end);
    }

    @Test
    @DisplayName("getReport 返回包含对照组与实验组统计")
    void getReportShouldReturnBothGroups() {
        CanaryReportVO vo = new CanaryReportVO();
        vo.setCanaryKey("TPL_AB");
        vo.setControl(new CanaryReportVO.GroupStats());
        vo.setTreatment(new CanaryReportVO.GroupStats());
        vo.getTreatment().setTotal(100L);
        vo.getControl().setTotal(200L);
        when(canaryReportService.getReport("TPL_AB", null, null)).thenReturn(vo);

        Result<CanaryReportVO> result = canaryReportController.getReport("TPL_AB", null, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().getControl());
        assertNotNull(result.getData().getTreatment());
        assertEquals(100L, result.getData().getTreatment().getTotal());
        assertEquals(200L, result.getData().getControl().getTotal());
    }
}
