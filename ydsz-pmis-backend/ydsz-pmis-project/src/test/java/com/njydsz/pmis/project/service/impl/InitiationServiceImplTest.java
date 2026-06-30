package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.BudgetItemDTO;
import com.njydsz.pmis.project.dto.GateReviewDTO;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.BudgetItemDO;
import com.njydsz.pmis.project.entity.GateReviewDO;
import com.njydsz.pmis.project.entity.InitiationDO;
import com.njydsz.pmis.project.enums.GateCode;
import com.njydsz.pmis.project.enums.InitiationStage;
import com.njydsz.pmis.project.feign.WorkflowServiceClient;
import com.njydsz.pmis.project.mapper.BudgetItemMapper;
import com.njydsz.pmis.project.mapper.GateReviewMapper;
import com.njydsz.pmis.project.mapper.InitiationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * InitiationServiceImpl 单元测试
 */
@DisplayName("InitiationServiceImpl 立项服务测试")
class InitiationServiceImplTest {

    private InitiationMapper initiationMapper;
    private BudgetItemMapper budgetItemMapper;
    private GateReviewMapper gateReviewMapper;
    private NameAssembler nameAssembler;
    private WorkflowServiceClient workflowServiceClient;
    private InitiationServiceImpl service;

    @BeforeEach
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
    @DisplayName("create 项目编号重复应抛 DUPLICATE_KEY")
    void create_duplicateCode() {
        InitiationDO exist = new InitiationDO();
        when(initiationMapper.selectByCode("P001")).thenReturn(exist);

        InitiationCreateDTO dto = validDto();
        dto.setProjectCode("P001");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 编号为空应抛 BAD_REQUEST")
    void create_blankCode() {
        InitiationCreateDTO dto = validDto();
        dto.setProjectCode(" ");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 名称为空应抛 BAD_REQUEST")
    void create_blankName() {
        InitiationCreateDTO dto = validDto();
        dto.setProjectName(" ");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 客户 ID 为空应抛 BAD_REQUEST")
    void create_nullCustomer() {
        InitiationCreateDTO dto = validDto();
        dto.setCustomerId(null);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 结束日期早于开始日期应抛 BAD_REQUEST")
    void create_invalidDateRange() {
        InitiationCreateDTO dto = validDto();
        dto.setPlannedStartDate(LocalDate.of(2026, 6, 1));
        dto.setPlannedEndDate(LocalDate.of(2026, 5, 1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 正常应返回 id 并填充默认值")
    void create_ok() {
        when(initiationMapper.selectByCode("P001")).thenReturn(null);
        when(initiationMapper.insert(any(InitiationDO.class))).thenAnswer(inv -> {
            InitiationDO arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });

        InitiationCreateDTO dto = validDto();
        dto.setPlannedStartDate(LocalDate.of(2026, 1, 1));
        dto.setPlannedEndDate(LocalDate.of(2026, 4, 1));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(100L);
    }

    @Test
    @DisplayName("changeStage 阶段非法应抛 BAD_REQUEST")
    void changeStage_invalid() {
        InitiationDO exist = new InitiationDO();
        exist.setId(1L);
        exist.setStage(InitiationStage.PRE_INITIATION.getCode());
        when(initiationMapper.selectById(1L)).thenReturn(exist);

        InitiationStageDTO dto = new InitiationStageDTO();
        dto.setId(1L);
        dto.setTargetStage(InitiationStage.EXECUTING.getCode());

        assertThatThrownBy(() -> service.changeStage(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("changeStage PRE_INITIATION -> SUBMITTED 合法")
    void changeStage_preToSubmit() {
        InitiationDO exist = new InitiationDO();
        exist.setId(1L);
        exist.setStage(InitiationStage.PRE_INITIATION.getCode());
        when(initiationMapper.selectById(1L)).thenReturn(exist);

        InitiationStageDTO dto = new InitiationStageDTO();
        dto.setId(1L);
        dto.setTargetStage(InitiationStage.SUBMITTED.getCode());

        service.changeStage(dto);
    }

    @Test
    @DisplayName("getById 不存在应抛 NOT_FOUND")
    void getById_notFound() {
        when(initiationMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 不存在应抛 NOT_FOUND")
    void delete_notFound() {
        when(initiationMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("addBudgetItem 分类非法应抛 BAD_REQUEST")
    void addBudgetItem_invalidCategory() {
        InitiationCreateDTO iDto = validDto();
        when(initiationMapper.selectById(any())).thenReturn(new InitiationDO());

        BudgetItemDTO b = new BudgetItemDTO();
        b.setInitiationId(1L);
        b.setCategory("INVALID");
        b.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> service.addBudgetItem(b))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("addBudgetItem 立项不存在应抛 NOT_FOUND")
    void addBudgetItem_initiationNotFound() {
        when(initiationMapper.selectById(99L)).thenReturn(null);

        BudgetItemDTO b = new BudgetItemDTO();
        b.setInitiationId(99L);
        b.setCategory("LABOR");
        b.setAmount(new BigDecimal("1000"));

        assertThatThrownBy(() -> service.addBudgetItem(b))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("reviewGate 编码非法应抛 BAD_REQUEST")
    void reviewGate_invalidCode() {
        when(initiationMapper.selectById(any())).thenReturn(new InitiationDO());

        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(1L);
        dto.setGateCode("INVALID");
        dto.setReviewResult("PASSED");

        assertThatThrownBy(() -> service.reviewGate(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("reviewGate 结果非法应抛 BAD_REQUEST")
    void reviewGate_invalidResult() {
        when(initiationMapper.selectById(any())).thenReturn(new InitiationDO());

        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(1L);
        dto.setGateCode(GateCode.CD1.name());
        dto.setReviewResult("INVALID");

        assertThatThrownBy(() -> service.reviewGate(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("reviewGate PASSED 正常")
    void reviewGate_passed() {
        InitiationDO exist = new InitiationDO();
        exist.setId(1L);
        when(initiationMapper.selectById(1L)).thenReturn(exist);
        when(gateReviewMapper.selectByInitiationAndGate(any(), any())).thenReturn(null);
        when(gateReviewMapper.insert(any(GateReviewDO.class))).thenAnswer(inv -> {
            GateReviewDO arg = inv.getArgument(0);
            arg.setId(200L);
            return 1;
        });

        GateReviewDTO dto = new GateReviewDTO();
        dto.setInitiationId(1L);
        dto.setGateCode(GateCode.CD1.name());
        dto.setReviewResult("PASSED");
        dto.setDecisionBasis("OK");

        Long id = service.reviewGate(dto);
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("startProcess 已有 workflow 应直接返回")
    void startProcess_existing() {
        InitiationDO exist = new InitiationDO();
        exist.setId(1L);
        exist.setWorkflowId("W-EXIST");
        when(initiationMapper.selectById(1L)).thenReturn(exist);

        String result = service.startProcess(1L, 100L);
        assertThat(result).isEqualTo("W-EXIST");
    }

    @Test
    @DisplayName("startProcess Feign 失败应返回 null 不抛异常")
    void startProcess_feignFailure() {
        InitiationDO exist = new InitiationDO();
        exist.setId(1L);
        when(initiationMapper.selectById(1L)).thenReturn(exist);
        when(workflowServiceClient.startProcess(any())).thenThrow(new RuntimeException("feign down"));

        String result = service.startProcess(1L, 100L);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("listGateReviews initiationId 为空应返回空列表")
    void listGateReviews_null() {
        assertThat(service.listGateReviews(null)).isEmpty();
    }

    @Test
    @DisplayName("aggregateByStage tenantId 为空应使用默认 1L")
    void aggregateByStage_defaultTenant() {
        when(initiationMapper.aggregateByStage(1L)).thenReturn(List.of());
        assertThat(service.aggregateByStage(null)).isEmpty();
    }

    @Test
    @DisplayName("assembleNames 空对象应安全返回")
    void assembleNames_null() {
        service.assembleNames(null);
    }

    private InitiationCreateDTO validDto() {
        InitiationCreateDTO dto = new InitiationCreateDTO();
        dto.setProjectCode("P001");
        dto.setProjectName("项目1");
        dto.setCustomerId(10L);
        dto.setProjectType("FIXED_PRICE");
        return dto;
    }
}
