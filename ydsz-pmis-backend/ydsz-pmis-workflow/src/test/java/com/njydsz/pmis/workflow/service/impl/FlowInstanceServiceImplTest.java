package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowAutoTriggerService;
import com.njydsz.pmis.workflow.service.FlowCanaryService;
import com.njydsz.pmis.workflow.service.FlowCcService;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowEventSubscriptionService;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowInstanceServiceImpl 单元测试
 *
 * <p>覆盖流程实例管理的核心方法：启动、查询、终止、挂起、激活、完成、撤回、分页、变量读写、视图转换。
 * rollback 方法由 FlowInstanceServiceImplRollbackTest 单独覆盖。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowInstanceServiceImplTest {

    @Mock private FlowInstanceMapper instanceMapper;
    @Mock private FlowDefinitionService definitionService;
    @Mock private FlowCanaryService canaryService;
    @Mock private FlowAdvancer advancer;
    @Mock private FlowTaskService taskService;
    @Mock private FlowTaskMapper taskMapper;
    @Mock private FlowNodeMapper nodeMapper;
    @Mock private FlowSkipMapper skipMapper;
    @Mock private FlowVariableStrategy variableStrategy;
    @Mock private FlowMetrics flowMetrics;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FlowSubProcessService subProcessService;
    @Mock private FlowCcService ccService;
    @Mock private FlowAutoTriggerService autoTriggerService;
    @Mock private FlowEventSubscriptionService eventSubscriptionService;
    @Mock private FlowEventListener eventListener;

    private FlowInstanceServiceImpl service;

    private static final Long INSTANCE_ID = 1001L;
    private static final Long INITIATOR_ID = 500L;

    @BeforeEach
    void setUp() {
        service = new FlowInstanceServiceImpl(
                instanceMapper, definitionService, canaryService, advancer,
                taskService, taskMapper, nodeMapper, skipMapper, variableStrategy,
                List.of(eventListener), flowMetrics, eventPublisher,
                subProcessService, ccService, autoTriggerService, eventSubscriptionService);
    }

    // ============ 查询 ============

    @Test
    @DisplayName("根据ID查询实例 - 成功")
    void getByIdShouldReturnInstance() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        FlowInstanceDO result = service.getById(INSTANCE_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(INSTANCE_ID);
        verify(instanceMapper).selectById(INSTANCE_ID);
    }

    @Test
    @DisplayName("根据ID查询实例 - 不存在返回 null")
    void getByIdShouldReturnNullWhenNotFound() {
        when(instanceMapper.selectById(999L)).thenReturn(null);

        FlowInstanceDO result = service.getById(999L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("根据业务键查询实例 - 成功")
    void getByBusinessShouldReturnInstance() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectByBusiness("PROJECT", "PRJ-001")).thenReturn(instance);

        FlowInstanceDO result = service.getByBusiness("PROJECT", "PRJ-001");

        assertThat(result).isNotNull();
        verify(instanceMapper).selectByBusiness("PROJECT", "PRJ-001");
    }

    // ============ 终止 ============

    @Test
    @DisplayName("终止运行中实例 - 成功")
    void terminateShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setStartAt(LocalDateTime.now().minusHours(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.terminate(INSTANCE_ID, "业务变更");

        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.TERMINATED.name()),
                any(), any(), any(), any());
        verify(taskService).cancelByInstance(INSTANCE_ID, "CANCELLED");
        verify(flowMetrics).incInstanceFinished(instance.getFlowCode(), "TERMINATED");
    }

    @Test
    @DisplayName("终止已完成实例 - 抛出异常")
    void terminateFinishedInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.terminate(INSTANCE_ID, "reason"))
                .isInstanceOf(BizException.class);

        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    // ============ 挂起 / 激活 ============

    @Test
    @DisplayName("挂起运行中实例 - 成功")
    void suspendShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.suspend(INSTANCE_ID);

        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.SUSPENDED.name()),
                any(), any(), any(), any());
        verify(taskMapper).freezeByInstance(INSTANCE_ID);
        verify(flowMetrics).incInstanceSuspended(instance.getFlowCode());
    }

    @Test
    @DisplayName("挂起非运行中实例 - 抛出异常")
    void suspendNonRunningInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.suspend(INSTANCE_ID))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("激活挂起实例 - 成功")
    void activateShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.SUSPENDED);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.activate(INSTANCE_ID);

        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.RUNNING.name()),
                any(), any(), any(), any());
        verify(taskMapper).unfreezeByInstance(INSTANCE_ID);
        verify(flowMetrics).incInstanceActivated(instance.getFlowCode());
    }

    @Test
    @DisplayName("激活非挂起实例 - 抛出异常")
    void activateNonSuspendedInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.activate(INSTANCE_ID))
                .isInstanceOf(BizException.class);
    }

    // ============ 完成 ============

    @Test
    @DisplayName("完成运行中实例 - 成功")
    void completeShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setStartAt(LocalDateTime.now().minusHours(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.complete(INSTANCE_ID, "end_node");

        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.COMPLETED.name()),
                eq("end_node"), any(), any(), any());
        verify(taskService).cancelByInstance(INSTANCE_ID, "SKIPPED");
        verify(flowMetrics).incInstanceFinished(instance.getFlowCode(), "COMPLETED");
    }

    @Test
    @DisplayName("完成已完成实例 - 直接返回")
    void completeFinishedInstanceShouldReturn() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.complete(INSTANCE_ID, "end_node");

        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    // ============ 变量读写 ============

    @Test
    @DisplayName("读取实例变量 - 成功")
    void getVariablesShouldReturnMap() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setVariable("{\"key1\":\"value1\",\"key2\":123}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        Map<String, Object> variables = service.getVariables(INSTANCE_ID);

        assertThat(variables).containsEntry("key1", "value1");
        assertThat(variables).containsEntry("key2", 123);
    }

    @Test
    @DisplayName("读取实例变量 - 变量为空返回空Map")
    void getVariablesShouldReturnEmptyWhenNoVariable() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setVariable(null);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        Map<String, Object> variables = service.getVariables(INSTANCE_ID);

        assertThat(variables).isEmpty();
    }

    @Test
    @DisplayName("设置单个变量 - 成功")
    void setVariableShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setVariable("{\"existing\":\"old\"}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.setVariable(INSTANCE_ID, "newKey", "newValue");

        verify(instanceMapper).updateVariable(eq(INSTANCE_ID), anyString());
    }

    @Test
    @DisplayName("设置变量 - key为空抛出异常")
    void setVariableWithEmptyKeyShouldThrow() {
        assertThatThrownBy(() -> service.setVariable(INSTANCE_ID, "", "value"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("批量设置变量 - 成功")
    void setVariablesShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        instance.setVariable("{\"existing\":\"old\"}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        Map<String, Object> newVars = Map.of("k1", "v1", "k2", 456);
        service.setVariables(INSTANCE_ID, newVars);

        verify(instanceMapper).updateVariable(eq(INSTANCE_ID), anyString());
    }

    @Test
    @DisplayName("批量设置变量 - 空Map直接返回")
    void setVariablesWithEmptyMapShouldReturn() {
        service.setVariables(INSTANCE_ID, Collections.emptyMap());

        verify(instanceMapper, never()).updateVariable(anyLong(), anyString());
    }

    // ============ 视图转换 ============

    @Test
    @DisplayName("toView - 正常转换")
    void toViewShouldReturnDTO() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        List<FlowInstanceViewDTO.FlowTaskViewDTO> tasks = Collections.emptyList();

        FlowInstanceViewDTO view = service.toView(instance, tasks);

        assertThat(view).isNotNull();
        assertThat(view.getId()).isEqualTo(INSTANCE_ID);
        assertThat(view.getFlowCode()).isEqualTo("project_initiation");
        assertThat(view.getFlowStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("toView - null实例返回null")
    void toViewShouldReturnNullForNullInstance() {
        FlowInstanceViewDTO view = service.toView(null, Collections.emptyList());

        assertThat(view).isNull();
    }

    // ============ 分页查询 ============

    @Test
    @DisplayName("分页查询实例 - 成功")
    void pageShouldReturnPageResult() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectPage(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(instance));
        when(instanceMapper.countPage(any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);

        PageResult<FlowInstanceDO> result = service.page(
                "PROJECT", INITIATOR_ID, "RUNNING",
                null, null, 1L, 1, 10);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
    }

    // ============ 发起人查询 ============

    @Test
    @DisplayName("按发起人查询实例列表 - 成功")
    void listByInitiatorShouldReturnList() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING);
        when(instanceMapper.selectByInitiator(INITIATOR_ID, "RUNNING"))
                .thenReturn(List.of(instance));

        List<FlowInstanceDO> result = service.listByInitiator(INITIATOR_ID, "RUNNING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInitiatorId()).isEqualTo(INITIATOR_ID);
    }

    // ============ 设置到期时间 ============

    @Test
    @DisplayName("设置实例到期时间 - 成功")
    void setDueAtShouldSucceed() {
        LocalDateTime dueAt = LocalDateTime.now().plusDays(3);

        service.setDueAt(INSTANCE_ID, dueAt);

        verify(instanceMapper).updateDueAt(INSTANCE_ID, dueAt);
    }

    // ============ 辅助方法 ============

    private FlowInstanceDO buildInstance(FlowInstanceStatus status) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(INSTANCE_ID);
        instance.setFlowCode("project_initiation");
        instance.setFlowName("项目立项审批");
        instance.setDefinitionId(200L);
        instance.setFlowVersion("1.0");
        instance.setBusinessType("PROJECT");
        instance.setBusinessId("PRJ-001");
        instance.setTitle("项目立项审批-PRJ-001");
        instance.setInitiatorId(INITIATOR_ID);
        instance.setInitiatorName("张三");
        instance.setCurrentNodeCode("approval_1");
        instance.setCurrentNodeName("部门审批");
        instance.setFlowStatus(status.name());
        instance.setActivityStatus(1);
        instance.setStartAt(LocalDateTime.now().minusHours(2));
        instance.setTenantId(1L);
        return instance;
    }
}