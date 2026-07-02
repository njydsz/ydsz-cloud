package com.njydsz.pmis.workflow.listener;

import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目立项流程事件监听器（业务侧示例 + 站内信触发器）
 *
 * <p>P2-35: 异步监听 FlowWorkflowEvent，解耦主流程事务。
 * <p>P0-1: 在关键生命周期埋点调用 FlowNotificationHelper，触发站内信触达。
 *
 * <p>本监听器兼任两层职责：
 * <ol>
 *   <li>业务流程联动（业务侧 TODO 中描述的 initiationService / wbsService 联动）</li>
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

    private final FlowNotificationHelper notificationHelper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskMapper taskMapper;
    /** P1-3: 子流程服务（监听器作为子流程完成回调的入口） */
    private final FlowSubProcessService subProcessService;

    @Override
    public void onInstanceStart(Long instanceId, Map<String, Object> variables) {
        log.info("[FlowListener] 立项流程启动: instanceId={} vars={}", instanceId,
                variables == null ? java.util.Collections.emptySet() : variables.keySet());
        // TODO: 调用 initiationService.markProcessing(instanceId)
    }

    @Override
    public void onTaskCreated(Long taskId) {
        // P0-1: 给当前办理人发送待办通知
        if (taskId == null) {
            return;
        }
        FlowTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        Long assigneeId = parseUserId(task.getAssigneeId());
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
        // TODO: 推送消息给当前办理人（IM 渠道）
    }

    @Override
    public void onTaskCompleted(Long taskId, String action, Map<String, Object> variables) {
        log.info("[FlowListener] 立项任务完成: taskId={} action={}", taskId, action);
        // TODO:
        //   action=PASS  → 记录审批轨迹到 pmis_audit_log
        //   action=REJECT → 通知发起人（站内信 / 邮件）
    }

    @Override
    public void onInstanceCompleted(Long instanceId) {
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
        // TODO: initiationService.markApproved(instanceId)
        //        + wbsService.bootstrapFromInitiation(instanceId)
    }

    @Override
    public void onInstanceRejected(Long instanceId, String reason) {
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
        // TODO: initiationService.markRejected(instanceId, reason)
    }

    @Override
    public void onError(Long instanceId, Throwable t) {
        log.error("[FlowListener] 立项流程异常: instanceId={}", instanceId, t);
        // TODO: 告警 + 重试
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
    @Async
    public void onFlowWorkflowEvent(FlowWorkflowEvent event) {
        log.info("[FlowListener] 异步事件: type={} instanceId={} taskId={}",
                event.getEventType(), event.getInstanceId(), event.getTaskId());
        // 事件分发由 onTaskUrged/onInstanceTerminated 等具体 default 方法处理；
        // 这里保留异步通道，便于后续扩展（IM 推送、监控埋点等）。
    }

    // ============================== P0-1: 关键事件通知触发 ==============================

    @Override
    public void onTaskUrged(Long instanceId, Long taskId) {
        // P0-1: 催办通知：实例级催办推送给所有当前待办办理人
        if (instanceId == null) {
            return;
        }
        List<FlowTaskDO> pending = taskMapper.selectPendingByInstance(instanceId);
        List<Long> receivers = pending == null ? Collections.emptyList() : pending.stream()
                .map(t -> parseUserId(t.getAssigneeId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        String flowName = instance == null ? "" : nullSafe(instance.getFlowName());
        String title = "审批催办";
        String content = String.format("【%s】 您有一个待办任务被催办，请尽快处理", flowName);
        notificationHelper.notifyUrge(receivers, title, content, instanceId);
    }

    @Override
    public void onInstanceTerminated(Long instanceId, String reason) {
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
    public void onInstanceRecalled(Long instanceId, Long initiatorId) {
        // P0-1: 撤回通知：通知所有当前待办办理人
        if (instanceId == null) {
            return;
        }
        List<FlowTaskDO> pending = taskMapper.selectPendingByInstance(instanceId);
        List<Long> receivers = pending == null ? Collections.emptyList() : pending.stream()
                .map(t -> parseUserId(t.getAssigneeId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        String flowName = instance == null ? "" : nullSafe(instance.getFlowName());
        String title = "审批已撤回";
        String content = String.format("【%s】 该流程已被发起人撤回", flowName);
        notificationHelper.notifyInstanceRecalled(receivers, title, content, instanceId);
    }

    @Override
    public void onTaskTransferred(Long taskId, Long fromUserId, Long toUserId) {
        // P0-1: 转办通知：通知新办理人
        if (taskId == null || toUserId == null) {
            return;
        }
        FlowTaskDO task = taskMapper.selectById(taskId);
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
    public void onTaskDelegated(Long taskId, Long fromUserId, Long toUserId) {
        // P0-1: 委派通知：通知被委派人
        if (taskId == null || toUserId == null) {
            return;
        }
        FlowTaskDO task = taskMapper.selectById(taskId);
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
    public void onTaskTimeout(Long taskId, Long instanceId) {
        // P0-1: 超时通知：通知当前办理人
        if (taskId == null) {
            return;
        }
        FlowTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        Long assigneeId = parseUserId(task.getAssigneeId());
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

    /** 解析 assigneeId 字符串为 Long（可能为 user:/role:/dept: 前缀） */
    private static Long parseUserId(String assigneeId) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return null;
        }
        // 数字或纯数字字符串
        try {
            return Long.parseLong(assigneeId);
        } catch (NumberFormatException ignore) {
            // 形如 "user:1001" → 取冒号后部分
            int idx = assigneeId.lastIndexOf(':');
            if (idx >= 0 && idx < assigneeId.length() - 1) {
                try {
                    return Long.parseLong(assigneeId.substring(idx + 1));
                } catch (NumberFormatException ignore2) {
                    return null;
                }
            }
            return null;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
