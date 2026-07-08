package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.CanaryReportVO;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link CanaryReportServiceImpl} 单元测试（P1-6 灰度 A/B 报表）。
 *
 * <p>selectCount 调用顺序（共 16 次）：
 * <ol>
 *   <li>实验组(treatment): total, success, failed, retry, dead, delivered, read, clicked</li>
 *   <li>对照组(control): total, success, failed, retry, dead, delivered, read, clicked</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CanaryReportServiceImpl 灰度A/B报表测试")
@ExtendWith(MockitoExtension.class)
class CanaryReportServiceImplTest {

    @Mock
    private MsgLogMapper msgLogMapper;

    @InjectMocks
    private CanaryReportServiceImpl canaryReportService;

    @Test
    @DisplayName("getReport 正确聚合实验组与对照组统计与比率")
    void getReportShouldAggregateBothGroups() {
        // 实验组: total=100, success=80, failed=10, retry=5, dead=5, delivered=60, read=30, clicked=10
        // 对照组: total=200, success=150, failed=30, retry=10, dead=10, delivered=100, read=40, clicked=10
        when(msgLogMapper.selectCount(any())).thenReturn(
                // 实验组 treatment (8 次)
                100L, 80L, 10L, 5L, 5L, 60L, 30L, 10L,
                // 对照组 control (8 次)
                200L, 150L, 30L, 10L, 10L, 100L, 40L, 10L
        );

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        CanaryReportVO vo = canaryReportService.getReport("TPL_ORDER", start, end);

        assertEquals("TPL_ORDER", vo.getCanaryKey());
        assertNotNull(vo.getControl());
        assertNotNull(vo.getTreatment());

        // 实验组校验
        CanaryReportVO.GroupStats treatment = vo.getTreatment();
        assertEquals(100L, treatment.getTotal());
        assertEquals(80L, treatment.getSuccess());
        assertEquals(10L, treatment.getFailed());
        assertEquals(5L, treatment.getRetry());
        assertEquals(5L, treatment.getDead());
        assertEquals(60L, treatment.getDelivered());
        assertEquals(30L, treatment.getRead());
        assertEquals(10L, treatment.getClicked());
        // successRate = 80/100*100 = 80.0
        assertEquals(80.0, treatment.getSuccessRate(), 0.01);
        // deliveryRate = (60+30+10)/100*100 = 100.0
        assertEquals(100.0, treatment.getDeliveryRate(), 0.01);
        // readRate = (30+10)/100*100 = 40.0
        assertEquals(40.0, treatment.getReadRate(), 0.01);

        // 对照组校验
        CanaryReportVO.GroupStats control = vo.getControl();
        assertEquals(200L, control.getTotal());
        assertEquals(150L, control.getSuccess());
        assertEquals(30L, control.getFailed());
        assertEquals(10L, control.getRetry());
        assertEquals(10L, control.getDead());
        assertEquals(100L, control.getDelivered());
        assertEquals(40L, control.getRead());
        assertEquals(10L, control.getClicked());
        // successRate = 150/200*100 = 75.0
        assertEquals(75.0, control.getSuccessRate(), 0.01);
        // deliveryRate = (100+40+10)/200*100 = 75.0
        assertEquals(75.0, control.getDeliveryRate(), 0.01);
        // readRate = (40+10)/200*100 = 25.0
        assertEquals(25.0, control.getReadRate(), 0.01);

        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getReport total=0 时比率为 0 不抛异常")
    void getReportShouldReturnZeroRatesWhenNoData() {
        when(msgLogMapper.selectCount(any())).thenReturn(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, // treatment
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L  // control
        );

        CanaryReportVO vo = canaryReportService.getReport("TPL_EMPTY", null, null);

        assertEquals(0L, vo.getTreatment().getTotal());
        assertEquals(0.0, vo.getTreatment().getSuccessRate());
        assertEquals(0.0, vo.getTreatment().getDeliveryRate());
        assertEquals(0.0, vo.getTreatment().getReadRate());
        assertEquals(0L, vo.getControl().getTotal());
        assertEquals(0.0, vo.getControl().getSuccessRate());
        assertEquals(0.0, vo.getControl().getDeliveryRate());
        assertEquals(0.0, vo.getControl().getReadRate());
    }

    @Test
    @DisplayName("getReport null 时间参数时默认最近 7 天")
    void getReportShouldDefaultToLast7DaysWhenNullParams() {
        when(msgLogMapper.selectCount(any())).thenReturn(
                1L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, // treatment
                2L, 2L, 0L, 0L, 0L, 0L, 0L, 0L  // control
        );

        CanaryReportVO vo = canaryReportService.getReport("TPL_DEFAULT", null, null);

        assertEquals(1L, vo.getTreatment().getTotal());
        assertEquals(2L, vo.getControl().getTotal());
        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getReport 空 canaryKey 抛 BizException")
    void getReportShouldRejectBlankCanaryKey() {
        assertThrows(BizException.class, () -> canaryReportService.getReport("", null, null));
        assertThrows(BizException.class, () -> canaryReportService.getReport(null, null, null));
        assertThrows(BizException.class, () -> canaryReportService.getReport("   ", null, null));
    }

    @Test
    @DisplayName("getReport selectCount 返回 null 时按 0 处理")
    void getReportShouldTreatNullCountAsZero() {
        when(msgLogMapper.selectCount(any())).thenReturn(
                null, null, null, null, null, null, null, null, // treatment
                null, null, null, null, null, null, null, null  // control
        );

        CanaryReportVO vo = canaryReportService.getReport("TPL_NULL", null, null);

        assertEquals(0L, vo.getTreatment().getTotal());
        assertEquals(0L, vo.getControl().getTotal());
        assertEquals(0.0, vo.getTreatment().getSuccessRate());
        assertEquals(0.0, vo.getControl().getSuccessRate());
    }

    @Test
    @DisplayName("getReport 实验组优于对照组时比率对比正确")
    void getReportShouldReflectTreatmentBetterThanControl() {
        // 实验组: successRate=90%, deliveryRate=80%, readRate=30%
        // 对照组: successRate=60%, deliveryRate=45%, readRate=20%
        when(msgLogMapper.selectCount(any())).thenReturn(
                // treatment: total=100, success=90, failed=5, retry=3, dead=2, delivered=50, read=20, clicked=10
                100L, 90L, 5L, 3L, 2L, 50L, 20L, 10L,
                // control: total=100, success=60, failed=25, retry=10, dead=5, delivered=25, read=15, clicked=5
                100L, 60L, 25L, 10L, 5L, 25L, 15L, 5L
        );

        CanaryReportVO vo = canaryReportService.getReport("TPL_AB", null, null);

        // 实验组优于对照组
        assertEquals(90.0, vo.getTreatment().getSuccessRate(), 0.01);
        assertEquals(60.0, vo.getControl().getSuccessRate(), 0.01);
        // deliveryRate = (delivered + read + clicked) / total * 100
        // treatment: (50+20+10)/100*100 = 80.0
        // control: (25+15+5)/100*100 = 45.0
        assertEquals(80.0, vo.getTreatment().getDeliveryRate(), 0.01);
        assertEquals(45.0, vo.getControl().getDeliveryRate(), 0.01);
        // readRate = (read + clicked) / total * 100
        // treatment: (20+10)/100*100 = 30.0
        // control: (15+5)/100*100 = 20.0
        assertEquals(30.0, vo.getTreatment().getReadRate(), 0.01);
        assertEquals(20.0, vo.getControl().getReadRate(), 0.01);
    }
}
