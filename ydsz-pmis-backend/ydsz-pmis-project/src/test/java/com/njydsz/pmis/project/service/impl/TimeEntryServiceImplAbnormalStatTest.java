package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.enums.TimeEntryStatus;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import com.njydsz.pmis.project.service.CostAllocationService;
import com.njydsz.pmis.project.service.RateCardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TimeEntryServiceImpl abnormalStat 单元测试（P0-5）
 *
 * <p>验证工时异常统计方法的核心行为：正常统计（加班/漏报/异常/总工时）、空记录、
 * 空 initiationId 提前返回、非法月份格式兜底当前月。
 *
 * @author ydsy-pmis-team
 * @since 1.0.0 (P0-5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TimeEntryServiceImpl abnormalStat 工时异常统计测试")
class TimeEntryServiceImplAbnormalStatTest {

    @Mock
    private TimeEntryMapper timeEntryMapper;
    @Mock
    private NameAssembler nameAssembler;
    @Mock
    private CostAllocationService costAllocationService;
    @Mock
    private RateCardService rateCardService;

    @InjectMocks
    private TimeEntryServiceImpl service;

    @Test
    @DisplayName("正常统计：1条加班、1条草稿、1条驳回，总工时24")
    void testAbnormalStat_正常统计() {
        // 记录1：加班（overtime>0），状态 APPROVED
        TimeEntryDO r1 = new TimeEntryDO();
        r1.setHours(new BigDecimal("8"));
        r1.setOvertime(new BigDecimal("2"));
        r1.setStatus(TimeEntryStatus.APPROVED.getCode());

        // 记录2：草稿（漏报），无加班
        TimeEntryDO r2 = new TimeEntryDO();
        r2.setHours(new BigDecimal("8"));
        r2.setOvertime(BigDecimal.ZERO);
        r2.setStatus(TimeEntryStatus.DRAFT.getCode());

        // 记录3：驳回（异常），无加班
        TimeEntryDO r3 = new TimeEntryDO();
        r3.setHours(new BigDecimal("8"));
        r3.setOvertime(BigDecimal.ZERO);
        r3.setStatus(TimeEntryStatus.REJECTED.getCode());

        when(timeEntryMapper.selectByInitiationAndDateRange(any(), any(), any()))
                .thenReturn(List.of(r1, r2, r3));

        Map<String, Object> result = service.abnormalStat("P001", "2026-07");

        assertEquals(1, result.get("overtimeCount"));
        assertEquals(1, result.get("missingCount"));
        assertEquals(1, result.get("abnormalCount"));
        assertEquals(new BigDecimal("24"), result.get("totalHours"));
    }

    @Test
    @DisplayName("空记录列表：返回全零值")
    void testAbnormalStat_空记录() {
        when(timeEntryMapper.selectByInitiationAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.abnormalStat("P001", "2026-07");

        assertEquals(0, result.get("overtimeCount"));
        assertEquals(0, result.get("missingCount"));
        assertEquals(0, result.get("abnormalCount"));
        assertEquals(BigDecimal.ZERO, result.get("totalHours"));
    }

    @Test
    @DisplayName("空 initiationId：返回零值且不调用 mapper")
    void testAbnormalStat_空initiationId() {
        Map<String, Object> result = service.abnormalStat(null, "2026-07");

        assertEquals(0, result.get("overtimeCount"));
        assertEquals(0, result.get("missingCount"));
        assertEquals(0, result.get("abnormalCount"));
        assertEquals(BigDecimal.ZERO, result.get("totalHours"));
        // initiationId 为空时提前返回，不应调用 mapper
        verify(timeEntryMapper, never()).selectByInitiationAndDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("非法月份格式：兜底当前月不抛异常")
    void testAbnormalStat_非法月份格式() {
        when(timeEntryMapper.selectByInitiationAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        Map<String, Object> result = assertDoesNotThrow(() -> service.abnormalStat("P001", "invalid"));

        assertEquals(0, result.get("overtimeCount"));
        assertEquals(0, result.get("missingCount"));
        assertEquals(0, result.get("abnormalCount"));
        assertEquals(BigDecimal.ZERO, result.get("totalHours"));
    }
}
