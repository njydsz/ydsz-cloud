package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EvmMeasureServiceImpl EVM 服务测试")
class EvmMeasureServiceImplTest {

    private EvmMeasureMapper mapper;
    private ThresholdProvider thresholdProvider;
    private EvmMeasureServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(EvmMeasureMapper.class);
        thresholdProvider = mock(ThresholdProvider.class);
        when(thresholdProvider.cpiYellow()).thenReturn(0.95);
        when(thresholdProvider.cpiRed()).thenReturn(0.85);
        when(thresholdProvider.spiYellow()).thenReturn(0.90);
        when(thresholdProvider.spiRed()).thenReturn(0.80);
        service = new EvmMeasureServiceImpl(mapper, thresholdProvider);
    }

    @Test
    @DisplayName("save 缺 PV 必填")
    void saveMissingPv() {
        EvmMeasureCreateDTO dto = new EvmMeasureCreateDTO();
        dto.setInitiationId(1L);
        dto.setPeriod("2026-06");
        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("save 新增成功 触发 CPI 计算")
    void saveOk() {
        when(mapper.selectByInitiationAndPeriod(1L, null, "2026-06")).thenReturn(null);
        when(mapper.insert(any(EvmMeasureDO.class))).thenAnswer(inv -> {
            ((EvmMeasureDO) inv.getArgument(0)).setId(10L);
            return 1;
        });
        EvmMeasureCreateDTO dto = new EvmMeasureCreateDTO();
        dto.setInitiationId(1L);
        dto.setPeriod("2026-06");
        dto.setPv(new BigDecimal("100"));
        dto.setEv(new BigDecimal("80"));
        dto.setAc(new BigDecimal("100"));
        dto.setBac(new BigDecimal("1000"));
        Long id = service.save(dto);
        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("save 幂等更新 已存在则 update")
    void saveIdempotent() {
        EvmMeasureDO existing = new EvmMeasureDO();
        existing.setId(5L);
        when(mapper.selectByInitiationAndPeriod(1L, null, "2026-06")).thenReturn(existing);
        when(mapper.updateById(any(EvmMeasureDO.class))).thenReturn(1);
        EvmMeasureCreateDTO dto = new EvmMeasureCreateDTO();
        dto.setInitiationId(1L);
        dto.setPeriod("2026-06");
        dto.setPv(new BigDecimal("100"));
        dto.setEv(new BigDecimal("80"));
        dto.setAc(new BigDecimal("100"));
        dto.setBac(new BigDecimal("1000"));
        Long id = service.save(dto);
        assertThat(id).isEqualTo(5L);
    }

    @Test
    @DisplayName("getById 不存在 抛异常")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("dashboard 汇总 yellow/red 计数")
    void dashboard() {
        EvmMeasureDO m1 = new EvmMeasureDO();
        m1.setId(1L);
        m1.setPeriod("2026-06");
        m1.setCpi(new BigDecimal("0.80"));
        m1.setSpi(new BigDecimal("0.90"));
        m1.setEac(new BigDecimal("1200"));
        m1.setVac(new BigDecimal("-200"));
        m1.setAlertLevel("RED");
        m1.setCv(new BigDecimal("-20"));
        m1.setSv(new BigDecimal("-10"));
        m1.setVac(new BigDecimal("-200"));
        EvmMeasureDO m2 = new EvmMeasureDO();
        m2.setId(2L);
        m2.setPeriod("2026-05");
        m2.setCpi(new BigDecimal("0.93"));
        m2.setSpi(new BigDecimal("1.0"));
        m2.setAlertLevel("YELLOW");
        m2.setCv(new BigDecimal("-7"));
        m2.setSv(new BigDecimal("0"));
        m2.setVac(new BigDecimal("-50"));
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(m1, m2));
        Map<String, Object> dash = service.dashboard(1L);
        assertThat(dash).containsKey("latestCpi");
        assertThat(dash.get("measureCount")).isEqualTo(2);
        assertThat(dash.get("redCount")).isEqualTo(1);
        assertThat(dash.get("yellowCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("dashboard 空数据返回 NORMAL")
    void dashboardEmpty() {
        when(mapper.selectByInitiation(2L)).thenReturn(List.of());
        Map<String, Object> dash = service.dashboard(2L);
        assertThat(dash.get("alertLevel")).isEqualTo("NORMAL");
        assertThat(dash.get("measureCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("trend / listByInitiation / listByWbs null 安全")
    void safeList() {
        when(mapper.trendByPeriod(1L)).thenReturn(List.of());
        when(mapper.selectByInitiation(1L)).thenReturn(List.of());
        when(mapper.selectByWbs(1L)).thenReturn(List.of());
        assertThat(service.trend(1L)).isEmpty();
        assertThat(service.trend(null)).isEmpty();
        assertThat(service.listByInitiation(1L)).isEmpty();
        assertThat(service.listByInitiation(null)).isEmpty();
        assertThat(service.listByWbs(1L)).isEmpty();
        assertThat(service.listByWbs(null)).isEmpty();
    }

    @Test
    @DisplayName("recalculateBaseline - 自增版本号 + 统计受影响测量")
    void recalculateBaseline() {
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(new EvmMeasureDO(), new EvmMeasureDO(), new EvmMeasureDO()));
        Map<String, Object> r1 = service.recalculateBaseline(1L, "PROJECT_CHANGE:CHG-001");
        assertThat(r1.get("ok")).isEqualTo(true);
        assertThat(r1.get("baselineVersion")).isEqualTo(1);
        assertThat(r1.get("affectedMeasures")).isEqualTo(3);
        assertThat(r1.get("recalcReason")).isEqualTo("PROJECT_CHANGE:CHG-001");

        // 第二次调用版本号应继续自增
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(new EvmMeasureDO()));
        Map<String, Object> r2 = service.recalculateBaseline(1L, "PROJECT_CHANGE:CHG-002");
        assertThat(r2.get("baselineVersion")).isEqualTo(2);
        assertThat(r2.get("affectedMeasures")).isEqualTo(1);
    }

    @Test
    @DisplayName("recalculateBaseline - null initiationId 返回失败")
    void recalculateBaselineNull() {
        Map<String, Object> r = service.recalculateBaseline(null, "X");
        assertThat(r.get("ok")).isEqualTo(false);
    }

    @Test
    @DisplayName("currentBaselineVersion - 未重算过返回 0")
    void currentBaselineVersionZero() {
        assertThat(service.currentBaselineVersion(999L)).isEqualTo(0);
        assertThat(service.currentBaselineVersion(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("currentBaselineVersion - 重算后返回当前版本号")
    void currentBaselineVersionAfter() {
        when(mapper.selectByInitiation(5L)).thenReturn(List.of());
        service.recalculateBaseline(5L, "X");
        service.recalculateBaseline(5L, "X");
        assertThat(service.currentBaselineVersion(5L)).isEqualTo(2);
    }
}
