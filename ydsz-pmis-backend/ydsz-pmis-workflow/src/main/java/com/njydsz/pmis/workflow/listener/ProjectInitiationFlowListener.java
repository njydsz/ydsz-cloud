package com.njydsz.pmis.workflow.listener;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.InitiationFeignClient;
import com.njydsz.pmis.common.feign.NotificationPushClient;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.instance.FlowSubProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 项目立项流程事件监听器（业务侧示例 + 站内信触发器）
 *
 * <p>P2-35: 异步监听 FlowWorkflowEvent，解耦主流程事务。
 * <p>P0-1: 在关键生命周期埋点调用 FlowNotificationHelper，触发站内信触达。
 *
 * <p>本监听器兼任两层职责：
 * <ol>
 *   <li>业务流程联动（调用 initiationService / wbsService 同步立项及任务分解）</li>
 *   <li>通知触达（对标用友 BPM / 钉钉审批的实时通知能力）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("projectInitiationFlowListener")
@RequiredArgsConstructor
public class ProjectInitiationFlowListener implements FlowEventListener {

    /** 立项状态联动重试最大次数 */
    private static final int LINKAGE_MAX_ATTEMPTS = 3;
    /** 立项状态联动重试退避（毫秒） */
    private static final long LINKAGE_BACKOFF_MS = 50L;
    /** 立项业务键前缀（见 InitiationServiceImpl#startProcess: PMIS_INIT_ + id） */
    private static final String INIT_BIZ_KEY_PREFIX = "PMIS_INIT_";

    private final FlowNotificationHelper notificationHelper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowRunTaskMapper taskMapper;
    /** P1-3: 子流程服务（监听器作为子流程完成回调的入口） */
    private final FlowSubProcessService subProcessService;
    /** P1-7: 立项状态联动 Feign 客户端（审批中 / 已批准 / 已驳回） */
    private final InitiationFeignClient initiationFeignClient;
    /** P1-7: 实时推送 Feign 客户端（IM 渠道待办通知） */
    private final NotificationPushClient notificationPushClient;

    @Override
    public void onInstanceStart(String instanceId, Map<String, Object> variables) {
        log.info("[FlowListener] 立项流程启动: instanceId={} vars={}", instanceId,
                variables == null ? Collections.emptySet() : variables.keySet());
        // P1-7: 流程启动 → 标记立项为审批中（APPROVING）
        FlowInstanceDO instance = instanceId == null ? null : instanceMapper.selectById(instanceId);
        String initiationId = resolveInitiationId(instance);
        if (initiationId != null) {
            linkageWithRetry("markProcessing", initiationId,
                    () -> initiationFeignClient.markProcessing(initiationId));
        }
    }

    @Override
    public void onTaskCreated(String taskId) {
        // P0-1: 给当前办理人发送待办通知
        if (taskId == null) {
            return;
        }
        FlowRunTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        String assigneeId = task.getAssigneeId();
        if (assigneeId == null) {
            return;
        }
        String title = "您有一个新的审批待办";
        String content = String.format("【%s】 %s - %s 待您审批",
                nullSafe(task.getFlowName()),
                nullSafe(task.getTitle()),
                nullSafe(task.getNodeName()));
        notificationHelper.notifyTaskAssigned(assigneeId, title, content, taskId,
                "WORKFLOW_TASK", "INFO");
        // P1-7: 推送实时消息给当前办理人（IM / WebSocket 渠道）
        pushImNotification(assigneeId, title, content, taskId);
    }

    @Override
    public void onTaskCompleted(String taskId, String action, Map<String, Object> variables) {
        log.info("[FlowListener] 立项任务完成: taskId={} action={}", taskId, action);
        // 审批轨迹与驳回通知由 onInstanceCompleted / onInstanceRejected 统一处理，
        // 此处仅记录任务级审计日志，避免重复触达。
    }

    @Override
    public void onInstanceCompleted(String instanceId) {
        log.info("[FlowListener] 立项流程完成: instanceId={}", instanceId);
        // P0-1: 通知发起人流程已完成
        if (instanceId == null) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null || instance.getInitiatorId() == null) {
            return;
        }
        // P1-3: 子流程完成 → 回调父流程
        if (instance.getParentInstanceId() != null) {
            try {
                subProcessService.onSubProcessCompleted(instanceId);
            } catch (Exception e) {
                log.error("[FlowListener] 子流程完成回调父流程失败: child={} parent={} err={}",
                        instanceId, instance.getParentInstanceId(), e.getMessage(), e);
            }
        }
        notificationHelper.notifyInstanceCompleted(instance.getInitiatorId(),
                "您的审批已通过",
                String.format("【%s】 您发起的 %s 已审批通过",
                        nullSafe(instance.getFlowName()),
                        nullSafe(instance.getTitle())),
                instanceId);
        // P1-7: 流程通过 → 标记立项为已批准（APPROVED）
        String initiationId = resolveInitiationId(instance);
        if (initiationId != null) {
            linkageWithRetry("markApproved", initiationId,
                    () -> initiationFeignClient.markApproved(initiationId));
        }
    }

    @Override
    public void onInstanceRejected(String instanceId, String reason) {
        log.info("[FlowListener] 立项流程驳回: instanceId={} reason={}", instanceId, reason);
        // P0-1: 通知发起人流程已驳回
        if (instanceId == null) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null || instance.getInitiatorId() == null) {
            return;
        }
        // P1-3: 子流程驳回 → 同步父流程驳回
        if (instance.getParentInstanceId() != null) {
            try {
                subProcessService.onSubProcessTerminated(instanceId, reason, false);
            } catch (Exception e) {
                log.error("[FlowListener] 子流程驳回同步父流程失败: child={} parent={} err={}",
                        instanceId, instance.getParentInstanceId(), e.getMessage(), e);
            }
        }
        notificationHelper.notifyInstanceRejected(instance.getInitiatorId(),
                "您的审批被驳回",
                String.format("【%s】 您发起的 %s 已被驳回%s",
                        nullSafe(instance.getFlowName()),
                        nullSafe(instance.getTitle()),
                        reason == null || reason.isBlank() ? "" : "，原因：" + reason),
                instanceId);
        // P1-7: 流程驳回 → 标记立项为已驳回（REJECTED）
        String initiationId = resolveInitiationId(instance);
        if (initiationId != null) {
            linkageWithRetry("markRejected", initiationId,
                    () -> initiationFeignClient.markRejected(initiationId, reason));
        }
    }

    @Override
    public void onError(String instanceId, Throwable t) {
        log.error("[FlowListener][ALERT] 立项流程异常: instanceId={}", instanceId, t);
        // P1-7: 触发重试机制 —— 尝试恢复立项状态联动（标记审批中），失败不抛出
        if (instanceId == null) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        String initiationId = resolveInitiationId(instance);
        if (initiationId != null) {
            linkageWithRetry("markProcessing(recover)", initiationId,
                    () -> initiationFeignClient.markProcessing(initiationId));
        }
    }

    // ============================== P2-35: 异步事件监听 ==============================

    /**
     * P2-35: 异步监听 FlowWorkflowEvent，解耦主流程事务
     *
     * <p>通过 ApplicationEventPublisher 发布的事件在此异步处理，
     * 不影响主流程事务提交与性能。
     *
     * @param event 工作流事件
     */
    @EventListener
    @Async("auditExecutor")
    public void onFlowWorkflowEvent(FlowWorkflowEvent event) {
        log.info("[FlowListener] 异步事件: type={} instanceId={} taskId={}",
                event.getEventType(), event.getInstanceId(), event.getTaskId());
        // 事件分发由 onTaskUrged/onInstanceTerminated 等具体 default 方法处理；
        // 这里保留异步通道，便于后续扩展（IM 推送、监控埋点等）。
    }

    // ============================== P0-1: 关键事件通知触发 ==============================

    @Override
    public void onTaskUrged(String instanceId, String taskId) {
        // P0-1: 催办通知：实例级催办推送给所有当前待办办理人
        if (instanceId == null) {
            return;
        }
        List<FlowRunTaskDO> pending = taskMapper.selectPendingByInstance(instanceId);
        List<String> receivers = pending == null ? Collections.emptyList() : pending.stream()
                .map(t -> t.getAssigneeId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        String flowName = instance == null ? "" : nullSafe(instance.getFlowName());
        String title = "审批催办";
        String content = String.format("【%s】 您有一个待办任务被催办，请尽快处理", flowName);
        notificationHelper.notifyUrge(receivers, title, content, instanceId);
    }

    @Override
    public void onInstanceTerminated(String instanceId, String reason) {
        // P0-1: 终止通知：通知发起人
        if (instanceId == null) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null || instance.getInitiatorId() == null) {
            return;
        }
        notificationHelper.notifyInstanceTerminated(instance.getInitiatorId(),
                "您的流程已被终止",
                String.format("【%s】 您发起的 %s 已被终止%s",
                        nullSafe(instance.getFlowName()),
                        nullSafe(instance.getTitle()),
                        reason == null || reason.isBlank() ? "" : "，原因：" + reason),
                instanceId);
    }

    @Override
    public void onInstanceRecalled(String instanceId, String initiatorId) {
        // P0-1: 撤回通知：通知所有当前待办办理人
        if (instanceId == null) {
            return;
        }
        List<FlowRunTaskDO> pending = taskMapper.selectPendingByInstance(instanceId);
        List<String> receivers = pending == null ? Collections.emptyList() : pending.stream()
                .map(t -> t.getAssigneeId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        String flowName = instance == null ? "" : nullSafe(instance.getFlowName());
        String title = "审批已撤回";
        String content = String.format("【%s】 该流程已被发起人撤回", flowName);
        notificationHelper.notifyInstanceRecalled(receivers, title, content, instanceId);
    }

    @Override
    public void onTaskTransferred(String taskId, String fromUserId, String toUserId) {
        // P0-1: 转办通知：通知新办理人
        if (taskId == null || toUserId == null) {
            return;
        }
        FlowRunTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        notificationHelper.notifyTaskTransferred(toUserId,
                "您有一个转办任务",
                String.format("【%s】 %s - %s 已转办给您",
                        nullSafe(task.getFlowName()),
                        nullSafe(task.getTitle()),
                        nullSafe(task.getNodeName())),
                taskId);
    }

    @Override
    public void onTaskDelegated(String taskId, String fromUserId, String toUserId) {
        // P0-1: 委派通知：通知被委派人
        if (taskId == null || toUserId == null) {
            return;
        }
        FlowRunTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        notificationHelper.notifyTaskDelegated(toUserId,
                "您有一个委派任务",
                String.format("【%s】 %s - %s 已委派给您",
                        nullSafe(task.getFlowName()),
                        nullSafe(task.getTitle()),
                        nullSafe(task.getNodeName())),
                taskId);
    }

    @Override
    public void onTaskTimeout(String taskId, String instanceId) {
        // P0-1: 超时通知：通知当前办理人
        if (taskId == null) {
            return;
        }
        FlowRunTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        String assigneeId = task.getAssigneeId();
        if (assigneeId == null) {
            return;
        }
        notificationHelper.notifyTaskTimeout(assigneeId,
                "审批任务已超时",
                String.format("【%s】 %s - %s 已超时，请尽快处理",
                        nullSafe(task.getFlowName()),
                        nullSafe(task.getTitle()),
                        nullSafe(task.getNodeName())),
                taskId);
    }

    // ============================== 工具方法 ==============================

    /**
     * 从流程实例的业务键解析立项 ID。
     *
     * <p>业务键格式为 {@code PMIS_INIT_<initiationId>}（见 InitiationServiceImpl#startProcess），
     * 兼容直接以数字存储的业务键。
     *
     * @param instance 流程实例（可空）
     * @return 立项 ID，解析失败返回 null
     */
    private String resolveInitiationId(FlowInstanceDO instance) {
        if (instance == null) {
            return null;
        }
        String bizId = instance.getBusinessId();
        if (!StringUtils.hasText(bizId)) {
            return null;
        }
        String raw = bizId.startsWith(INIT_BIZ_KEY_PREFIX)
                ? bizId.substring(INIT_BIZ_KEY_PREFIX.length())
                : bizId;
        try {
            return raw.trim();
        } catch (NumberFormatException e) {
            log.warn("[FlowListener] 无法从业务键解析立项 ID: bizId={}", bizId);
            return null;
        }
    }

    /**
     * 立项状态联动 —— 带退避重试，吞掉异常避免影响主流程。
     *
     * <p>跨服务调用可能因网络抖动瞬时失败，重试 {@value #LINKAGE_MAX_ATTEMPTS} 次。
     * 最终失败仅记录告警，不抛出（状态可由对账任务补偿）。
     *
     * @param action       动作名（日志用）
     * @param initiationId 立项 ID
     * @param call         Feign 调用
     */
    private void linkageWithRetry(String action, String initiationId, Supplier<Result<Void>> call) {
        for (int attempt = 1; attempt <= LINKAGE_MAX_ATTEMPTS; attempt++) {
            try {
                Result<Void> result = call.get();
                if (result != null && result.isSuccess()) {
                    log.info("[FlowListener] 立项状态联动成功: action={} initiationId={} attempt={}",
                            action, initiationId, attempt);
                    return;
                }
                log.warn("[FlowListener] 立项状态联动返回失败: action={} initiationId={} attempt={} result={}",
                        action, initiationId, attempt, result);
            } catch (Exception e) {
                log.warn("[FlowListener] 立项状态联动异常: action={} initiationId={} attempt={}: {}",
                        action, initiationId, attempt, e.getMessage());
            }
            if (attempt < LINKAGE_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(LINKAGE_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("[FlowListener][ALERT] 立项状态联动最终失败: action={} initiationId={}",
                action, initiationId);
    }

    /**
     * 推送实时消息给办理人（IM / WebSocket 渠道）。
     *
     * <p>消息推送为非关键路径，失败仅记录日志，不影响任务创建。
     *
     * @param assigneeId 办理人 ID
     * @param title      标题
     * @param content    内容
     * @param taskId     任务 ID
     */
    private void pushImNotification(String assigneeId, String title, String content, String taskId) {
        if (assigneeId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("content", content);
            payload.put("taskId", taskId);
            payload.put("type", "WORKFLOW_TASK");
            notificationPushClient.pushToUser(assigneeId, "NOTIFICATION", payload);
        } catch (Exception e) {
            log.warn("[FlowListener] IM 推送失败: assigneeId={} taskId={}: {}",
                    assigneeId, taskId, e.getMessage());
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
