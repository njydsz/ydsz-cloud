package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.dto.EmbeddedApprovalViewDTO;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowAiAssistService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import com.njydsz.pmis.workflow.service.impl.FlowEmbeddedApprovalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-2 嵌入式审批服务单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FlowEmbeddedApprovalService 嵌入式审批")
class FlowEmbeddedApprovalServiceImplTest {

    private FlowInstanceService instanceService;
    private FlowTaskService taskService;
    private FlowAiAssistService aiAssistService;
    private FlowHisTaskMapper hisTaskMapper;
    private FlowEmbeddedApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        instanceService = mock(FlowInstanceService.class);
        taskService = mock(FlowTaskService.class);
        aiAssistService = mock(FlowAiAssistService.class);
        hisTaskMapper = mock(FlowHisTaskMapper.class);
        service = new FlowEmbeddedApprovalServiceImpl(instanceService, taskService, aiAssistService, hisTaskMapper);
    }

    @Test
    @DisplayName("参数校验: businessType 为空抛出异常")
    void loadPanel_blankBusinessType() {
        assertThatThrownBy(() -> service.loadPanel("", "1", 1L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("参数校验: businessId 为空抛出异常")
    void loadPanel_nullBusinessId() {
        assertThatThrownBy(() -> service.loadPanel("PROJECT_INITIATION", null, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("未发起流程：返回空面板，myRole=OBSERVER，actions=[SUBMIT]")
    void loadPanel_notStarted() {
        when(instanceService.getByBusiness(any(), any())).thenReturn(null);
        when(aiAssistService.isAiAvailable()).thenReturn(false);

        EmbeddedApprovalViewDTO view = service.loadPanel("PROJECT_INITIATION", "1", 100L);

        assertThat(view).isNotNull();
        assertThat(view.getInstance()).isNull();
        assertThat(view.getMyRole()).isEqualTo("OBSERVER");
        assertThat(view.getActions()).contains("SUBMIT");
        assertThat(view.getMessage()).isEqualTo("未发起流程");
        assertThat(view.isFinished()).isFalse();
    }

    @Test
    @DisplayName("发起人视角: myRole=INITIATOR，可 WITHDRAW")
    void loadPanel_initiator() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.getMyRole()).isEqualTo("INITIATOR");
        assertThat(view.getActions()).contains("WITHDRAW");
        assertThat(view.getActions()).contains("URGE");
        assertThat(view.getCurrentTasks()).hasSize(1);
        assertThat(view.getCurrentTasks().get(0).isMine()).isTrue();
    }

    @Test
    @DisplayName("审批人视角: myRole=APPROVER，可 PASS/REJECT/TRANSFER")
    void loadPanel_approver() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(true);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.getMyRole()).isEqualTo("APPROVER");
        assertThat(view.getActions()).contains("PASS", "REJECT", "TRANSFER", "DELEGATE", "URGE");
        assertThat(view.isAiAvailable()).isTrue();
    }

    @Test
    @DisplayName("观察者视角: myRole=OBSERVER，无 actions")
    void loadPanel_observer() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "300", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.getMyRole()).isEqualTo("OBSERVER");
        assertThat(view.getActions()).isEmpty();
    }

    @Test
    @DisplayName("流程已结束: finished=true，actions 为空")
    void loadPanel_finished() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.COMPLETED);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(Collections.emptyList());
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.isFinished()).isTrue();
        assertThat(view.getMessage()).isEqualTo("流程已结束");
        assertThat(view.getActions()).isEmpty();
    }

    @Test
    @DisplayName("可撤回: 发起人视角下，pending 无 CLAIMED 状态可撤回")
    void loadPanel_canRecall() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        t.setTaskStatus(FlowTaskStatus.PENDING.name());
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.isCanRecall()).isTrue();
    }

    @Test
    @DisplayName("不可撤回: 当前节点已被签收(CLAIMED)则不能撤回")
    void loadPanel_cannotRecallWhenClaimed() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        t.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(Collections.emptyList());
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 100L);

        assertThat(view.isCanRecall()).isFalse();
    }

    @Test
    @DisplayName("历史轨迹: 加载完成的历史任务按 finishAt 排序组装")
    void loadPanel_history() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        FlowHisTaskDO h1 = new FlowHisTaskDO();
        h1.setId(100L);
        h1.setNodeCode("node1");
        h1.setNodeName("Node1");
        h1.setAssigneeId("100");
        h1.setAssigneeName("张三");
        h1.setComment("同意");
        h1.setFinishAt(LocalDateTime.of(2026, 7, 1, 10, 0, 0));
        h1.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        h1.setPerformType("PASS");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(List.of(h1));
        when(aiAssistService.isAiAvailable()).thenReturn(false);
        when(instanceService.toView(any(), any())).thenAnswer(inv -> {
            FlowInstanceDO i = inv.getArgument(0);
            return FlowInstanceViewDTO.builder()
                    .id(i.getId())
                    .flowStatus(i.getFlowStatus())
                    .build();
        });

        EmbeddedApprovalViewDTO view = service.loadPanel("X", "1", 200L);

        assertThat(view.getHistory()).hasSize(1);
        assertThat(view.getHistory().get(0).get("comment")).isEqualTo("同意");
        assertThat(view.getHistory().get(0).get("type")).isEqualTo("TASK");
    }

    @Test
    @DisplayName("快捷操作 PASS: 自动找 mine 任务并通过")
    void quickAction_pass() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("PASS");
        dto.setUserId(100L);
        dto.setComment("同意");

        service.quickAction(dto);

        verify(taskService, times(1)).pass(any());
        verify(taskService, never()).reject(any());
    }

    @Test
    @DisplayName("快捷操作 REJECT: 自动找 mine 任务并驳回")
    void quickAction_reject() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("REJECT");
        dto.setUserId(100L);
        dto.setComment("不同意");

        service.quickAction(dto);

        verify(taskService, times(1)).reject(any());
    }

    @Test
    @DisplayName("快捷操作 TRANSFER: 必须指定 targetUserId")
    void quickAction_transferWithoutTarget() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "100", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("TRANSFER");
        dto.setUserId(100L);

        assertThatThrownBy(() -> service.quickAction(dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("快捷操作 URGE: 不需要 mine 任务")
    void quickAction_urge() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(Collections.emptyList());
        when(taskService.urge(anyLong(), anyLong(), any())).thenReturn(List.of("100", "200"));

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("URGE");
        dto.setUserId(100L);
        dto.setComment("请尽快处理");

        service.quickAction(dto);

        verify(taskService, times(1)).urge(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("快捷操作 WITHDRAW: 仅发起人可撤回")
    void quickAction_withdraw() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.RUNNING);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(instanceService.recall(1L, 100L)).thenReturn(true);

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("WITHDRAW");
        dto.setUserId(100L);

        service.quickAction(dto);

        verify(instanceService, times(1)).recall(1L, 100L);
    }

    @Test
    @DisplayName("快捷操作 WITHDRAW 失败: 抛业务异常")
    void quickAction_withdrawFailed() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.RUNNING);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(instanceService.recall(1L, 100L)).thenReturn(false);

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("WITHDRAW");
        dto.setUserId(100L);

        assertThatThrownBy(() -> service.quickAction(dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.BIZ_ERROR.getCode());
    }

    @Test
    @DisplayName("流程已结束不能操作: 抛业务异常")
    void quickAction_finishedInstance() {
        FlowInstanceDO ins = instance(1L, 100L, FlowInstanceStatus.COMPLETED);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("PASS");
        dto.setUserId(100L);

        assertThatThrownBy(() -> service.quickAction(dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.BIZ_ERROR.getCode());
    }

    @Test
    @DisplayName("快捷操作 unknown action: 抛参数异常")
    void quickAction_unknownAction() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("BLAH");
        dto.setUserId(100L);

        assertThatThrownBy(() -> service.quickAction(dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("当前用户无 mine 任务: PASS 抛权限异常")
    void quickAction_noMine() {
        FlowInstanceDO ins = instance(1L, 200L, FlowInstanceStatus.RUNNING);
        FlowTaskDO t = pending(10L, "1", "999", "USER");
        when(instanceService.getByBusiness("X", "1")).thenReturn(ins);
        when(taskService.listPendingByInstance(1L)).thenReturn(List.of(t));

        EmbeddedApprovalActionDTO dto = new EmbeddedApprovalActionDTO();
        dto.setBusinessType("X");
        dto.setBusinessId("1");
        dto.setAction("PASS");
        dto.setUserId(100L);

        assertThatThrownBy(() -> service.quickAction(dto))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    // ============ 工具方法 ============

    private FlowInstanceDO instance(Long id, Long initiatorId, FlowInstanceStatus status) {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(id);
        ins.setInitiatorId(initiatorId);
        ins.setFlowStatus(status.name());
        ins.setFlowCode("TEST_FLOW");
        ins.setCurrentNodeCode("node1");
        ins.setCurrentNodeName("Node1");
        return ins;
    }

    private FlowTaskDO pending(Long id, String instanceId, String assigneeId, String assigneeType) {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(id);
        t.setInstanceId(1L);
        t.setAssigneeId(assigneeId);
        t.setAssigneeType(assigneeType);
        t.setAssigneeName("测试用户");
        t.setNodeCode("node1");
        t.setNodeName("Node1");
        t.setTaskStatus(FlowTaskStatus.PENDING.name());
        return t;
    }
}
