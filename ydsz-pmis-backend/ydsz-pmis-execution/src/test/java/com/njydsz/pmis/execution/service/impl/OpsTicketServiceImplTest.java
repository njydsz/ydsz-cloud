package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.OpsTicketAssignDTO;
import com.njydsz.pmis.execution.dto.OpsTicketCreateDTO;
import com.njydsz.pmis.execution.dto.OpsTicketStatusDTO;
import com.njydsz.pmis.execution.entity.OpsTicketDO;
import com.njydsz.pmis.execution.enums.OpsTicketStatus;
import com.njydsz.pmis.execution.mapper.OpsTicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpsTicketServiceImpl 运维工单测试
 */
@DisplayName("OpsTicketServiceImpl 运维工单")
class OpsTicketServiceImplTest {

    private OpsTicketMapper mapper;
    private OpsTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(OpsTicketMapper.class);
        service = new OpsTicketServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 必填校验")
    void create_null() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 项目 ID 必填")
    void create_noInitiation() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 标题必填")
    void create_noTitle() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        dto.setInitiationId(1L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 非法优先级")
    void create_badPriority() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        dto.setInitiationId(1L);
        dto.setTitle("X");
        dto.setPriority("P9");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 非法类别")
    void create_badCategory() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        dto.setInitiationId(1L);
        dto.setTitle("X");
        dto.setPriority("P3");
        dto.setCategory("XXX");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功：P1 自动设置 15 分钟响应 SLA")
    void create_p1_success() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        dto.setInitiationId(1L);
        dto.setTitle("服务异常");
        dto.setPriority("P1");
        dto.setCategory("BUG");
        when(mapper.insert(any(OpsTicketDO.class))).thenAnswer(inv -> {
            OpsTicketDO t = inv.getArgument(0);
            t.setId(50L);
            return 1;
        });

        Long id = service.create(dto);
        assertThat(id).isEqualTo(50L);
        ArgumentCaptor<OpsTicketDO> cap = ArgumentCaptor.forClass(OpsTicketDO.class);
        verify(mapper).insert(cap.capture());
        OpsTicketDO saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(OpsTicketStatus.OPEN.getCode());
        assertThat(saved.getResponseBreached()).isFalse();
        assertThat(saved.getResolveBreached()).isFalse();
        assertThat(saved.getResponseDueAt()).isNotNull();
        assertThat(saved.getResolveDueAt()).isNotNull();
        // P1: 15 分钟响应
        long respMin = java.time.temporal.ChronoUnit.MINUTES.between(
                LocalDateTime.now(), saved.getResponseDueAt());
        assertThat(respMin).isBetween(14L, 16L);
        assertThat(saved.getTicketCode()).startsWith("TK-");
    }

    @Test
    @DisplayName("create 默认 category = OTHER")
    void create_defaultCategory() {
        OpsTicketCreateDTO dto = new OpsTicketCreateDTO();
        dto.setInitiationId(1L);
        dto.setTitle("X");
        dto.setPriority("P3");
        when(mapper.insert(any(OpsTicketDO.class))).thenReturn(1);
        service.create(dto);
        ArgumentCaptor<OpsTicketDO> cap = ArgumentCaptor.forClass(OpsTicketDO.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getCategory()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("assign 必填校验")
    void assign_null() {
        assertThatThrownBy(() -> service.assign(null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.assign(new OpsTicketAssignDTO()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("assign 工单不存在")
    void assign_notFound() {
        OpsTicketAssignDTO dto = new OpsTicketAssignDTO();
        dto.setId(99L);
        dto.setAssigneeId(2L);
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.assign(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("assign 非 OPEN 状态不允许派单")
    void assign_badStatus() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketAssignDTO dto = new OpsTicketAssignDTO();
        dto.setId(1L);
        dto.setAssigneeId(2L);
        assertThatThrownBy(() -> service.assign(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("assign 成功：OPEN → ASSIGNED")
    void assign_success() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.OPEN.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketAssignDTO dto = new OpsTicketAssignDTO();
        dto.setId(1L);
        dto.setAssigneeId(2L);
        dto.setAssigneeName("张三");
        service.assign(dto);
        verify(mapper, times(1)).updateAssignee(eq(1L), eq(2L), eq("张三"),
                eq(OpsTicketStatus.ASSIGNED.getCode()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("changeStatus 必填校验")
    void changeStatus_null() {
        assertThatThrownBy(() -> service.changeStatus(null))
                .isInstanceOf(BizException.class);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("changeStatus 非法目标状态")
    void changeStatus_badTarget() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.OPEN.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("XXX");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("changeStatus 合法迁移 + 时点回填 RESOLVED")
    void changeStatus_resolved() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.IN_PROGRESS.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        when(mapper.selectById(1L)).thenReturn(t, t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(OpsTicketStatus.RESOLVED.getCode());
        dto.setResolutionNote("已修复");
        service.changeStatus(dto);
        verify(mapper, times(1)).updateStatus(1L, OpsTicketStatus.RESOLVED.getCode());
        verify(mapper, times(1)).updateById(any(OpsTicketDO.class));
    }

    @Test
    @DisplayName("closeAndEvaluate 非 RESOLVED 拒绝")
    void closeAndEvaluate_wrongStatus() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.IN_PROGRESS.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(OpsTicketStatus.CLOSED.getCode());
        dto.setCustomerScore(5);
        assertThatThrownBy(() -> service.closeAndEvaluate(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("closeAndEvaluate 缺少评分拒绝")
    void closeAndEvaluate_noScore() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(OpsTicketStatus.CLOSED.getCode());
        assertThatThrownBy(() -> service.closeAndEvaluate(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("closeAndEvaluate 评分非法范围拒绝")
    void closeAndEvaluate_badScore() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        when(mapper.selectById(1L)).thenReturn(t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(OpsTicketStatus.CLOSED.getCode());
        dto.setCustomerScore(6);
        assertThatThrownBy(() -> service.closeAndEvaluate(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("closeAndEvaluate 成功")
    void closeAndEvaluate_success() {
        OpsTicketDO t = new OpsTicketDO();
        t.setStatus(OpsTicketStatus.RESOLVED.getCode());
        when(mapper.selectById(1L)).thenReturn(t, t);
        OpsTicketStatusDTO dto = new OpsTicketStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(OpsTicketStatus.CLOSED.getCode());
        dto.setCustomerScore(5);
        dto.setCustomerComment("好");
        service.closeAndEvaluate(dto);
        verify(mapper, times(1)).updateStatus(1L, OpsTicketStatus.CLOSED.getCode());
    }

    @Test
    @DisplayName("scanSlaBreaches 标记 response/resolve 超时")
    void scanSla() {
        OpsTicketDO t1 = new OpsTicketDO();
        t1.setId(1L);
        t1.setResponseDueAt(LocalDateTime.now().minusMinutes(30));
        t1.setResolveDueAt(LocalDateTime.now().minusHours(2));
        t1.setResponseBreached(false);
        t1.setResolveBreached(false);
        OpsTicketDO t2 = new OpsTicketDO();
        t2.setId(2L);
        t2.setResponseDueAt(LocalDateTime.now().plusHours(1));
        t2.setResolveDueAt(LocalDateTime.now().plusHours(5));
        t2.setResponseBreached(false);
        t2.setResolveBreached(false);
        when(mapper.selectActiveTickets(any())).thenReturn(List.of(t1, t2));

        int n = service.scanSlaBreaches();
        assertThat(n).isEqualTo(2);
        verify(mapper, times(1)).markResponseBreached(1L);
        verify(mapper, times(1)).markResolveBreached(1L);
        verify(mapper, never()).markResponseBreached(2L);
    }

    @Test
    @DisplayName("slaSummary / aggregateByStatus 委托 mapper")
    void delegations() {
        when(mapper.aggregateSlaBreach()).thenReturn(List.of());
        when(mapper.aggregateByStatus(1L)).thenReturn(List.of());
        assertThat(service.slaSummary()).isEmpty();
        assertThat(service.aggregateByStatus(1L)).isEmpty();
        assertThat(service.aggregateByStatus(null)).isEmpty();
    }

    @Test
    @DisplayName("listByInitiation / Warranty / Assignee 空 id 返回空列表")
    void list_empty() {
        assertThat(service.listByInitiation(null)).isEmpty();
        assertThat(service.listByWarranty(null)).isEmpty();
        assertThat(service.listByAssignee(null, null)).isEmpty();
    }
}
