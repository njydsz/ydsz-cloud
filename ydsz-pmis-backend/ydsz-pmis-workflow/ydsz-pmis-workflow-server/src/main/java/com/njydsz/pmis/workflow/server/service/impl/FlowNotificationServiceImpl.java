package com.njydsz.pmis.workflow.server.service.impl.notification;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.feign.MessageServiceClient;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.workflow.server.service.notification.FlowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流消息通知服务实现 — 轻量适配器
 *
 * <p>通知基础设施（outbox/template/channel/preference）已移除，通知能力由独立的
 * 消息通知引擎 {@code ydsz-pmis-message} 承载。本类仅作为 Feign 适配器，将工作流
 * 关键事件转发到 {@link NotificationClient}，遵循"尽力而为"语义（异常 try-catch
 * 吞掉，不拖垮主流程事务）。
 *
 * <p>通道说明：
 * <ul>
 *   <li>INAPP  — 通过 NotificationClient Feign 调用 notification 服务写入站内信（channel=PUSH）</li>
 *   <li>EMAIL   — 同样通过 NotificationClient 投递（channel=EMAIL），由 notification 服务负责实际邮件发送</li>
 *   <li>WEBHOOK — 通过 {@link MessageServiceClient} 委托消息中心发送到 extra.webhookUrl 指定的企业微信/钉钉机器人</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotificationServiceImpl implements FlowNotificationService {

    /** 通知通道常量 */
    private static final String CHANNEL_INAPP = "INAPP";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_WEBHOOK = "WEBHOOK";

    /** Feign 通知客户端（INAPP / EMAIL 通道），由 @RequiredArgsConstructor 注入 */
    private final NotificationClient notificationClient;

    /** 消息中心客户端（WEBHOOK 通道），由 @RequiredArgsConstructor 注入 */
    private final MessageServiceClient messageServiceClient;

    @Override
    public void notifyTaskCreated(String instanceId, String taskId, String assigneeId, String assigneeName) {
        try {
            if (assigneeId == null) {
                return;
            }
            String title = "您有一个新的审批待办";
            String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 需要您处理";
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_TASK");
            extra.put("instanceId", instanceId);
            extra.put("taskId", taskId);
            extra.put("assigneeName", assigneeName);
            send(CHANNEL_INAPP, assigneeId, title, content, extra);
            log.debug("[FlowNotify] 任务创建通知: instanceId={} taskId={} assigneeId={}",
                    instanceId, taskId, assigneeId);
        } catch (Exception e) {
            log.warn("[FlowNotify] 任务创建通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void notifyUrge(String instanceId, String taskId, List<String> assigneeIds, String comment) {
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
                Map<String, Object> extra = new HashMap<>();
                extra.put("bizType", "WORKFLOW_URGE");
                extra.put("instanceId", instanceId);
                extra.put("taskId", taskId);
                extra.put("comment", comment);
                send(CHANNEL_INAPP, assigneeId, title, content, extra);
            }
            log.debug("[FlowNotify] 催办通知: instanceId={} taskId={} targets={}",
                    instanceId, taskId, assigneeIds.size());
        } catch (Exception e) {
            log.warn("[FlowNotify] 催办通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void notifyCc(String instanceId, String nodeCode, List<Long> ccUserIds, String title) {
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
                send(CHANNEL_INAPP, String.valueOf(userId), title, content, extra);
            }
            log.debug("[FlowNotify] 抄送通知: instanceId={} nodeCode={} targets={}",
                    instanceId, nodeCode, ccUserIds.size());
        } catch (Exception e) {
            log.warn("[FlowNotify] 抄送通知异常: instanceId={} nodeCode={} err={}",
                    instanceId, nodeCode, e.getMessage());
        }
    }

    @Override
    public void notifyInstanceCompleted(String instanceId, String initiatorId) {
        try {
            if (initiatorId == null) {
                return;
            }
            String title = "您的审批流程已完成";
            String content = "流程实例[" + instanceId + "] 已审批通过";
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_COMPLETED");
            extra.put("instanceId", instanceId);
            send(CHANNEL_INAPP, initiatorId, title, content, extra);
            log.debug("[FlowNotify] 流程完成通知: instanceId={} initiatorId={}",
                    instanceId, initiatorId);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程完成通知异常: instanceId={} initiatorId={} err={}",
                    instanceId, initiatorId, e.getMessage());
        }
    }

    @Override
    public void notifyInstanceRejected(String instanceId, String initiatorId, String reason) {
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
            send(CHANNEL_INAPP, initiatorId, title, content, extra);
            log.debug("[FlowNotify] 流程驳回通知: instanceId={} initiatorId={} reason={}",
                    instanceId, initiatorId, reason);
        } catch (Exception e) {
            log.warn("[FlowNotify] 流程驳回通知异常: instanceId={} initiatorId={} err={}",
                    instanceId, initiatorId, e.getMessage());
        }
    }

    @Override
    public void notifySlaTimeout(String instanceId, String taskId, String assigneeId, String action) {
        try {
            if (assigneeId == null) {
                return;
            }
            String title = "审批任务已超时";
            String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 超时，触发动作：" + action;
            Map<String, Object> extra = new HashMap<>();
            extra.put("bizType", "WORKFLOW_SLA_TIMEOUT");
            extra.put("instanceId", instanceId);
            extra.put("taskId", taskId);
            extra.put("action", action);
            // SLA 超时同时走站内信 + 邮件
            send(CHANNEL_INAPP, assigneeId, title, content, extra);
            send(CHANNEL_EMAIL, assigneeId, title, content, extra);
            log.debug("[FlowNotify] SLA 超时通知: instanceId={} taskId={} assigneeId={} action={}",
                    instanceId, taskId, assigneeId, action);
        } catch (Exception e) {
            log.warn("[FlowNotify] SLA 超时通知异常: instanceId={} taskId={} err={}",
                    instanceId, taskId, e.getMessage());
        }
    }

    @Override
    public void send(String channel, String userId, String title, String content, Map<String, Object> extra) {
        try {
            if (channel == null || userId == null) {
                return;
            }
            switch (channel) {
                case CHANNEL_INAPP -> sendInApp(userId, title, content, extra);
                case CHANNEL_EMAIL -> sendEmail(userId, title, content, extra);
                case CHANNEL_WEBHOOK -> sendWebhook(userId, title, content, extra);
                default -> log.warn("[FlowNotify] 未知通知通道: channel={} userId={} title={}",
                        channel, userId, title);
            }
        } catch (Exception e) {
            log.warn("[FlowNotify] 通知发送异常: channel={} userId={} err={}",
                    channel, userId, e.getMessage());
        }
    }

    /**
     * INAPP 通道：通过 NotificationClient Feign 调用 notification 服务写入站内信。
     */
    private void sendInApp(String userId, String title, String content, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        if (extra != null) {
            payload.putAll(extra);
        }
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("channel", "PUSH");
        try {
            notificationClient.send(toFeignDTO(payload));
        } catch (Exception e) {
            log.warn("[FlowNotify][INAPP] Feign 调用降级为日志: userId={} title={} err={}",
                    userId, title, e.getMessage());
        }
        log.debug("[FlowNotify][INAPP] userId={} title={}", userId, title);
    }

    /**
     * EMAIL 通道：同样通过 NotificationClient 投递（channel=EMAIL），
     * 由 notification 服务负责实际邮件发送。
     */
    private void sendEmail(String userId, String title, String content, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        if (extra != null) {
            payload.putAll(extra);
        }
        payload.put("userId", userId);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("channel", "EMAIL");
        Object receiver = extra == null ? null : extra.get("receiver");
        if (receiver != null) {
            payload.put("receiver", receiver);
        }
        try {
            notificationClient.send(toFeignDTO(payload));
        } catch (Exception e) {
            log.warn("[FlowNotify][EMAIL] Feign 调用降级为日志: userId={} title={} err={}",
                    userId, title, e.getMessage());
        }
        log.debug("[FlowNotify][EMAIL] userId={} title={}", userId, title);
    }

    /**
     * WEBHOOK 通道：通过 {@link MessageServiceClient} 委托消息中心发送到 extra.webhookUrl 指定的机器人地址。
     * webhookUrl 未配置时直接跳过（不算异常）。
     */
    private void sendWebhook(String userId, String title, String content, Map<String, Object> extra) {
        String webhookUrl = extra == null ? null : (String) extra.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[FlowNotify][WEBHOOK] 未配置 webhookUrl，跳过: userId={} title={}", userId, title);
            return;
        }
        MessageRequest request = new MessageRequest();
        request.setChannel("WEBHOOK");
        request.setReceiver(userId);
        request.setSubject(title);
        request.setContent(content);
        request.setBizType(extra == null ? null : asString(extra.get("bizType")));
        request.setBizId(extra == null ? null : asString(extra.get("bizId")));
        Map<String, Object> params = new HashMap<>();
        if (extra != null) {
            params.putAll(extra);
        }
        params.put("webhookUrl", webhookUrl);
        request.setParams(params);
        try {
            BaseResponse<MessageResult> result = messageServiceClient.send(request);
            if (result != null && BaseResponse.getData() != null && !BaseResponse.getData().isSuccess()) {
                log.warn("[FlowNotify][WEBHOOK] 发送失败: userId={} url={} err={}",
                        userId, webhookUrl, BaseResponse.getData().getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("[FlowNotify][WEBHOOK] 发送异常: userId={} url={} err={}",
                    userId, webhookUrl, e.getMessage());
        }
        log.debug("[FlowNotify][WEBHOOK] userId={} title={} url={}", userId, title, webhookUrl);
    }

    /**
     * 将 Map 形式的 payload 转换为强类型 NotificationFeignDTO
     */
    private NotificationFeignDTO toFeignDTO(Map<String, Object> payload) {
        NotificationFeignDTO dto = new NotificationFeignDTO();
        if (payload == null) {
            return dto;
        }
        dto.setTitle(asString(payload.get("title")));
        dto.setContent(asString(payload.get("content")));
        dto.setLevel(asString(payload.get("level")));
        dto.setCategory(asString(payload.get("category")));
        dto.setSenderId(asString(payload.get("senderId")));
        dto.setReceiverId(asString(payload.get("receiverId")));
        if (dto.getReceiverId() == null) {
            dto.setReceiverId(asString(payload.get("userId")));
        }
        Object receiverIds = payload.get("receiverIds");
        if (receiverIds instanceof List<?> list) {
            List<Long> ids = new ArrayList<>(list.size());
            for (Object o : list) {
                Long id = asLong(o);
                if (id != null) {
                    ids.add(id);
                }
            }
            dto.setReceiverIds(ids);
        }
        dto.setBizType(asString(payload.get("bizType")));
        dto.setBizId(asString(payload.get("bizId")));
        Object expiredAt = payload.get("expiredAt");
        if (expiredAt instanceof LocalDateTime ldt) {
            dto.setExpiredAt(ldt);
        }
        Object emailEnabled = payload.get("emailEnabled");
        if (emailEnabled instanceof Boolean b) {
            dto.setEmailEnabled(b);
        }
        dto.setReceiverEmail(asString(payload.get("receiverEmail")));
        if (dto.getReceiverEmail() == null) {
            dto.setReceiverEmail(asString(payload.get("receiver")));
        }
        return dto;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("[FlowNotificationServiceImpl] Long 解析失败 o={}: {}", o, e.getMessage());
            return null;
        }
    }
}
