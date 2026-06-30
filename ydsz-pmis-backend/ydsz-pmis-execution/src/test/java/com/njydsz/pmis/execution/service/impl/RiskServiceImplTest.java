package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.RiskCreateDTO;
import com.njydsz.pmis.execution.dto.RiskStatusDTO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RiskServiceImpl 风险服务测试")
class RiskServiceImplTest {

    private RiskMapper mapper;
    private RiskServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RiskMapper.class);
        service = new RiskServiceImpl(mapper);
    }

    @Test
    @DisplayName("create - 缺少必填")
    void createMissing() {
        RiskCreateDTO dto = new RiskCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 自动评估风险等级")
    void createEvaluateLevel() {
        when(mapper.selectByCode("R-1")).thenReturn(null);
        when(mapper.insert(any(RiskDO.class))).thenAnswer(inv -> {
            RiskDO r = inv.getArgument(0);
            r.setId(7L);
            return 1;
        });
        RiskCreateDTO dto = new RiskCreateDTO();
        dto.setRiskCode("R-1");
        dto.setRiskTitle("客户需求变更");
        dto.setInitiationId(1L);
        dto.setOwnerId(2L);
        dto.setProbability("HIGH");
        dto.setImpact("HIGH");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(7L);
        ArgumentCaptor<RiskDO> captor = ArgumentCaptor.forClass(RiskDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("create - MEDIUM+HIGH 应为 HIGH")
    void createMediumHigh() {
        when(mapper.selectByCode("R-2")).thenReturn(null);
        when(mapper.insert(any(RiskDO.class))).thenReturn(1);
        RiskCreateDTO dto = new RiskCreateDTO();
        dto.setRiskCode("R-2");
        dto.setRiskTitle("工期紧张");
        dto.setInitiationId(1L);
        dto.setOwnerId(2L);
        dto.setProbability("MEDIUM");
        dto.setImpact("HIGH");
        service.create(dto);
        ArgumentCaptor<RiskDO> captor = ArgumentCaptor.forClass(RiskDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("changeStatus - 非法迁移拒绝")
    void changeStatusInvalid() {
        RiskDO r = new RiskDO();
        r.setId(1L);
        r.setStatus("CLOSED");
        when(mapper.selectById(1L)).thenReturn(r);
        RiskStatusDTO dto = new RiskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("OPEN");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete - 已发生不能删")
    void deleteOccurred() {
        RiskDO r = new RiskDO();
        r.setId(1L);
        r.setStatus("OCCURRED");
        when(mapper.selectById(1L)).thenReturn(r);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }
}
