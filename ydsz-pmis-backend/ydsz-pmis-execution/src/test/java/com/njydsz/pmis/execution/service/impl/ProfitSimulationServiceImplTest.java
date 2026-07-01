package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.execution.dto.SimulationStatusDTO;
import com.njydsz.pmis.execution.entity.ProfitSimulationDO;
import com.njydsz.pmis.execution.mapper.ProfitSimulationMapper;
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

@DisplayName("ProfitSimulationServiceImpl 利润测算")
class ProfitSimulationServiceImplTest {

    private ProfitSimulationMapper mapper;
    private ProfitSimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProfitSimulationMapper.class);
        service = new ProfitSimulationServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 缺 simulationCode")
    void createMissingCode() {
        ProfitSimulationCreateDTO dto = new ProfitSimulationCreateDTO();
        dto.setInitiationId(1L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 重复编号")
    void createDuplicate() {
        when(mapper.selectByCode("SIM-1")).thenReturn(new ProfitSimulationDO());
        ProfitSimulationCreateDTO dto = new ProfitSimulationCreateDTO();
        dto.setSimulationCode("SIM-1");
        dto.setSimulationName("测算1");
        dto.setInitiationId(1L);
        dto.setContractAmount(new BigDecimal("100000"));
        dto.setTargetMargin(new BigDecimal("0.30"));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功 自动算 V1 + 利润指标")
    void createOk() {
        when(mapper.selectByCode("SIM-1")).thenReturn(null);
        when(mapper.maxVersion(1L)).thenReturn(0);
        when(mapper.insert(any(ProfitSimulationDO.class))).thenAnswer(inv -> {
            ((ProfitSimulationDO) inv.getArgument(0)).setId(50L);
            return 1;
        });
        ProfitSimulationCreateDTO dto = new ProfitSimulationCreateDTO();
        dto.setSimulationCode("SIM-1");
        dto.setSimulationName("基准测算");
        dto.setInitiationId(1L);
        dto.setContractAmount(new BigDecimal("1000000"));
        dto.setTargetMargin(new BigDecimal("0.30"));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(50L);
    }

    @Test
    @DisplayName("create 自动续版本 V2 V3")
    void createVersionIncrement() {
        when(mapper.selectByCode("SIM-2")).thenReturn(null);
        when(mapper.maxVersion(1L)).thenReturn(2);
        when(mapper.insert(any(ProfitSimulationDO.class))).thenAnswer(inv -> {
            ProfitSimulationDO s = inv.getArgument(0);
            s.setId(60L);
            return 1;
        });
        ProfitSimulationCreateDTO dto = new ProfitSimulationCreateDTO();
        dto.setSimulationCode("SIM-2");
        dto.setSimulationName("乐观测算");
        dto.setInitiationId(1L);
        dto.setContractAmount(new BigDecimal("1200000"));
        dto.setTargetMargin(new BigDecimal("0.35"));
        dto.setScenarioType("OPTIMISTIC");
        service.create(dto);
        // version 应为 3
        org.mockito.ArgumentCaptor<ProfitSimulationDO> captor =
                org.mockito.ArgumentCaptor.forClass(ProfitSimulationDO.class);
        org.mockito.Mockito.verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        assertThat(captor.getValue().getScenarioType()).isEqualTo("OPTIMISTIC");
    }

    @Test
    @DisplayName("changeStatus DRAFT → SUBMITTED")
    void changeStatus() {
        ProfitSimulationDO s = new ProfitSimulationDO();
        s.setId(1L);
        s.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(s);
        when(mapper.updateById(any(ProfitSimulationDO.class))).thenReturn(1);
        SimulationStatusDTO dto = new SimulationStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeStatus(dto);
    }

    @Test
    @DisplayName("changeStatus 非法迁移")
    void changeStatusInvalid() {
        ProfitSimulationDO s = new ProfitSimulationDO();
        s.setId(1L);
        s.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(s);
        SimulationStatusDTO dto = new SimulationStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("APPROVED");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete 已审批不可删")
    void deleteApprovedBlock() {
        ProfitSimulationDO s = new ProfitSimulationDO();
        s.setId(1L);
        s.setStatus("APPROVED");
        when(mapper.selectById(1L)).thenReturn(s);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete 草稿可删")
    void deleteDraftOk() {
        ProfitSimulationDO s = new ProfitSimulationDO();
        s.setId(1L);
        s.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(s);
        service.delete(1L);
    }

    @Test
    @DisplayName("compare 多版本对比")
    void compare() {
        ProfitSimulationDO a = new ProfitSimulationDO();
        a.setId(1L);
        a.setSimulationCode("SIM-1");
        a.setSimulationName("基准");
        a.setVersion(1);
        a.setScenarioType("BASE");
        a.setExternalRevenue(new BigDecimal("1000000"));
        a.setInternalCost(new BigDecimal("700000"));
        a.setGrossProfit(new BigDecimal("300000"));
        a.setGrossMargin(new BigDecimal("0.30"));
        a.setTargetMargin(new BigDecimal("0.30"));
        a.setStatus("APPROVED");
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(a));
        List<Map<String, Object>> result = service.compare(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("marginAchieved")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("listByInitiation null 安全")
    void listSafe() {
        when(mapper.selectByInitiation(1L)).thenReturn(List.of());
        assertThat(service.listByInitiation(1L)).isEmpty();
        assertThat(service.listByInitiation(null)).isEmpty();
    }

    @Test
    @DisplayName("changeStatus 未知状态")
    void changeStatusUnknown() {
        ProfitSimulationDO s = new ProfitSimulationDO();
        s.setId(1L);
        s.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(s);
        SimulationStatusDTO dto = new SimulationStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("X");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("getById 不存在")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class);
    }
}
