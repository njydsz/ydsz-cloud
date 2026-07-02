package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.workflow.service.FlowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 工作流消息通知服务 — 默认实现（日志占位）
 *
 * <p>当前版本为占位实现，所有通道均以日志输出代替真实投递：
 * <ul>
 *   <li>IN_APP  — 预留插入 pmis_message 表（暂仅日志）</li>
 *   <li>EMAIL   — 预留对接邮件网关（暂仅日志）</li>
 *   <li>WEBHOOK — 预留对接企业微信/钉钉机器人（暂仅日志）</li>
 * </ul>
 *
 * <p>所有方法均 try-catch 吞异常，保证不拖垮主流程事务。
 * 后续接入真实通道时替换对应 send 分支即可。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotificationServiceImpl implements FlowNotificationService {

    /** 通知通道常量 */
    private static final String CHANNEL_IN_APP = "IN_APP";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_WEBHOOK = "WEBHOOK";

    @Override
    public void notifyTaskCreated(Long instanceId, Long taskId, String assigneeId, String assigneeName) {
        try {
            if (assigneeId == null) {
                return;
            }
            String title = "您有一个新的审批待办";
            String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 需要您处理";
            Long userId = parseUserId(assigneeId);
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_TASK");
            extra.put("instanceId", instanceId);
            extra.put("taskId", taskId);
            extra.put("assigneeName", assigneeName);
            send(CHANNEL_IN_APP, userId, title, content, extra);
            log.info("[FlowNotify] 任务创建通知: instanceId={} taskId={} assigneeId={}",
                    instanceId, taskId, assigneeId);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务创建通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void notifyUrge(Long instanceId, Long taskId, List<String> assigneeIds, String comment) {
        try {
            if (assigneeIds == null || assigneeIds.isEmpty()) {
                return;
            }
            String title = "您有待办被催办";
            String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 被催办";
            if (comment != null && !comment.isBlank()) {
                content += "，备注：" + comment;
            }
            for (String assigneeId : assigneeIds) {
                Long userId = parseUserId(assigneeId);
                Map<String, Object> extra = new HashMap<>();
                extra.put("bizType", "WORKFLOW_URGE");
                extra.put("instanceId", instanceId);
                extra.put("taskId", taskId);
                extra.put("comment", comment);
                send(CHANNEL_IN_APP, userId, title, content, extra);
            }
            log.info("[FlowNotify] 催办通知: instanceId={} taskId={} targets={}",
                    instanceId, taskId, assigneeIds.size());
        } catch (Exception e) {
            log.warn("[FlowNotify] 催办通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void notifyCc(Long instanceId, String nodeCode, List<Long> ccUserIds, String title) {
        try {
            if (ccUserIds == null || ccUserIds.isEmpty()) {
                return;
            }
            String content = "流程实例[" + instanceId + "] 节点[" + nodeCode + "] 抄送给您";
            for (Long userId : ccUserIds) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("bizType", "WORKFLOW_CC");
                extra.put("instanceId", instanceId);
                extra.put("nodeCode", nodeCode);
                send(CHANNEL_IN_APP, userId, title, content, extra);
            }
            log.info("[FlowNotify] 抄送通知: instanceId={} nodeCode={} targets={}",
                    instanceId, nodeCode, ccUserIds.size());
        } catch (Exception e) {
            log.warn("[FlowNotify] 抄送通知异常: instanceId={} nodeCode={} err={}",
                    instanceId, nodeCode, e.getMessage());
        }
    }

    @Override
    public void notifyInstanceCompleted(Long instanceId, Long initiatorId) {
        try {
            if (initiatorId == null) {
                return;
            }
            String title = "您的审批流程已完成";
            String content = "流程实例[" + instanceId + "] 已审批通过";
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_COMPLETED");
            extra.put("instanceId", instanceId);
            send(CHANNEL_IN_APP, initiatorId, title, content, extra);
            log.info("[FlowNotify] 流程完成通知: instanceId={} initiatorId={}",
                    instanceId, initiatorId);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程完成通知异常: instanceId={} initiatorId={} err={}",
                    instanceId, initiatorId, e.getMessage());
        }
    }

    @Override
    public void notifyInstanceRejected(Long instanceId, Long initiatorId, String reason) {
        try {
            if (initiatorId == null) {
                return;
            }
            String title = "您的审批流程被驳回";
            String content = "流程实例[" + instanceId + "] 被驳回";
            if (reason != null && !reason.isBlank()) {
                content += "，原因：" + reason;
            }
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_REJECTED");
            extra.put("instanceId", instanceId);
            extra.put("reason", reason);
            send(CHANNEL_IN_APP, initiatorId, title, content, extra);
            log.info("[FlowNotify] 流程驳回通知: instanceId={} initiatorId={} reason={}",
                    instanceId, initiatorId, reason);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程驳回通知异常: instanceId={} initiatorId={} err={}",
                    instanceId, initiatorId, e.getMessage());
        }
    }

    @Override
    public void notifySlaTimeout(Long instanceId, Long taskId, String assigneeId, String action) {
        try {
            if (assigneeId == null) {
                return;
            }
            String title = "审批任务已超时";
            String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 超时，触发动作：" + action;
            Long userId = parseUserId(assigneeId);
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_SLA_TIMEOUT");
            extra.put("instanceId", instanceId);
            extra.put("taskId", taskId);
            extra.put("action", action);
            // SLA 超时同时走站内信 + 邮件
            send(CHANNEL_IN_APP, userId, title, content, extra);
            send(CHANNEL_EMAIL, userId, title, content, extra);
            log.info("[FlowNotify] SLA 超时通知: instanceId={} taskId={} assigneeId={} action={}",
                    instanceId, taskId, assigneeId, action);
        } catch (Exception e) {
            log.warn("[FlowNotify] SLA 超时通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void send(String channel, Long userId, String title, String content, Map<String, Object> extra) {
        try {
            if (channel == null || userId == null) {
                return;
            }
            String traceId = TraceIdUtil.getOrCreate();

            switch (channel) {
                case CHANNEL_IN_APP:
                    // GAP-P1 占位：预留插入 pmis_message 表
                    // MessageDO msg = new MessageDO(); msg.setUserId(userId); ...
                    log.info("[FlowNotify][IN_APP] userId={} title={} content={} traceId={} extra={}",
                            userId, title, content, traceId, extra);
                    break;
                case CHANNEL_EMAIL:
                    // GAP-P1 占位：预留对接邮件网关
                    // mailClient.send(userId + "@company.com", title, content);
                    log.info("[FlowNotify][EMAIL] userId={} title={} content={} traceId={}",
                            userId, title, content, traceId);
                    break;
                case CHANNEL_WEBHOOK:
                    // GAP-P1 占位：预留对接企业微信/钉钉机器人
                    // webhookClient.push(webhookUrl, payload);
                    log.info("[FlowNotify][WEBHOOK] userId={} title={} content={} traceId={}",
                            userId, title, content, traceId);
                    break;
                default:
                    log.warn("[FlowNotify] 未知通知通道: channel={} userId={} title={}",
                            channel, userId, title);
            }
        } catch (Exception e) {
            log.warn("[FlowNotify] 通知发送异常: channel={} userId={} err={}",
                    channel, userId, e.getMessage());
        }
    }

    /**
     * 将字符串形式的 assigneeId 安全解析为 Long
     *
     * @param assigneeId 办理人 ID（字符串）
     * @return Long 值，解析失败返回 null
     */
    private Long parseUserId(String assigneeId) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(assigneeId.trim());
        } catch (NumberFormatException e) {
            log.warn("[FlowNotify] assigneeId 无法解析为 Long: {}", assigneeId);
            return null;
        }
    }
}
