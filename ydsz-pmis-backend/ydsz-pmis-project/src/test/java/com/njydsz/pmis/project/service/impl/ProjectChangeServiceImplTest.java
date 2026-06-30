package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.entity.ProjectChangeDO;
import com.njydsz.pmis.project.enums.ChangeStatus;
import com.njydsz.pmis.project.enums.ChangeType;
import com.njydsz.pmis.project.enums.RiskLevel;
import com.njydsz.pmis.project.mapper.ProjectChangeMapper;
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

/**
 * ProjectChangeServiceImpl 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ProjectChangeServiceImpl 项目变更服务")
class ProjectChangeServiceImplTest {

    private ProjectChangeMapper mapper;
    private ProjectChangeServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProjectChangeMapper.class);
        service = new ProjectChangeServiceImpl(mapper);
    }

    @Test
    @DisplayName("创建-空请求")
    void createNull() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-类型不合法")
    void createBadType() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setChangeCode("C-1");
        dto.setInitiationId(1L);
        dto.setChangeType("XXX");
        dto.setChangeTitle("t");
        dto.setApplicantId(1L);
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-申请人缺失")
    void createNoApplicant() {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setChangeCode("C-1");
        dto.setInitiationId(1L);
        dto.setChangeType(ChangeType.SCOPE.getCode());
        dto.setChangeTitle("t");
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-编号重复")
    void createDuplicate() {
        when(mapper.selectByCode("C-1")).thenReturn(new ProjectChangeDO());
        ProjectChangeCreateDTO dto = valid("C-1");
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-成功")
    void createSuccess() {
        when(mapper.selectByCode("C-1")).thenReturn(null);
        when(mapper.insert(any(ProjectChangeDO.class))).thenAnswer(inv -> {
            ProjectChangeDO d = inv.getArgument(0);
            d.setId(10L);
            return 1;
        });
        Long id = service.create(valid("C-1"));
        assertThat(id).isEqualTo(10L);
        ArgumentCaptor<ProjectChangeDO> capt = ArgumentCaptor.forClass(ProjectChangeDO.class);
        verify(mapper).insert(capt.capture());
        assertThat(capt.getValue().getStatus()).isEqualTo(ChangeStatus.DRAFT.getCode());
    }

    @Test
    @DisplayName("创建-自动评估-重大变更")
    void createAutoEvaluateMajor() {
        when(mapper.selectByCode("C-2")).thenReturn(null);
        when(mapper.insert(any(ProjectChangeDO.class))).thenAnswer(inv -> {
            ProjectChangeDO d = inv.getArgument(0);
            d.setId(11L);
            return 1;
        });
        ProjectChangeCreateDTO dto = valid("C-2");
        dto.setChangeType(ChangeType.CONTRACT.getCode());
        dto.setBudgetImpact(new BigDecimal("800000"));
        dto.setContractImpact(new BigDecimal("1500000"));
        dto.setScheduleImpactDays(45);
        service.create(dto);
        ArgumentCaptor<ProjectChangeDO> capt = ArgumentCaptor.forClass(ProjectChangeDO.class);
        verify(mapper).insert(capt.capture());
        assertThat(capt.getValue().getMajorFlag()).isEqualTo(1);
        assertThat(capt.getValue().getRiskLevelAfter()).isEqualTo(RiskLevel.HIGH.getCode());
    }

    @Test
    @DisplayName("状态迁移-DRAFT->SUBMITTED")
    void changeStatusDraft2Submitted() {
        when(mapper.selectById(1L)).thenReturn(change(1L, "DRAFT"));
        when(mapper.updateStatus(1L, "SUBMITTED")).thenReturn(1);
        ProjectChangeStatusDTO dto = new ProjectChangeStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeStatus(dto);
        verify(mapper).updateStatus(1L, "SUBMITTED");
    }

    @Test
    @DisplayName("状态迁移-不允许的迁移")
    void changeStatusInvalid() {
        when(mapper.selectById(1L)).thenReturn(change(1L, "DRAFT"));
        ProjectChangeStatusDTO dto = new ProjectChangeStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("APPROVED");
        assertThatThrownBy(() -> service.changeStatus(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-已执行 拒绝")
    void deleteExecuted() {
        when(mapper.selectById(1L)).thenReturn(change(1L, "EXECUTED"));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-DRAFT 成功")
    void deleteDraft() {
        when(mapper.selectById(1L)).thenReturn(change(1L, "DRAFT"));
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("按项目查询")
    void listByInitiation() {
        when(mapper.selectByInitiation(1L)).thenReturn(java.util.List.of(change(1L, "DRAFT")));
        assertThat(service.listByInitiation(1L)).hasSize(1);
        assertThat(service.listByInitiation(null)).isEmpty();
    }

    @Test
    @DisplayName("按类型统计")
    void aggregateByType() {
        when(mapper.aggregateByType(1L)).thenReturn(java.util.List.of(java.util.Map.of("type", "SCOPE", "count", 2)));
        assertThat(service.aggregateByType(1L)).hasSize(1);
        assertThat(service.aggregateByType(null)).isNotNull();
    }

    @Test
    @DisplayName("按状态统计")
    void aggregateByStatus() {
        when(mapper.aggregateByStatus(1L)).thenReturn(java.util.List.of());
        assertThat(service.aggregateByStatus(1L)).isEmpty();
    }

    @Test
    @DisplayName("按项目统计重大变更数")
    void countMajor() {
        when(mapper.countMajorByInitiation(1L)).thenReturn(2L);
        assertThat(service.countMajorByInitiation(1L)).isEqualTo(2L);
        assertThat(service.countMajorByInitiation(null)).isEqualTo(0L);
    }

    @Test
    @DisplayName("getById-不存在")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("分页查询")
    void page() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        assertThat(service.page(1, 10, null, "SCOPE", "DRAFT", 1L)).isNotNull();
    }

    private ProjectChangeCreateDTO valid(String code) {
        ProjectChangeCreateDTO dto = new ProjectChangeCreateDTO();
        dto.setChangeCode(code);
        dto.setInitiationId(1L);
        dto.setChangeType(ChangeType.SCOPE.getCode());
        dto.setChangeTitle("t");
        dto.setApplicantId(1L);
        return dto;
    }

    private ProjectChangeDO change(Long id, String status) {
        ProjectChangeDO c = new ProjectChangeDO();
        c.setId(id);
        c.setChangeCode("C-" + id);
        c.setChangeType(ChangeType.SCOPE.getCode());
        c.setInitiationId(1L);
        c.setStatus(status);
        c.setApplicantId(1L);
        return c;
    }
}
