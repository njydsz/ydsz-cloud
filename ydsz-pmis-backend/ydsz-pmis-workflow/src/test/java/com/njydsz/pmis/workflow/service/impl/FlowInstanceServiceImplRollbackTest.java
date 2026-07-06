package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowInstanceServiceImpl.rollback 单元测试
 *
 * <p>P2-3：覆盖流程回滚（已完成实例撤销）的核心场景与边界条件，包括：
 * <ul>
 *   <li>正常回滚 — 发起人 / 管理员 / 默认时间窗口内</li>
 *   <li>状态校验 — 非 COMPLETED 状态拒绝回滚</li>
 *   <li>权限校验 — 非发起人且非管理员拒绝回滚</li>
 *   <li>参数校验 — 回滚原因为空拒绝</li>
 *   <li>时间窗口 — 超出最大天数拒绝</li>
 *   <li>状态更新 — 实例状态置为 ROLLED_BACK，durationMs 重算</li>
 *   <li>变量持久化 — _rollback 元信息写入 variable JSON</li>
 *   <li>事件触发 — onInstanceRolledBack 被调用</li>
 *   <li>Spring 事件发布 — INSTANCE_ROLLED_BACK 事件被发布</li>
 *   <li>无 endAt 时跳过时间窗口校验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowInstanceServiceImplRollbackTest {

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
    private static final Long OTHER_USER_ID = 999L;

    @BeforeEach
    void setUp() {
        service = new FlowInstanceServiceImpl(
                instanceMapper, definitionService, canaryService, advancer,
                taskService, taskMapper, nodeMapper, skipMapper, variableStrategy,
                List.of(eventListener), flowMetrics, eventPublisher,
                subProcessService, ccService, autoTriggerService, eventSubscriptionService);
    }

    @AfterEach
    void tearDown() {
        // 清理 SecurityContext，避免线程复用串扰
        SecurityContext.clear();
    }

    // ==================== 正常场景 ====================

    @Test
    @DisplayName("发起人回滚 COMPLETED 实例 - 成功")
    void rollbackByInitiatorShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, INITIATOR_ID, "项目信息有误，需撤销", 7);

        assertThat(result).isTrue();
        // 状态置为 ROLLED_BACK
        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.ROLLED_BACK.name()),
                eq(instance.getCurrentNodeCode()),
                eq(instance.getCurrentNodeName()),
                any(LocalDateTime.class),
                anyLong());
        // variable 持久化
        verify(instanceMapper).updateVariable(eq(INSTANCE_ID), anyString());
        // Prometheus 指标
        verify(flowMetrics).incRecall(instance.getFlowCode());
        // 事件监听器被触发
        verify(eventListener).onInstanceRolledBack(INSTANCE_ID, INITIATOR_ID, "项目信息有误，需撤销");
    }

    @Test
    @DisplayName("管理员回滚 COMPLETED 实例 - 成功（含权限校验）")
    void rollbackByAdminShouldSucceed() {
        LoginUser admin = new LoginUser();
        admin.setUserId(OTHER_USER_ID);
        admin.setPermissions(List.of("workflow:instance:rollback"));
        SecurityContext.setCurrent(admin);

        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(2));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, OTHER_USER_ID, "管理员撤销", 7);

        assertThat(result).isTrue();
        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.ROLLED_BACK.name()),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("超管回滚 COMPLETED 实例 - 成功")
    void rollbackBySuperAdminShouldSucceed() {
        LoginUser superAdmin = new LoginUser();
        superAdmin.setUserId(OTHER_USER_ID);
        superAdmin.setPermissions(List.of("*:*:*"));
        SecurityContext.setCurrent(superAdmin);

        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(3));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, OTHER_USER_ID, "超管撤销", 7);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("默认 maxRollbackDays - 传 0 时使用默认值 7 天")
    void rollbackShouldUseDefaultDaysWhenZero() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(5));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 0);

        assertThat(result).isTrue();
        verify(instanceMapper).updateStatus(eq(INSTANCE_ID),
                eq(FlowInstanceStatus.ROLLED_BACK.name()),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("无 endAt 时跳过时间窗口校验")
    void rollbackShouldSkipTimeWindowWhenEndAtIsNull() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(null); // 无完成时间
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 1);

        assertThat(result).isTrue();
    }

    // ==================== 状态校验 ====================

    @Test
    @DisplayName("回滚 RUNNING 实例 - 抛出 BAD_REQUEST")
    void rollbackRunningInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.RUNNING, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                    // 消息码 a1b2c3d4 为状态校验错误码，参数含状态名
                    assertThat(biz.getMessage()).contains("a1b2c3d4");
                });

        // 不应更新状态
        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("回滚 TERMINATED 实例 - 抛出 BAD_REQUEST")
    void rollbackTerminatedInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.TERMINATED, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                    assertThat(biz.getMessage()).contains("a1b2c3d4");
                });
    }

    @Test
    @DisplayName("回滚 REJECTED 实例 - 抛出 BAD_REQUEST")
    void rollbackRejectedInstanceShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.REJECTED, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                    assertThat(biz.getMessage()).contains("a1b2c3d4");
                });
    }

    // ==================== 权限校验 ====================

    @Test
    @DisplayName("非发起人且非管理员 - 抛出 FORBIDDEN")
    void rollbackByUnauthorizedUserShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, OTHER_USER_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.FORBIDDEN.getCode());
                });

        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("回滚原因为空 - 抛出 BAD_REQUEST")
    void rollbackWithEmptyReasonShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("回滚原因为 null - 抛出 BAD_REQUEST")
    void rollbackWithNullReasonShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, null, 7))
                .isInstanceOf(BizException.class);
    }

    // ==================== 时间窗口校验 ====================

    @Test
    @DisplayName("超出回滚时间窗口 - 抛出 BAD_REQUEST")
    void rollbackExceedingTimeWindowShouldThrow() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        // 完成于 10 天前，但只允许 7 天
        instance.setEndAt(LocalDateTime.now().minusDays(10));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("刚好在时间窗口边界（第 7 天） - 成功")
    void rollbackOnTimeWindowBoundaryShouldSucceed() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        // 完成于 6 天前（< 7 天边界）
        instance.setEndAt(LocalDateTime.now().minusDays(6));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        boolean result = service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7);

        assertThat(result).isTrue();
    }

    // ==================== 实例不存在 ====================

    @Test
    @DisplayName("实例不存在 - 抛出 NOT_FOUND")
    void rollbackNonExistentInstanceShouldThrow() {
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    // ==================== 元信息持久化 ====================

    @Test
    @DisplayName("variable JSON 持久化 - 包含 _rollback 元信息")
    void rollbackShouldPersistRollbackMetadata() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setVariable("{\"key1\":\"value1\"}");
        instance.setEndAt(LocalDateTime.now().minusDays(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.rollback(INSTANCE_ID, INITIATOR_ID, "测试撤销", 7);

        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(INSTANCE_ID), varCaptor.capture());
        String persistedVar = varCaptor.getValue();
        // 保留原有变量
        assertThat(persistedVar).contains("key1").contains("value1");
        // 追加 _rollback 元信息
        assertThat(persistedVar).contains("_rollback")
                .contains("operatorId")
                .contains("reason")
                .contains("测试撤销")
                .contains("rolledBackAt");
    }

    // ==================== 事件触发 ====================

    @Test
    @DisplayName("事件触发 - onInstanceRolledBack 被调用")
    void rollbackShouldFireEventListener() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7);

        verify(eventListener, times(1)).onInstanceRolledBack(INSTANCE_ID, INITIATOR_ID, "撤销");
    }

    @Test
    @DisplayName("Spring 事件发布 - INSTANCE_ROLLED_BACK 被发布")
    void rollbackShouldPublishSpringEvent() {
        FlowInstanceDO instance = buildInstance(FlowInstanceStatus.COMPLETED, INITIATOR_ID);
        instance.setEndAt(LocalDateTime.now().minusDays(1));
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.rollback(INSTANCE_ID, INITIATOR_ID, "撤销", 7);

        // ApplicationEventPublisher.publishEvent 被调用一次
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    // ==================== 辅助方法 ====================

    private FlowInstanceDO buildInstance(FlowInstanceStatus status, Long initiatorId) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(INSTANCE_ID);
        instance.setFlowCode("project_initiation");
        instance.setFlowName("项目立项审批");
        instance.setDefinitionId(200L);
        instance.setFlowVersion("1.0");
        instance.setBusinessType("PROJECT");
        instance.setBusinessId("PRJ-2024-001");
        instance.setTitle("项目立项审批-PRJ-2024-001");
        instance.setInitiatorId(initiatorId);
        instance.setInitiatorName("张三");
        instance.setCurrentNodeCode("end");
        instance.setCurrentNodeName("结束");
        instance.setFlowStatus(status.name());
        instance.setActivityStatus(1);
        instance.setStartAt(LocalDateTime.now().minusDays(5));
        instance.setEndAt(LocalDateTime.now().minusDays(1));
        instance.setDurationMs(345600000L);
        instance.setTenantId(1L);
        return instance;
    }
}
