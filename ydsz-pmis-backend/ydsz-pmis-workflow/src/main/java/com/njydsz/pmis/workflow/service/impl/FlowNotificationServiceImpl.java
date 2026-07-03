package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.workflow.service.FlowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GAP-V2-03: 工作流消息通知服务 — 真实通道落地实现
 *
 * <p>三个通道均已对接真实投递，统一遵循"尽力而为"语义（异常 try-catch 吞掉，不拖垮主流程事务）：
 * <ul>
 *   <li>IN_APP  — 通过 NotificationClient Feign 调用 notification 服务写入站内信（channel=PUSH）</li>
 *   <li>EMAIL   — 同样通过 NotificationClient 投递（channel=EMAIL），由 notification 服务负责实际邮件发送</li>
 *   <li>WEBHOOK — 通过 RestTemplate POST 发送到 extra.webhookUrl 指定的企业微信/钉钉机器人</li>
 * </ul>
 *
 * <p>NotificationClient 已配置 fallbackFactory，notification 模块不可用时自动降级；
 * 各通道再用 try-catch 包裹，确保 Feign/网络异常均不影响主流程。
 * 成功路径日志降为 debug（避免洪泛），异常仍用 warn。
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

    /** Feign 通知客户端（IN_APP / EMAIL 通道），由 @RequiredArgsConstructor 注入 */
    private final NotificationClient notificationClient;

    /**
     * WEBHOOK 通道使用的 RestTemplate。
     *
     * <p>不通过构造器/字段注入，避免强制要求容器中存在 RestTemplate Bean。
     * 此处直接 new 出默认实例即可满足 best-effort 投递需求；
     * final + 内联初始化使 Lombok @RequiredArgsConstructor 跳过该字段。
     */
    private final RestTemplate restTemplate = new RestTemplate();

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
            log.debug("[FlowNotify] 任务创建通知: instanceId={} taskId={} assigneeId={}",
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
            log.debug("[FlowNotify] 催办通知: instanceId={} taskId={} targets={}",
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
            log.debug("[FlowNotify] 抄送通知: instanceId={} nodeCode={} targets={}",
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
            log.debug("[FlowNotify] 流程完成通知: instanceId={} initiatorId={}",
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
            log.debug("[FlowNotify] 流程驳回通知: instanceId={} initiatorId={} reason={}",
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
            log.debug("[FlowNotify] SLA 超时通知: instanceId={} taskId={} assigneeId={} action={}",
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
            Object bizType = extra == null ? null : extra.get("bizType");

            switch (channel) {
                case CHANNEL_IN_APP -> sendInApp(userId, title, content, bizType, extra, traceId);
                case CHANNEL_EMAIL -> sendEmail(userId, title, content, bizType, extra, traceId);
                case CHANNEL_WEBHOOK -> sendWebhook(userId, title, content, extra, traceId);
                default -> log.warn("[FlowNotify] 未知通知通道: channel={} userId={} title={}",
                        channel, userId, title);
            }
        } catch (Exception e) {
            log.warn("[FlowNotify] 通知发送异常: channel={} userId={} err={}",
                    channel, userId, e.getMessage());
        }
    }

    /**
     * IN_APP 通道：通过 NotificationClient Feign 调用 notification 服务写入站内信。
     * channel=PUSH，Feign 异常由 fallbackFactory 兜底，再叠加 try-catch 双保险。
     */
    private void sendInApp(Long userId, String title, String content,
                           Object bizType, Map<String, Object> extra, String traceId) {
        Map<String, Object> payload = new HashMap<>();
        if (extra != null) {
            payload.putAll(extra);
        }
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("bizType", bizType);
        payload.put("channel", "PUSH");
        try {
            notificationClient.send(payload);
        } catch (Exception e) {
            // fallbackFactory 已兜底，此处再 catch 防御非 Feign 异常，降级为日志
            log.warn("[FlowNotify][IN_APP] Feign 调用降级为日志: userId={} title={} err={}",
                    userId, title, e.getMessage());
        }
        log.debug("[FlowNotify][IN_APP] userId={} title={} content={} traceId={}",
                userId, title, content, traceId);
    }

    /**
     * EMAIL 通道：同样通过 NotificationClient 投递（channel=EMAIL），
     * 由 notification 服务负责实际邮件发送。receiver 优先取自 extra，
     * 未配置时不设占位邮箱，由 notification 服务按 userId 查询真实邮箱（P0-2 修复）。
     */
    private void sendEmail(Long userId, String title, String content,
                           Object bizType, Map<String, Object> extra, String traceId) {
        Map<String, Object> payload = new HashMap<>();
        if (extra != null) {
            payload.putAll(extra);
        }
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("bizType", bizType);
        payload.put("channel", "EMAIL");
        // P0-2 修复：receiver 优先从 extra 读取，未配置时不拼占位邮箱，由 notification 服务按 userId 查询
        Object receiver = extra == null ? null : extra.get("receiver");
        if (receiver != null) {
            payload.put("receiver", receiver);
        }
        try {
            notificationClient.send(payload);
        } catch (Exception e) {
            log.warn("[FlowNotify][EMAIL] Feign 调用降级为日志: userId={} title={} err={}",
                    userId, title, e.getMessage());
        }
        log.debug("[FlowNotify][EMAIL] userId={} title={} content={} traceId={}",
                userId, title, content, traceId);
    }

    /**
     * WEBHOOK 通道：通过 RestTemplate POST 发送到 extra.webhookUrl 指定的机器人地址。
     * webhookUrl 未配置时直接跳过（不算异常）。
     */
    private void sendWebhook(Long userId, String title, String content,
                             Map<String, Object> extra, String traceId) {
        String webhookUrl = extra == null ? null : (String) extra.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[FlowNotify][WEBHOOK] 未配置 webhookUrl，跳过: userId={} title={}", userId, title);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("channel", "WEBHOOK");
        payload.put("traceId", traceId);
        if (extra != null) {
            payload.putAll(extra);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(JSON.toJSONString(payload), headers);
            restTemplate.postForEntity(webhookUrl, request, String.class);
        } catch (Exception e) {
            log.warn("[FlowNotify][WEBHOOK] 发送失败: url={} userId={} err={}",
                    webhookUrl, userId, e.getMessage());
        }
        log.debug("[FlowNotify][WEBHOOK] userId={} title={} traceId={} url={}",
                userId, title, traceId, webhookUrl);
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
