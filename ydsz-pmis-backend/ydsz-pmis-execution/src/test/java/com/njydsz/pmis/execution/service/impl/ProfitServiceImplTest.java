package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ProfitSnapshotDTO;
import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProfitServiceImpl 利润服务测试")
class ProfitServiceImplTest {

    private ProfitSnapshotMapper mapper;
    private ProfitServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProfitSnapshotMapper.class);
        service = new ProfitServiceImpl(mapper);
    }

    @Test
    @DisplayName("generateSnapshot - 缺参拒绝")
    void snapshotMissing() {
        ProfitSnapshotDTO dto = new ProfitSnapshotDTO();
        assertThatThrownBy(() -> service.generateSnapshot(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("generateSnapshot - 新建并计算派生字段")
    void snapshotCreate() {
        when(mapper.selectByInitiationAndPeriod(1L, "2026-06")).thenReturn(null);
        when(mapper.insert(any(ProfitSnapshotDO.class))).thenAnswer(inv -> {
            ProfitSnapshotDO s = inv.getArgument(0);
            s.setId(100L);
            return 1;
        });
        ProfitSnapshotDTO dto = new ProfitSnapshotDTO();
        dto.setInitiationId(1L);
        dto.setPeriod("2026-06");
        dto.setRecognizedRevenue(new BigDecimal("1000"));
        dto.setLaborCost(new BigDecimal("200"));
        dto.setPurchaseCost(new BigDecimal("100"));
        dto.setProgressPct(new BigDecimal("60"));
        Long id = service.generateSnapshot(dto);
        assertThat(id).isEqualTo(100L);

        ArgumentCaptor<ProfitSnapshotDO> captor = ArgumentCaptor.forClass(ProfitSnapshotDO.class);
        verify(mapper).insert(captor.capture());
        ProfitSnapshotDO saved = captor.getValue();
        assertThat(saved.getTotalCost()).isEqualByComparingTo("300");
        assertThat(saved.getGrossProfit()).isEqualByComparingTo("700");
        assertThat(saved.getGrossMargin()).isEqualByComparingTo("0.7000");
    }

    @Test
    @DisplayName("generateSnapshot - 已存在则更新")
    void snapshotUpdate() {
        ProfitSnapshotDO existing = new ProfitSnapshotDO();
        existing.setId(50L);
        existing.setInitiationId(1L);
        existing.setPeriod("2026-06");
        when(mapper.selectByInitiationAndPeriod(1L, "2026-06")).thenReturn(existing);
        ProfitSnapshotDTO dto = new ProfitSnapshotDTO();
        dto.setInitiationId(1L);
        dto.setPeriod("2026-06");
        dto.setRecognizedRevenue(new BigDecimal("500"));
        dto.setLaborCost(new BigDecimal("100"));
        Long id = service.generateSnapshot(dto);
        assertThat(id).isEqualTo(50L);
        verify(mapper).updateById(any(ProfitSnapshotDO.class));
    }

    @Test
    @DisplayName("healthScore - 无快照返回 -1")
    void healthScoreNone() {
        when(mapper.selectByInitiationAndPeriod(1L, "2026-06")).thenReturn(null);
        assertThat(service.healthScore(1L, "2026-06")).isEqualTo(-1);
    }
}
