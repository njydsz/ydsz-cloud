package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ProjectClosureCreateDTO;
import com.njydsz.pmis.execution.dto.ProjectClosureStatusDTO;
import com.njydsz.pmis.execution.entity.ProjectClosureDO;
import com.njydsz.pmis.execution.enums.ClosureStatus;
import com.njydsz.pmis.execution.enums.ClosureType;
import com.njydsz.pmis.execution.mapper.ProjectClosureMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectClosureServiceImpl 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ProjectClosureServiceImpl 项目结项")
class ProjectClosureServiceImplTest {

    private ProjectClosureMapper mapper;
    private ProjectClosureServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProjectClosureMapper.class);
        service = new ProjectClosureServiceImpl(mapper);
    }

    @Test
    @DisplayName("创建-空请求")
    void createNull() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-类型不合法")
    void createBadType() {
        ProjectClosureCreateDTO dto = new ProjectClosureCreateDTO();
        dto.setClosureCode("CL-1");
        dto.setInitiationId(1L);
        dto.setClosureType("XXX");
        dto.setApplicantId(1L);
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-质保期负数")
    void createNegativeWarranty() {
        ProjectClosureCreateDTO dto = new ProjectClosureCreateDTO();
        dto.setClosureCode("CL-1");
        dto.setInitiationId(1L);
        dto.setClosureType(ClosureType.FORMAL.getCode());
        dto.setApplicantId(1L);
        dto.setWarrantyMonths(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-编号重复")
    void createDuplicate() {
        when(mapper.selectByCode("CL-1")).thenReturn(new ProjectClosureDO());
        ProjectClosureCreateDTO dto = valid("CL-1");
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建-成功 回款比例自动计算")
    void createSuccess() {
        when(mapper.selectByCode("CL-1")).thenReturn(null);
        when(mapper.insert(any(ProjectClosureDO.class))).thenAnswer(inv -> {
            ProjectClosureDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        ProjectClosureCreateDTO dto = valid("CL-1");
        dto.setContractAmount(new BigDecimal("1000000"));
        dto.setReceivedAmount(new BigDecimal("800000"));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<ProjectClosureDO> capt = ArgumentCaptor.forClass(ProjectClosureDO.class);
        verify(mapper).insert(capt.capture());
        assertThat(capt.getValue().getReceivedRatio()).isEqualByComparingTo("0.8000");
        assertThat(capt.getValue().getStatus()).isEqualTo(ClosureStatus.DRAFT.getCode());
    }

    @Test
    @DisplayName("创建-合同为0 防止除零")
    void createZeroContract() {
        when(mapper.selectByCode("CL-2")).thenReturn(null);
        when(mapper.insert(any(ProjectClosureDO.class))).thenAnswer(inv -> {
            ProjectClosureDO d = inv.getArgument(0);
            d.setId(2L);
            return 1;
        });
        ProjectClosureCreateDTO dto = valid("CL-2");
        dto.setContractAmount(BigDecimal.ZERO);
        dto.setReceivedAmount(new BigDecimal("100"));
        service.create(dto);
        ArgumentCaptor<ProjectClosureDO> capt = ArgumentCaptor.forClass(ProjectClosureDO.class);
        verify(mapper).insert(capt.capture());
        assertThat(capt.getValue().getReceivedRatio()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("状态迁移-DRAFT->SUBMITTED")
    void changeStatusDraft2Submitted() {
        when(mapper.selectById(1L)).thenReturn(closure(1L, "DRAFT"));
        ProjectClosureStatusDTO dto = new ProjectClosureStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeStatus(dto);
        verify(mapper).updateById(any(ProjectClosureDO.class));
    }

    @Test
    @DisplayName("状态迁移-ARCHIVED 锁定")
    void changeStatusArchived() {
        when(mapper.selectById(1L)).thenReturn(closure(1L, "APPROVED"));
        when(mapper.updateLocked(1L, 1)).thenReturn(1);
        ProjectClosureStatusDTO dto = new ProjectClosureStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("ARCHIVED");
        service.changeStatus(dto);
        verify(mapper).updateLocked(1L, 1);
    }

    @Test
    @DisplayName("状态迁移-非法目标")
    void changeStatusBadTarget() {
        when(mapper.selectById(1L)).thenReturn(closure(1L, "DRAFT"));
        ProjectClosureStatusDTO dto = new ProjectClosureStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("XXX");
        assertThatThrownBy(() -> service.changeStatus(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-已归档 拒绝")
    void deleteArchived() {
        when(mapper.selectById(1L)).thenReturn(closure(1L, "ARCHIVED"));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-锁定 拒绝")
    void deleteLocked() {
        ProjectClosureDO c = closure(1L, "DRAFT");
        c.setLocked(1);
        when(mapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("getByInitiation-空")
    void getByInitiationNull() {
        assertThat(service.getByInitiation(null)).isNull();
    }

    @Test
    @DisplayName("checkAdmission-存在")
    void checkAdmission() {
        when(mapper.selectById(1L)).thenReturn(closure(1L, "DRAFT"));
        assertThat(service.checkAdmission(1L)).isNotNull();
    }

    @Test
    @DisplayName("page")
    void page() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        assertThat(service.page(1, 10, null, "FORMAL", "DRAFT")).isNotNull();
    }

    @Test
    @DisplayName("listByType")
    void listByType() {
        when(mapper.selectByType("FORMAL")).thenReturn(List.of(closure(1L, "DRAFT")));
        assertThat(service.listByType("FORMAL")).hasSize(1);
    }

    @Test
    @DisplayName("aggregateByType")
    void aggregateByType() {
        when(mapper.aggregateByType(1L)).thenReturn(List.of());
        assertThat(service.aggregateByType(1L)).isEmpty();
    }

    private ProjectClosureCreateDTO valid(String code) {
        ProjectClosureCreateDTO dto = new ProjectClosureCreateDTO();
        dto.setClosureCode(code);
        dto.setInitiationId(1L);
        dto.setClosureType(ClosureType.FORMAL.getCode());
        dto.setApplicantId(1L);
        return dto;
    }

    private ProjectClosureDO closure(Long id, String status) {
        ProjectClosureDO c = new ProjectClosureDO();
        c.setId(id);
        c.setClosureCode("CL-" + id);
        c.setInitiationId(1L);
        c.setClosureType(ClosureType.FORMAL.getCode());
        c.setStatus(status);
        c.setApplicantId(1L);
        return c;
    }
}
