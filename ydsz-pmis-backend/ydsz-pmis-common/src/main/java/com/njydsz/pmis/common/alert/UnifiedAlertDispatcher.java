package com.njydsz.pmis.common.alert;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.api.dto.MessageRequest;
import com.njydsz.pmis.message.api.dto.MessageResult;
import com.njydsz.pmis.message.api.client.MessageServiceClient;
import com.njydsz.pmis.message.api.client.NotificationClient;
import com.njydsz.pmis.message.api.dto.RealtimePushDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一告警分发器 — 消费 {@link UnifiedAlertEvent} 并委托到 message 模块发送。
 *
 * <p>所有模块的告警事件经过此分发器统一处理：
 * <ol>
 *   <li><b>角色解析</b>：根据 alertLevel 自动解析目标角色（YELLOW→PM/PMO，RED→PMO/GM/CFO）</li>
 *   <li><b>通道路由</b>：根据 alertLevel 自动解析推送渠道（RED→INAPP+EMAIL，YELLOW→INAPP）</li>
 *   <li><b>消息发送</b>：通过 {@link MessageServiceClient} Feign 调用 message 模块</li>
 *   <li><b>实时广播</b>：通过 {@link NotificationClient} Feign 广播告警到前端 WebSocket</li>
 * </ol>
 *
 * <p>使用 {@code @Async} 异步执行，避免阻塞业务主流程。
 * 任何异常被 try-catch 吞掉，保证不影响调用方事务。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedAlertDispatcher {

    private final MessageServiceClient messageServiceClient;
    private final NotificationClient notificationClient;

    /** 默认渠道映射 */
    private static final Map<String, String[]> CHANNEL_MAP = Map.of(
            "RED", new String[]{"INAPP", "EMAIL"},
            "YELLOW", new String[]{"INAPP"},
            "NORMAL", new String[]{"INAPP"},
            "INFO", new String[]{"INAPP"}
    );

    /** 默认角色映射 */
    private static final Map<String, String[]> ROLE_MAP = Map.of(
            "RED", new String[]{"PMO", "GM", "CFO"},
            "YELLOW", new String[]{"PM", "PMO"},
            "NORMAL", new String[]{"PM"},
            "INFO", new String[]{"PM"}
    );

    /**
     * 消费统一告警事件，异步分发到 message 模块。
     *
     * @param event 告警事件
     */
    @Async
    @EventListener
    public void onAlertEvent(UnifiedAlertEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.error("[UnifiedAlertDispatcher] 告警分发异常: code={} type={} level={} reason={}",
                    event.getAlertCode(), event.getAlertType(), event.getAlertLevel(), e.getMessage(), e);
        }
    }

    /**
     * 同步分发告警（便于单元测试和需要确认结果的调用方）。
     *
     * @param event 告警事件
     */
    public void dispatch(UnifiedAlertEvent event) {
        if (event == null) {
            return;
        }

        // 1. 解析推送渠道
        String[] channels = resolveChannels(event);
        // 2. 解析目标角色
        String targetRole = resolveTargetRole(event);
        // 3. 逐通道发送
        for (String channel : channels) {
            try {
                sendViaMessageCenter(event, channel, targetRole);
            } catch (Exception e) {
                log.warn("[UnifiedAlertDispatcher] 通道 {} 推送失败: code={} err={}",
                        channel, event.getAlertCode(), e.getMessage());
            }
        }
        // 4. 实时广播到前端
        broadcastAlert(event);
    }

    /**
     * 通过 MessageServiceClient Feign 发送告警消息。
     */
    private void sendViaMessageCenter(UnifiedAlertEvent event, String channel, String targetRole) {
        MessageRequest req = new MessageRequest();
        req.setChannel(channel);
        req.setBizType("ALERT");
        req.setBizId(event.getSourceId());
        req.setTemplateCode(buildTemplateCode(event));
        req.setSubject(event.getTitle());
        req.setContent(event.getContent());

        Map<String, Object> params = new HashMap<>();
        params.put("alertCode", event.getAlertCode());
        params.put("alertType", event.getAlertType());
        params.put("alertLevel", event.getAlertLevel());
        params.put("title", event.getTitle());
        params.put("content", event.getContent());
        params.put("targetRole", targetRole);
        params.put("sourceModule", event.getSourceModule());
        params.put("sourceId", event.getSourceId());
        params.put("recovery", event.isRecovery());
        req.setParams(params);

        // 接收人：优先使用 targetUserIds，其次 targetRole
        String receiver = event.getTargetUserIds() != null
                ? event.getTargetUserIds()
                : targetRole;
        req.setReceiver(receiver);

        try {
            Result<MessageResult> r = messageServiceClient.send(req);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                log.warn("[UnifiedAlertDispatcher] Feign 返回失败: channel={} code={} r={}",
                        channel, event.getAlertCode(), r == null ? "null" : r.getCode());
            }
        } catch (Exception e) {
            log.warn("[UnifiedAlertDispatcher] Feign 调用异常: channel={} code={} err={}",
                    channel, event.getAlertCode(), e.getMessage());
        }
    }

    /**
     * 通过 NotificationClient Feign 实时广播告警到前端。
     */
    private void broadcastAlert(UnifiedAlertEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertCode", event.getAlertCode());
            payload.put("alertType", event.getAlertType());
            payload.put("alertLevel", event.getAlertLevel());
            payload.put("title", event.getTitle());
            payload.put("content", event.getContent());
            payload.put("sourceModule", event.getSourceModule());
            payload.put("recovery", event.isRecovery());
            RealtimePushDTO pushDTO = new RealtimePushDTO(payload);
            notificationClient.broadcast("ALERT", pushDTO);
        } catch (Exception e) {
            log.warn("[UnifiedAlertDispatcher] 实时推送降级忽略: code={} err={}",
                    event.getAlertCode(), e.getMessage());
        }
    }

    /**
     * 解析推送渠道列表。
     */
    private String[] resolveChannels(UnifiedAlertEvent event) {
        if (event.getPushChannels() != null && !event.getPushChannels().isBlank()) {
            return Arrays.stream(event.getPushChannels().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .toArray(String[]::new);
        }
        String level = event.getAlertLevel() == null ? "NORMAL" : event.getAlertLevel().toUpperCase();
        return CHANNEL_MAP.getOrDefault(level, CHANNEL_MAP.get("NORMAL"));
    }

    /**
     * 解析目标角色。
     */
    private String resolveTargetRole(UnifiedAlertEvent event) {
        if (event.getTargetRole() != null && !event.getTargetRole().isBlank()) {
            return event.getTargetRole();
        }
        String level = event.getAlertLevel() == null ? "NORMAL" : event.getAlertLevel().toUpperCase();
        String[] roles = ROLE_MAP.getOrDefault(level, ROLE_MAP.get("NORMAL"));
        return String.join(",", roles);
    }

    /**
     * 构建模板编码: ALERT_{TYPE}_{LEVEL}
     */
    private String buildTemplateCode(UnifiedAlertEvent event) {
        String type = event.getAlertType() == null ? "OTHER" : event.getAlertType().toUpperCase();
        String level = event.getAlertLevel() == null ? "NORMAL" : event.getAlertLevel().toUpperCase();
        return "ALERT_" + type + "_" + level;
    }
}
