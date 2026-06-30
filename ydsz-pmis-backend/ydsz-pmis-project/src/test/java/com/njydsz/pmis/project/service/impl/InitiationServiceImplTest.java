package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.GateReviewDTO;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.GateReviewDO;
import com.njydsz.pmis.project.entity.InitiationDO;
import com.njydsz.pmis.project.enums.GateCode;
import com.njydsz.pmis.project.feign.WorkflowServiceClient;
import com.njydsz.pmis.project.mapper.BudgetItemMapper;
import com.njydsz.pmis.project.mapper.GateReviewMapper;
import com.njydsz.pmis.project.mapper.InitiationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InitiationServiceImpl 立项服务测试")
class InitiationServiceImplTest {

    private InitiationMapper initiationMapper;
    private BudgetItemMapper budgetItemMapper;
    private GateReviewMapper gateReviewMapper;
    private NameAssembler nameAssembler;
    private WorkflowServiceClient workflowServiceClient;
    private InitiationServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        initiationMapper = mock(InitiationMapper.class);
        budgetItemMapper = mock(BudgetItemMapper.class);
        gateReviewMapper = mock(GateReviewMapper.class);
        nameAssembler = mock(NameAssembler.class);
        workflowServiceClient = mock(WorkflowServiceClient.class);
        service = new InitiationServiceImpl(initiationMapper, budgetItemMapper,
                gateReviewMapper, nameAssembler, workflowServiceClient);
    }

    @Test
    @DisplayName("创建立项 - 缺少必填抛 BAD_REQUEST")
    void createMissing() {
        InitiationCreateDTO dto = new InitiationCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建立项 - 编号重复抛 DUPLICATE_KEY")
    void createDuplicate() {
        when(initiationMapper.selectByCode("P-1")).thenReturn(new InitiationDO());
        InitiationCreateDTO dto = new InitiationCreateDTO();
        dto.setProjectCode("P-1");
        dto.setProjectName("proj");
        dto.setCustomerId(1L);
        dto.setProjectType("FIXED_PRICE");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("创建立项 - 结束日期早于开始日期抛错")
    void createInvalidDate() {
        InitiationCreateDTO dto = new InitiationCreateDTO();
        dto.setProjectCode("P-2");
        dto.setProjectName("proj");
        dto.setCustomerId(1L);
        dto.setProjectType("FIXED_PRICE");
        dto.setPlannedStartDate(LocalDate.of(2026, 6, 30));
        dto.setPlannedEndDate(LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建立项成功 - 计算 durationDays + 默认 stage")
    void createOk() {
        when(initiationMapper.selectByCode("P-3")).thenReturn(null);
        when(initiationMapper.insert(any(InitiationDO.class))).thenAnswer(inv -> {
            InitiationDO o = inv.getArgument(0);
            o.setId(99L);
            return 1;
        });
        InitiationCreateDTO dto = new InitiationCreateDTO();
        dto.setProjectCode("P-3");
        dto.setProjectName("金陵项目");
        dto.setCustomerId(1L);
        dto.setProjectType("FIXED_PRICE");
        dto.setPlannedStartDate(LocalDate.of(2026, 1, 1));
        dto.setPlannedEndDate(LocalDate.of(2026, 1, 31));
        dto.setCustomerName("南京客户");
        dto.setPmName("张PM");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(99L);

        ArgumentCaptor<InitiationDO> captor = ArgumentCaptor.forClass(InitiationDO.class);
        verify(initiationMapper).insert(captor.capture());
        InitiationDO saved = captor.getValue();
        assertThat(saved.getStage()).isEqualTo("PRE_INITIATION");
        assertThat(saved.getProjectLevel()).isEqualTo("C");
        assertThat(saved.getDurationDays()).isEqualTo(30);
        assertThat(saved.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("阶段迁移 - 非法跳级拒绝")
    void changeStageInvalid() {
        InitiationDO o = new InitiationDO();
        o.setId(1L);
        o.setStage("PRE_INITIATION");
        when(initiationMapper.selectById(1L)).thenReturn(o);
        InitiationStageDTO dto = new InitiationStageDTO();
        dto.setId(1L);
        dto.setTargetStage("APPROVED");
        assertThatThrownBy(() -> service.changeStage(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("阶段迁移 - PRE_INITIATION -> SUBMITTED 合法")
    void changeStageOk() {
        InitiationDO o = new InitiationDO();
        o.setId(1L);
        o.setStage("PRE_INITIATION");
        when(initiationMapper.selectById(1L)).thenReturn(o);
        InitiationStageDTO dto = new InitiationStageDTO();
        dto.setId(1L);
        dto.setTargetStage("SUBMITTED");
        service.changeStage(dto);
        verify(initiationMapper).updateStage(1L, "SUBMITTED", null);
    }

    @Test
    @DisplayName("门径评审 - CD1 通过应记录 next gate")
    void reviewGatePass() {
        InitiationDO o = new InitiationDO();
        o.setId(7L);
        o.setStage("APPROVING");
        when(initiationMapper.selectById(7L)).thenReturn(o);
        when(gateReviewMapper.selectByInitiationAndGate(7L, "CD1")).thenReturn(null);
        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(7L);
        dto.setGateCode("CD1");
        dto.setReviewResult("PASSED");
        dto.setDecisionBasis("材料齐全");
        service.reviewGate(dto);
        ArgumentCaptor<GateReviewDO> captor = ArgumentCaptor.forClass(GateReviewDO.class);
        verify(gateReviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getNextGate()).isEqualTo("CD2");
    }

    @Test
    @DisplayName("门径评审 - 非法结果抛错")
    void reviewGateInvalidResult() {
        InitiationDO o = new InitiationDO();
        o.setId(7L);
        o.setStage("APPROVING");
        when(initiationMapper.selectById(7L)).thenReturn(o);
        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(7L);
        dto.setGateCode("CD1");
        dto.setReviewResult("MAYBE");
        assertThatThrownBy(() -> service.reviewGate(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("门径评审 - CD5 通过无下个门径")
    void reviewGateLast() {
        InitiationDO o = new InitiationDO();
        o.setId(8L);
        o.setStage("EXECUTING");
        when(initiationMapper.selectById(8L)).thenReturn(o);
        when(gateReviewMapper.selectByInitiationAndGate(8L, "CD5")).thenReturn(null);
        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(8L);
        dto.setGateCode("CD5");
        dto.setReviewResult("PASSED");
        service.reviewGate(dto);
        ArgumentCaptor<GateReviewDO> captor = ArgumentCaptor.forClass(GateReviewDO.class);
        verify(gateReviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getNextGate()).isNull();
    }

    @Test
    @DisplayName("预算汇总 - 应按金额求和并落库")
    void recomputeBudget() {
        com.njydsz.pmis.project.entity.BudgetItemDO b1 = new com.njydsz.pmis.project.entity.BudgetItemDO();
        b1.setAmount(new BigDecimal("100.00"));
        com.njydsz.pmis.project.entity.BudgetItemDO b2 = new com.njydsz.pmis.project.entity.BudgetItemDO();
        b2.setAmount(new BigDecimal("250.50"));
        when(budgetItemMapper.selectByInitiationId(11L)).thenReturn(List.of(b1, b2));
        InitiationDO o = new InitiationDO();
        o.setId(11L);
        when(initiationMapper.selectById(11L)).thenReturn(o);
        BigDecimal total = service.recomputeBudget(11L);
        assertThat(total).isEqualByComparingTo("350.50");
        assertThat(o.getBudgetAmount()).isEqualByComparingTo("350.50");
        verify(initiationMapper).updateById(o);
    }

    @Test
    @DisplayName("启动审批流 - 成功写回 workflowId")
    void startProcessOk() {
        InitiationDO o = new InitiationDO();
        o.setId(20L);
        o.setProjectCode("P-20");
        o.setProjectName("流程项目");
        o.setStage("SUBMITTED");
        when(initiationMapper.selectById(20L)).thenReturn(o);
        when(workflowServiceClient.startProcess(anyMap())).thenReturn(R.ok("INST-100"));
        String id = service.startProcess(20L, 1L);
        assertThat(id).isEqualTo("INST-100");
        assertThat(o.getWorkflowId()).isEqualTo("INST-100");
    }

    @Test
    @DisplayName("启动审批流 - Feign 异常时返回 null")
    void startProcessFail() {
        InitiationDO o = new InitiationDO();
        o.setId(21L);
        o.setProjectCode("P-21");
        when(initiationMapper.selectById(21L)).thenReturn(o);
        when(workflowServiceClient.startProcess(anyMap())).thenThrow(new RuntimeException("down"));
        String id = service.startProcess(21L, 1L);
        assertThat(id).isNull();
    }

    @Test
    @DisplayName("启动审批流 - 已存在流程实例直接返回")
    void startProcessDuplicate() {
        InitiationDO o = new InitiationDO();
        o.setId(22L);
        o.setWorkflowId("EXIST-1");
        when(initiationMapper.selectById(22L)).thenReturn(o);
        String id = service.startProcess(22L, 1L);
        assertThat(id).isEqualTo("EXIST-1");
    }

    @Test
    @DisplayName("assembleNames - 已有名称不调用 Feign")
    void assembleNamesSkip() {
        InitiationDO o = new InitiationDO();
        o.setCustomerId(1L);
        o.setCustomerName("已有客户");
        o.setPmId(2L);
        o.setPmName("已有PM");
        service.assembleNames(o);
        org.mockito.Mockito.verify(nameAssembler, org.mockito.Mockito.never()).resolveCustomer(any());
        org.mockito.Mockito.verify(nameAssembler, org.mockito.Mockito.never()).resolveEmployee(any());
    }

    @Test
    @DisplayName("assembleNames - 缺名称时调用 Feign 补齐")
    void assembleNamesFill() {
        InitiationDO o = new InitiationDO();
        o.setCustomerId(1L);
        o.setPmId(2L);
        when(nameAssembler.resolveCustomer(1L)).thenReturn("客户X");
        when(nameAssembler.resolveEmployee(2L)).thenReturn("员工Y");
        service.assembleNames(o);
        assertThat(o.getCustomerName()).isEqualTo("客户X");
        assertThat(o.getPmName()).isEqualTo("员工Y");
    }

    @Test
    @DisplayName("分页 - 对结果集调用 assembleNames")
    void pageAssemble() {
        InitiationDO o = new InitiationDO();
        o.setId(1L);
        o.setCustomerId(10L);
        Page<InitiationDO> p = new Page<>();
        p.setRecords(List.of(o));
        when(initiationMapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<InitiationDO> r = service.page(1, 10, null, null, null, null);
        assertThat(r.getRecords()).hasSize(1);
        verify(nameAssembler).resolveCustomer(10L);
    }
}
