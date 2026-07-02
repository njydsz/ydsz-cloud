package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import com.njydsz.pmis.workflow.service.impl.FlowSlaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowSlaServiceImpl 单元测试
 *
 * <p>P1-6: SLA 超时自动策略（PASS/REJECT/NOTIFY/ESCALATE）
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("FlowSlaServiceImpl 单元测试")
class FlowSlaServiceImplTest {

    private FlowTaskMapper taskMapper;
    private FlowNodeMapper nodeMapper;
    private FlowInstanceMapper instanceMapper;
    private FlowTaskService taskService;
    private FlowNotificationHelper notificationHelper;
    private FlowMetrics flowMetrics;
    private FlowSlaServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(FlowTaskMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        taskService = mock(FlowTaskService.class);
        notificationHelper = mock(FlowNotificationHelper.class);
        // P2-3: Prometheus 指标 mock
        flowMetrics = mock(FlowMetrics.class);
        service = new FlowSlaServiceImpl(taskMapper, nodeMapper, instanceMapper, taskService,
                notificationHelper, flowMetrics);
    }

    // ============================== parseSlaConfig ==============================

    @Test
    @DisplayName("parseSlaConfig null/空 返回空 Map")
    void testParseSlaConfigEmpty() {
        assertThat(service.parseSlaConfig(null)).isEmpty();
        assertThat(service.parseSlaConfig("")).isEmpty();
        assertThat(service.parseSlaConfig("   ")).isEmpty();
    }

    @Test
    @DisplayName("parseSlaConfig 非法 JSON 返回空 Map 而不抛异常")
    void testParseSlaConfigInvalidJson() {
        assertThat(service.parseSlaConfig("not a json")).isEmpty();
        assertThat(service.parseSlaConfig("{invalid}")).isEmpty();
    }

    @Test
    @DisplayName("parseSlaConfig 合法 JSON 返回 Map")
    void testParseSlaConfigValidJson() {
        var map = service.parseSlaConfig("{\"timeoutMinutes\":120,\"action\":\"AUTO_PASS\"}");
        assertThat(map).containsEntry("timeoutMinutes", 120);
        assertThat(map).containsEntry("action", "AUTO_PASS");
    }

    // ============================== applySlaConfig ==============================

    @Test
    @DisplayName("applySlaConfig task/node 为 null 静默返回")
    void testApplySlaConfigNullArgs() {
        service.applySlaConfig(null, new FlowNodeDO());
        service.applySlaConfig(new FlowTaskDO(), null);
        // 无任何副作用，不抛异常即可
    }

    @Test
    @DisplayName("applySlaConfig 节点无 slaConfig 不设置 dueAt")
    void testApplySlaConfigNoConfig() {
        FlowTaskDO task = new FlowTaskDO();
        task.setCreatedAt(LocalDateTime.now());
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setSlaConfig(null);

        service.applySlaConfig(task, node);
        assertThat(task.getDueAt()).isNull();
    }

    @Test
    @DisplayName("applySlaConfig 节点配 timeoutMinutes=120 设置 dueAt")
    void testApplySlaConfigValidTimeout() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0, 0);
        FlowTaskDO task = new FlowTaskDO();
        task.setCreatedAt(createdAt);
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setSlaConfig("{\"timeoutMinutes\":120,\"action\":\"AUTO_PASS\"}");

        service.applySlaConfig(task, node);
        assertThat(task.getDueAt()).isEqualTo(createdAt.plusMinutes(120));
        assertThat(task.getSlaAction()).isEqualTo("AUTO_PASS");
    }

    @Test
    @DisplayName("applySlaConfig timeoutMinutes<=0 不设置 dueAt")
    void testApplySlaConfigInvalidTimeout() {
        FlowTaskDO task = new FlowTaskDO();
        task.setCreatedAt(LocalDateTime.now());
        FlowNodeDO node = new FlowNodeDO();
        node.setSlaConfig("{\"timeoutMinutes\":0}");

        service.applySlaConfig(task, node);
        assertThat(task.getDueAt()).isNull();
    }

    @Test
    @DisplayName("applySlaConfig 未知 action 记录 warning 但不抛异常")
    void testApplySlaConfigUnknownAction() {
        FlowTaskDO task = new FlowTaskDO();
        task.setCreatedAt(LocalDateTime.now());
        FlowNodeDO node = new FlowNodeDO();
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"UNKNOWN_ACTION\"}");

        service.applySlaConfig(task, node);
        // 仍然设置 dueAt，但 slaAction 为 null（无法解析）
        assertThat(task.getDueAt()).isNotNull();
        assertThat(task.getSlaAction()).isNull();
    }

    // ============================== scanAndProcess ==============================

    @Test
    @DisplayName("scanAndProcess 无候选任务返回 0")
    void testScanAndProcessEmpty() {
        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(Collections.emptyList());
        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("scanAndProcess 任务 dueAt 未到 跳过处理")
    void testScanAndProcessSkipNotOverdue() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus("PENDING");
        task.setDueAt(LocalDateTime.now().plusHours(2));
        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(1L)).thenReturn(task);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0); // 未到 dueAt，跳过
        verify(taskMapper, never()).incrementReminderCount(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("scanAndProcess 任务已超期 + REMIND 动作 + 未达 maxReminders 触发提醒")
    void testScanAndProcessRemind() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(5);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(10L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setNodeName("审批节点");
        task.setFlowName("测试流程");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(0);
        task.setLastRemindedAt(null);

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"REMIND\",\"maxReminders\":3,\"reminderIntervalMinutes\":60}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);
        when(taskMapper.incrementReminderCount(eq(10L), eq(1), any())).thenReturn(1);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(1);
        verify(notificationHelper, atLeastOnce()).notifyTaskTimeout(eq(1001L), anyString(),
                anyString(), eq(10L));
        verify(taskMapper).incrementReminderCount(eq(10L), eq(1), any());
    }

    @Test
    @DisplayName("scanAndProcess 已达 maxReminders 触发 AUTO_PASS 最终动作")
    void testScanAndProcessAutoPassFinal() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(20L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setNodeName("审批节点");
        task.setFlowName("测试流程");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(3); // 已达 maxReminders
        task.setLastRemindedAt(LocalDateTime.now().minusMinutes(120));

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"AUTO_PASS\",\"maxReminders\":3,\"reminderIntervalMinutes\":60}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(1);
        ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
        verify(taskService, atLeastOnce()).pass(captor.capture());
        FlowTaskOperateDTO dto = captor.getValue();
        assertThat(dto.getTaskId()).isEqualTo(20L);
        assertThat(dto.getUserId()).isEqualTo(0L); // 系统用户
        verify(taskMapper).markSlaAction(eq(20L), eq("AUTO_PASS"), eq(0));
    }

    @Test
    @DisplayName("scanAndProcess 已达 maxReminders 触发 AUTO_REJECT 最终动作")
    void testScanAndProcessAutoRejectFinal() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(30L);
        task.setTaskStatus("CLAIMED");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setNodeName("审批节点");
        task.setFlowName("测试流程");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(3);
        task.setLastRemindedAt(LocalDateTime.now().minusMinutes(120));

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"AUTO_REJECT\",\"maxReminders\":3,\"reminderIntervalMinutes\":60}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(1);
        verify(taskService, atLeastOnce()).reject(any(FlowTaskOperateDTO.class));
        verify(taskMapper).markSlaAction(eq(30L), eq("AUTO_REJECT"), eq(0));
    }

    @Test
    @DisplayName("scanAndProcess ESCALATE 升级到 escalateUserId")
    void testScanAndProcessEscalate() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(40L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setNodeName("审批节点");
        task.setFlowName("测试流程");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(3);
        task.setLastRemindedAt(LocalDateTime.now().minusMinutes(120));
        task.setSlaEscalated(0);

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"ESCALATE\",\"maxReminders\":3,\"reminderIntervalMinutes\":60,\"escalateUserId\":2002}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(40L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);
        when(taskMapper.updateById(any(FlowTaskDO.class))).thenReturn(1);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(1);
        ArgumentCaptor<FlowTaskOperateDTO> captor = ArgumentCaptor.forClass(FlowTaskOperateDTO.class);
        verify(taskService, atLeastOnce()).transfer(captor.capture());
        FlowTaskOperateDTO dto = captor.getValue();
        assertThat(dto.getTargetUserId()).isEqualTo(2002L);
        verify(taskMapper).markSlaAction(eq(40L), eq("ESCALATE"), eq(1));
    }

    @Test
    @DisplayName("scanAndProcess 已升级的任务不重复升级")
    void testScanAndProcessEscalateAlreadyEscalated() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(50L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(3);
        task.setSlaEscalated(1); // 已升级

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"ESCALATE\",\"maxReminders\":3}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
        verify(taskService, never()).transfer(any(FlowTaskOperateDTO.class));
    }

    @Test
    @DisplayName("scanAndProcess 任务已 PENDING/CLAIMED 之外的终态 跳过")
    void testScanAndProcessSkipFinished() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(60L);
        task.setTaskStatus("COMPLETED");
        task.setDueAt(LocalDateTime.now().minusMinutes(120));
        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(60L)).thenReturn(task);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
    }

    @Test
    @DisplayName("scanAndProcess 任务 dueAt 为 null 跳过")
    void testScanAndProcessSkipNoDueAt() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(70L);
        task.setTaskStatus("PENDING");
        task.setDueAt(null);
        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(70L)).thenReturn(task);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
    }

    @Test
    @DisplayName("scanAndProcess 距离上次提醒未到间隔 跳过")
    void testScanAndProcessSkipRemindInterval() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(80L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(1);
        task.setLastRemindedAt(LocalDateTime.now().minusMinutes(10)); // 10 分钟前，刚提醒

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig("{\"timeoutMinutes\":60,\"action\":\"REMIND\",\"maxReminders\":3,\"reminderIntervalMinutes\":60}");

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(80L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
        verify(taskMapper, never()).incrementReminderCount(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("scanAndProcess 节点无 SLA 配置记录 warning 并跳过")
    void testScanAndProcessNoSlaConfigOnNode() {
        LocalDateTime pastDue = LocalDateTime.now().minusMinutes(120);
        FlowTaskDO task = new FlowTaskDO();
        task.setId(90L);
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setNodeCode("n1");
        task.setDefinitionId(1L);
        task.setDueAt(pastDue);
        task.setReminderCount(0);

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setSlaConfig(null); // 节点未配

        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(task));
        when(taskMapper.selectById(90L)).thenReturn(task);
        when(nodeMapper.selectByCode(1L, "n1")).thenReturn(node);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
    }

    @Test
    @DisplayName("scanAndProcess 单条异常不影响其他任务")
    void testScanAndProcessPartialFailure() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(101L);
        t1.setTaskStatus("COMPLETED"); // 已完成，会跳过
        FlowTaskDO t2 = new FlowTaskDO();
        t2.setId(102L);
        t2.setTaskStatus("COMPLETED");
        when(taskMapper.selectSlaCandidates(anyInt())).thenReturn(List.of(t1, t2));
        when(taskMapper.selectById(101L)).thenReturn(t1);
        when(taskMapper.selectById(102L)).thenReturn(t2);

        int n = service.scanAndProcess();
        assertThat(n).isEqualTo(0);
        // 两条都跳过，不抛异常
    }

    // ============================== processOverdue (manual trigger) ==============================

    @Test
    @DisplayName("processOverdue null task 返回 false")
    void testProcessOverdueNull() {
        assertThat(service.processOverdue(null)).isFalse();
    }

    @Test
    @DisplayName("processOverdue task 不存在 返回 false")
    void testProcessOverdueNotFound() {
        FlowTaskDO input = new FlowTaskDO();
        input.setId(999L);
        when(taskMapper.selectById(999L)).thenReturn(null);
        assertThat(service.processOverdue(input)).isFalse();
    }

    @Test
    @DisplayName("processOverdue 已完成任务 返回 false")
    void testProcessOverdueCompleted() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus("COMPLETED");
        when(taskMapper.selectById(1L)).thenReturn(task);
        assertThat(service.processOverdue(task)).isFalse();
    }
}
