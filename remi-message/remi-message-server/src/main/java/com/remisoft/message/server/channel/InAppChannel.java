package com.remisoft.message.server.channel.impl;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.remisoft.common.feign.MessageRequest;
import com.remisoft.common.feign.MessageResult;
import com.remisoft.common.util.id.SnowflakeUtils;
import com.remisoft.message.domain.dto.core.NotificationSendDTO;
import com.remisoft.message.server.channel.MessageChannel;
import com.remisoft.message.server.service.core.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 站内信通道实现。
 *
 * <p>P0-4 修复：不再返回空壳成功结果，而是调用 {@link NotificationService#send}
 * 将通知落库到 {@code remi_msg_notification} 表，确保站内信真正可被用户看到。
 *
 * <p>支持从 {@link MessageRequest} 中提取：
 * <ul>
 *   <li>{@code receiver} → 接收人 ID</li>
 *   <li>{@code subject} → 通知标题</li>
 *   <li>{@code content} → 通知内容</li>
 *   <li>{@code bizType} → 通知分类</li>
 *   <li>{@code params.level} → 通知级别（INFO/WARN/ERROR/URGENT）</li>
 *   <li>{@code params.actionUrl} → 点击跳转 URL</li>
 *   <li>{@code params.actionText} → 跳转按钮文案</li>
 *   <li>{@code params.icon} → 通知图标</li>
 *   <li>{@code params.sourceModule} → 来源模块</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "INAPP";

    private final NotificationService notificationService;

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "站内信接收人不能为空");
        }
        String traceId = "INAPP-" + SnowflakeUtils.nextIdStr();
        try {
            NotificationSendDTO dto = buildNotificationDTO(request);
            int count = notificationService.send(dto);
            if (count <= 0) {
                log.warn("[INAPP] 站内信发送未落库: receiver={} bizType={}",
                        request.getReceiver(), request.getBizType());
                return MessageResult.fail(CHANNEL_TYPE, "站内信发送失败：未落库");
            }
            log.info("[INAPP] 站内信发送成功: receiver={} bizType={} count={} traceId={}",
                    request.getReceiver(), request.getBizType(), count, traceId);
            return MessageResult.ok(CHANNEL_TYPE, traceId);
        } catch (Exception e) {
            log.error("[INAPP] 站内信发送异常: receiver={} err={}",
                    request.getReceiver(), e.getMessage());
            return MessageResult.fail(CHANNEL_TYPE, "站内信发送异常: " + e.getMessage());
        }
    }

    /**
     * 从 {@link MessageRequest} 构建 {@link NotificationSendDTO}。
     *
     * @param request 消息请求
     * @return 通知发送 DTO
     */
    private NotificationSendDTO buildNotificationDTO(MessageRequest request) {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverId(request.getReceiver());
        dto.setTitle(request.getSubject());
        dto.setContent(request.getContent());
        dto.setBizType(request.getBizType());
        dto.setBizId(request.getBizId());
        dto.setSenderId("SYSTEM");
        // 从 params 中提取扩展字段
        Map<String, Object> params = request.getParams();
        if (params != null) {
            dto.setLevel(getStringParam(params, "level", "INFO"));
            dto.setCategory(getStringParam(params, "category", request.getBizType()));
            dto.setActionUrl(getStringParam(params, "actionUrl", null));
            dto.setActionText(getStringParam(params, "actionText", null));
            dto.setIcon(getStringParam(params, "icon", null));
            dto.setSourceModule(getStringParam(params, "sourceModule", null));
            dto.setMessageGroup(getStringParam(params, "messageGroup", null));
        }
        dto.setPriority(request.getPriority());
        return dto;
    }

    /**
     * 从 params Map 中安全提取字符串值。
     *
     * @param params       参数 Map
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数值或默认值
     */
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
