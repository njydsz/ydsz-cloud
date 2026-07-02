package com.njydsz.pmis.workflow.flow.engine;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流 → 通知中心 适配器
 *
 * <p>把工作流关键事件（任务创建/催办/驳回/转办/委派/超时/完成/挂起/激活/撤回）转写为
 * 通知中心可消费的 payload 并通过 Feign 投递。任何 Feign 异常被 try-catch 吞掉，
 * 主流程事务不被拖垮。
 *
 * <p>P0-1: 站内信打通（对标钉钉/飞书审批的实时通知能力）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNotificationHelper {

    /** 通知 Feign 客户端，notification 模块不可用时由 FallbackFactory 兜底为 0 */
    private final NotificationClient notificationClient;

    /**
     * 任务待办通知：谁有新的待办需要处理
     *
     * @param receiverId  接收人（单个办理人）
     * @param title       通知标题（如 "您有一个新的审批待办"）
     * @param content     通知内容
     * @param taskId      任务 ID（bizId）
     * @param bizType     业务类型（WORKFLOW_TASK）
     * @param level       级别 INFO/WARN/ERROR/URGENT
     */
    public void notifyTaskAssigned(Long receiverId, String title, String content,
                                   Long taskId, String bizType, String level) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, level,
                "WORKFLOW", bizType, String.valueOf(taskId), receiverId, null);
        sendQuietly(payload, "TASK_ASSIGNED");
    }

    /**
     * 任务催办通知：被催办人收到提醒
     *
     * @param receiverIds 接收人列表
     * @param title       标题
     * @param content     内容
     * @param instanceId  流程实例 ID
     */
    public void notifyUrge(List<Long> receiverIds, String title, String content, Long instanceId) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "URGENT",
                "WORKFLOW", "WORKFLOW_URGE", String.valueOf(instanceId), null, receiverIds);
        sendQuietly(payload, "URGE");
    }

    /**
     * 流程完成通知：发起人收到结果
     */
    public void notifyInstanceCompleted(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "INFO",
                "WORKFLOW", "WORKFLOW_COMPLETED", String.valueOf(instanceId), receiverId, null);
        sendQuietly(payload, "INSTANCE_COMPLETED");
    }

    /**
     * 流程驳回通知：发起人收到驳回结果
     */
    public void notifyInstanceRejected(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "WARN",
                "WORKFLOW", "WORKFLOW_REJECTED", String.valueOf(instanceId), receiverId, null);
        sendQuietly(payload, "INSTANCE_REJECTED");
    }

    /**
     * 流程撤回通知：所有当前待办人收到撤回消息
     */
    public void notifyInstanceRecalled(List<Long> receiverIds, String title, String content, Long instanceId) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "WARN",
                "WORKFLOW", "WORKFLOW_RECALLED", String.valueOf(instanceId), null, receiverIds);
        sendQuietly(payload, "INSTANCE_RECALLED");
    }

    /**
     * 流程终止通知：发起人收到终止消息
     */
    public void notifyInstanceTerminated(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "WARN",
                "WORKFLOW", "WORKFLOW_TERMINATED", String.valueOf(instanceId), receiverId, null);
        sendQuietly(payload, "INSTANCE_TERMINATED");
    }

    /**
     * 任务转办通知：新办理人收到新待办
     */
    public void notifyTaskTransferred(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "INFO",
                "WORKFLOW", "WORKFLOW_TRANSFERRED", String.valueOf(taskId), receiverId, null);
        sendQuietly(payload, "TASK_TRANSFERRED");
    }

    /**
     * 任务委派通知：被委派人收到委派
     */
    public void notifyTaskDelegated(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "INFO",
                "WORKFLOW", "WORKFLOW_DELEGATED", String.valueOf(taskId), receiverId, null);
        sendQuietly(payload, "TASK_DELEGATED");
    }

    /**
     * 任务超时通知：办理人收到超时预警
     */
    public void notifyTaskTimeout(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        Map<String, Object> payload = buildPayload(title, content, "WARN",
                "WORKFLOW", "WORKFLOW_TIMEOUT", String.valueOf(taskId), receiverId, null);
        sendQuietly(payload, "TASK_TIMEOUT");
    }

    // ============================== 私有 ==============================

    private Map<String, Object> buildPayload(String title, String content, String level,
                                              String category, String bizType, String bizId,
                                              Long receiverId, List<Long> receiverIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("content", content);
        payload.put("level", level);
        payload.put("category", category);
        payload.put("bizType", bizType);
        payload.put("bizId", bizId);
        if (receiverId != null) {
            payload.put("receiverId", receiverId);
        }
        if (receiverIds != null && !receiverIds.isEmpty()) {
            payload.put("receiverIds", receiverIds);
        }
        String traceId = TraceIdUtil.getOrCreate();
        if (traceId != null) {
            payload.put("providerTraceId", traceId);
        }
        return payload;
    }

    /**
     * 静默发送：catch 住所有异常，避免拖垮主流程
     */
    private void sendQuietly(Map<String, Object> payload, String action) {
        try {
            Result<Integer> result = notificationClient.send(payload);
            if (result == null || !result.isSuccess()) {
                log.warn("[FlowNotify] 通知发送失败 action={} code={} msg={}",
                        action,
                        result == null ? "null" : result.getCode(),
                        result == null ? "null" : result.getMessage());
            } else {
                log.debug("[FlowNotify] 通知发送成功 action={} count={}", action, result.getData());
            }
        } catch (Exception e) {
            log.warn("[FlowNotify] 通知发送异常 action={} err={}", action, e.getMessage());
        }
    }
}
