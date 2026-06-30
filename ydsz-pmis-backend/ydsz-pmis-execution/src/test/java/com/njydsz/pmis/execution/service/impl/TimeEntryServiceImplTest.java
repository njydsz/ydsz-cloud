package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.assembler.NameAssembler;
import com.njydsz.pmis.execution.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.execution.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.execution.entity.TimeEntryDO;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import com.njydsz.pmis.execution.service.CostAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TimeEntryServiceImpl 工时服务测试")
class TimeEntryServiceImplTest {

    private TimeEntryMapper mapper;
    private NameAssembler nameAssembler;
    private CostAllocationService costAllocationService;
    private TimeEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TimeEntryMapper.class);
        nameAssembler = mock(NameAssembler.class);
        costAllocationService = mock(CostAllocationService.class);
        service = new TimeEntryServiceImpl(mapper, nameAssembler, costAllocationService);
    }

    @Test
    @DisplayName("create - 缺少员工抛错")
    void createMissing() {
        TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 24h超限拒绝")
    void createOverLimit() {
        TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
        dto.setEntryDate(LocalDate.now());
        dto.setEmployeeId(1L);
        dto.setInitiationId(1L);
        dto.setHours(new BigDecimal("30"));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 成功 - 折算人天")
    void createOk() {
        when(mapper.insert(any(TimeEntryDO.class))).thenAnswer(inv -> {
            TimeEntryDO e = inv.getArgument(0);
            e.setId(50L);
            return 1;
        });
        TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
        dto.setEntryDate(LocalDate.of(2026, 6, 30));
        dto.setEmployeeId(1L);
        dto.setEmployeeName("张三");
        dto.setInitiationId(1L);
        dto.setHours(new BigDecimal("8"));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(50L);

        ArgumentCaptor<TimeEntryDO> captor = ArgumentCaptor.forClass(TimeEntryDO.class);
        verify(mapper).insert(captor.capture());
        TimeEntryDO saved = captor.getValue();
        assertThat(saved.getDays()).isEqualByComparingTo("1.00");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("approve - 通过触发成本归集")
    void approveTriggersCostAllocation() {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(1L);
        e.setStatus("SUBMITTED");
        e.setHours(new BigDecimal("8"));
        e.setDays(new BigDecimal("1.00"));
        e.setEntryDate(LocalDate.of(2026, 6, 30));
        e.setEmployeeId(2L);
        e.setEmployeeName("张三");
        e.setInitiationId(10L);
        e.setLevelCode("L5");
        when(mapper.selectById(1L)).thenReturn(e);
        when(costAllocationService.syncFromTimeEntry(any(), any(), any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(99L);
        TimeEntryApprovalDTO dto = new TimeEntryApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("APPROVED");
        dto.setApproverId(3L);
        dto.setApproverName("PM");
        service.approve(dto);
        verify(costAllocationService).syncFromTimeEntry(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("张三"),
                org.mockito.ArgumentMatchers.eq("L5"),
                org.mockito.ArgumentMatchers.eq("2026-06"),
                org.mockito.ArgumentMatchers.any(BigDecimal.class),
                org.mockito.ArgumentMatchers.eq(true)
        );
    }

    @Test
    @DisplayName("approve - 驳回需原因")
    void approveRejectedNeedReason() {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(1L);
        e.setStatus("SUBMITTED");
        when(mapper.selectById(1L)).thenReturn(e);
        TimeEntryApprovalDTO dto = new TimeEntryApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("REJECTED");
        assertThatThrownBy(() -> service.approve(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete - 已批准不能删")
    void deleteApproved() {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(1L);
        e.setStatus("APPROVED");
        when(mapper.selectById(1L)).thenReturn(e);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }
}
