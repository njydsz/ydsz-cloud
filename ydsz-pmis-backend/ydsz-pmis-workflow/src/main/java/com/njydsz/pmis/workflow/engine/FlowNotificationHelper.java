package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.service.FlowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流 -> 通知中心 适配器
 *
 * <p>把工作流关键事件（任务创建/催办/驳回/转办/委派/超时/完成/撤回/终止）转写为
 * 通知中心可消费的 payload 并通过 {@link FlowNotificationService} 投递。所有方法
 * 委托给 FlowNotificationService 统一处理通道分发（IN_APP/EMAIL/WEBHOOK），
 * 任何异常被 try-catch 吞掉，主流程事务不被拖垮。
 *
 * <p>P0-1: 站内信打通（对标钉钉/飞书审批的实时通知能力）。
 * <p>P2-重构: 统一委托 FlowNotificationService，消除双服务直接调用 Feign 的重复逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNotificationHelper {

    /** 默认通知通道：站内信 */
    private static final String CHANNEL_IN_APP = "IN_APP";

    /** 工作流通知服务，统一管理多通道投递（IN_APP/EMAIL/WEBHOOK） */
    private final FlowNotificationService notificationService;

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
        try {
            Map<String, Object> extra = buildExtra(bizType, level);
            extra.put("taskId", taskId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务待办通知异常 receiverId={} taskId={} err={}",
                    receiverId, taskId, e.getMessage());
        }
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
        for (Long receiverId : receiverIds) {
            try {
                Map<String, Object> extra = buildExtra("WORKFLOW_URGE", "URGENT");
                extra.put("instanceId", instanceId);
                notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
            } catch (Exception e) {
                log.warn("[FlowNotify] 催办通知异常 receiverId={} instanceId={} err={}",
                        receiverId, instanceId, e.getMessage());
            }
        }
    }

    /**
     * 流程完成通知：发起人收到结果
     */
    public void notifyInstanceCompleted(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_COMPLETED", "INFO");
            extra.put("instanceId", instanceId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程完成通知异常 receiverId={} instanceId={} err={}",
                    receiverId, instanceId, e.getMessage());
        }
    }

    /**
     * 流程驳回通知：发起人收到驳回结果
     */
    public void notifyInstanceRejected(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_REJECTED", "WARN");
            extra.put("instanceId", instanceId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程驳回通知异常 receiverId={} instanceId={} err={}",
                    receiverId, instanceId, e.getMessage());
        }
    }

    /**
     * 流程撤回通知：所有当前待办人收到撤回消息
     */
    public void notifyInstanceRecalled(List<Long> receiverIds, String title, String content, Long instanceId) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        for (Long receiverId : receiverIds) {
            try {
                Map<String, Object> extra = buildExtra("WORKFLOW_RECALLED", "WARN");
                extra.put("instanceId", instanceId);
                notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
            } catch (Exception e) {
                log.warn("[FlowNotify] 流程撤回通知异常 receiverId={} instanceId={} err={}",
                        receiverId, instanceId, e.getMessage());
            }
        }
    }

    /**
     * 流程终止通知：发起人收到终止消息
     */
    public void notifyInstanceTerminated(Long receiverId, String title, String content, Long instanceId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_TERMINATED", "WARN");
            extra.put("instanceId", instanceId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程终止通知异常 receiverId={} instanceId={} err={}",
                    receiverId, instanceId, e.getMessage());
        }
    }

    /**
     * 任务转办通知：新办理人收到新待办
     */
    public void notifyTaskTransferred(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_TRANSFERRED", "INFO");
            extra.put("taskId", taskId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务转办通知异常 receiverId={} taskId={} err={}",
                    receiverId, taskId, e.getMessage());
        }
    }

    /**
     * 任务委派通知：被委派人收到委派
     */
    public void notifyTaskDelegated(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_DELEGATED", "INFO");
            extra.put("taskId", taskId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务委派通知异常 receiverId={} taskId={} err={}",
                    receiverId, taskId, e.getMessage());
        }
    }

    /**
     * 任务超时通知：办理人收到超时预警
     */
    public void notifyTaskTimeout(Long receiverId, String title, String content, Long taskId) {
        if (receiverId == null) {
            return;
        }
        try {
            Map<String, Object> extra = buildExtra("WORKFLOW_TIMEOUT", "WARN");
            extra.put("taskId", taskId);
            notificationService.send(CHANNEL_IN_APP, receiverId, title, content, extra);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务超时通知异常 receiverId={} taskId={} err={}",
                    receiverId, taskId, e.getMessage());
        }
    }

    // ============================== 私有 ==============================

    /**
     * 构建扩展参数 Map（统一填充 category / bizType / level）
     *
     * @param bizType 业务类型
     * @param level   级别
     * @return 扩展参数 Map
     */
    private Map<String, Object> buildExtra(String bizType, String level) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("category", "WORKFLOW");
        extra.put("bizType", bizType);
        extra.put("level", level);
        return extra;
    }
}
