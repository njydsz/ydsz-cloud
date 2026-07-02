package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.assembler.NameAssembler;
import com.njydsz.pmis.execution.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.execution.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.execution.entity.WbsTaskDO;
import com.njydsz.pmis.execution.mapper.WbsTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WbsTaskServiceImpl WBS 任务服务测试")
class WbsTaskServiceImplTest {

    private WbsTaskMapper mapper;
    private NameAssembler nameAssembler;
    private WbsTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(WbsTaskMapper.class);
        nameAssembler = mock(NameAssembler.class);
        service = new WbsTaskServiceImpl(mapper, nameAssembler);
    }

    @Test
    @DisplayName("create - 缺少必填抛错")
    void createMissing() {
        WbsTaskCreateDTO dto = new WbsTaskCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create - 编号重复抛错")
    void createDuplicate() {
        when(mapper.selectByCode("T-1")).thenReturn(new WbsTaskDO());
        WbsTaskCreateDTO dto = new WbsTaskCreateDTO();
        dto.setTaskCode("T-1");
        dto.setTaskName("task");
        dto.setInitiationId(1L);
        dto.setOwnerId(2L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create - 计划日期非法")
    void createInvalidDate() {
        WbsTaskCreateDTO dto = new WbsTaskCreateDTO();
        dto.setTaskCode("T-2");
        dto.setTaskName("task");
        dto.setInitiationId(1L);
        dto.setOwnerId(2L);
        dto.setPlannedStartDate(LocalDate.of(2026, 6, 30));
        dto.setPlannedEndDate(LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 成功 - 计算工期 + WBS路径")
    void createOk() {
        when(mapper.selectByCode("T-3")).thenReturn(null);
        when(mapper.selectById(anyLong())).thenReturn(null);
        when(mapper.insert(any(WbsTaskDO.class))).thenAnswer(inv -> {
            WbsTaskDO t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });
        WbsTaskCreateDTO dto = new WbsTaskCreateDTO();
        dto.setTaskCode("T-3");
        dto.setTaskName("需求调研");
        dto.setInitiationId(1L);
        dto.setOwnerId(2L);
        dto.setPlannedStartDate(LocalDate.of(2026, 1, 1));
        dto.setPlannedEndDate(LocalDate.of(2026, 1, 16));
        dto.setPlannedEffort(new BigDecimal("15"));
        dto.setOwnerName("张三");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(99L);

        ArgumentCaptor<WbsTaskDO> captor = ArgumentCaptor.forClass(WbsTaskDO.class);
        verify(mapper).insert(captor.capture());
        WbsTaskDO saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
        assertThat(saved.getPriority()).isEqualTo("NORMAL");
        assertThat(saved.getTaskLevel()).isEqualTo(1);
        assertThat(saved.getDurationDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("changeStatus - 非法迁移拒绝")
    void changeStatusInvalid() {
        WbsTaskDO t = new WbsTaskDO();
        t.setId(1L);
        t.setStatus("PLANNED");
        when(mapper.selectById(1L)).thenReturn(t);
        WbsTaskStatusDTO dto = new WbsTaskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("COMPLETED");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("changeStatus - 启动后写入 actualStartDate")
    void changeStatusToInProgress() {
        WbsTaskDO t = new WbsTaskDO();
        t.setId(1L);
        t.setStatus("PLANNED");
        when(mapper.selectById(1L)).thenReturn(t);
        WbsTaskStatusDTO dto = new WbsTaskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("IN_PROGRESS");
        service.changeStatus(dto);
        verify(mapper).updateStatus(1L, "IN_PROGRESS");
        assertThat(t.getActualStartDate()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus - 完成时自动 100% 进度")
    void changeStatusToCompleted() {
        WbsTaskDO t = new WbsTaskDO();
        t.setId(1L);
        t.setStatus("IN_REVIEW");
        when(mapper.selectById(1L)).thenReturn(t);
        WbsTaskStatusDTO dto = new WbsTaskStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("COMPLETED");
        service.changeStatus(dto);
        verify(mapper).updateProgress(eq1(1L), eq2(new BigDecimal("100")), any());
        verify(mapper).updateStatus(1L, "COMPLETED");
        assertThat(t.getActualEndDate()).isNotNull();
    }

    private static Long eq1(Long v) { return org.mockito.ArgumentMatchers.eq(v); }
    private static <T> T eq2(T v) { return org.mockito.ArgumentMatchers.eq(v); }

    @Test
    @DisplayName("updateProgress - 越界拒绝")
    void updateProgressInvalid() {
        WbsTaskDO t = new WbsTaskDO();
        t.setId(1L);
        t.setStatus("IN_PROGRESS");
        when(mapper.selectById(1L)).thenReturn(t);
        assertThatThrownBy(() -> service.updateProgress(1L, new BigDecimal("150"), null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete - 进行中拒绝")
    void deleteInProgress() {
        WbsTaskDO t = new WbsTaskDO();
        t.setId(1L);
        t.setStatus("IN_PROGRESS");
        when(mapper.selectById(1L)).thenReturn(t);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("calcOverallProgress - 工时加权")
    void overallProgress() {
        WbsTaskDO t1 = new WbsTaskDO();
        t1.setPlannedEffort(new BigDecimal("10"));
        t1.setProgressPct(new BigDecimal("50"));
        WbsTaskDO t2 = new WbsTaskDO();
        t2.setPlannedEffort(new BigDecimal("20"));
        t2.setProgressPct(new BigDecimal("100"));
        when(mapper.selectByInitiation(1L)).thenReturn(List.of(t1, t2));
        BigDecimal r = service.calcOverallProgress(1L);
        // (10*50 + 20*100) / (10+20) = 2500/30 = 83.33
        assertThat(r).isEqualByComparingTo("83.33");
    }

    @Test
    @DisplayName("getById - 不存在抛 NOT_FOUND")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }
}
