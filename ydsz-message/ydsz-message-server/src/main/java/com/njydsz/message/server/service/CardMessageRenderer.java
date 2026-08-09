package com.njydsz.message.server.service.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.domain.dto.core.CardMessageDTO;

/**
 * 卡片消息渲染器。
 * <p>渲染 IM 卡片/Adaptive Card/Markdown Card 消息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Component
public class CardMessageRenderer {

    /**
     * 将卡片 DTO 转换为 MessageRequest。
     *
     * @param card     卡片 DTO
     * @param channel  目标通道
     * @param receiver 接收人
     * @param bizType  业务类型
     * @param bizId    业务 ID
     * @return MessageRequest
     */
    public MessageRequest toMessageRequest(CardMessageDTO card, String channel,
                                           String receiver, String bizType, String bizId) {
        MessageRequest request = new MessageRequest();
        request.setChannel(channel);
        request.setReceiver(receiver);
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setSubject(card.getTitle());
        request.setContent(card.getContent());

        Map<String, Object> params = new HashMap<>();
        String upperChannel = channel == null ? "" : channel.toUpperCase();

        switch (upperChannel) {
            case "DINGTALK", "DINGTALK_WORK" -> {
                params.put("msgType", "action_card");
                if (card.getButtons() != null && !card.getButtons().isEmpty()) {
                    params.put("actionUrl", card.getButtons().get(0).getUrl());
                } else if (StringUtils.hasText(card.getActionUrl())) {
                    params.put("actionUrl", card.getActionUrl());
                }
            }
            case "WECOM", "WECOM_APP" -> {
                params.put("msgType", "textcard");
                params.put("actionUrl", card.getActionUrl());
            }
            case "INAPP" -> {
                // 站内卡片: extra 携带卡片 JSON
                Map<String, Object> extra = new HashMap<>();
                extra.put("card", true);
                extra.put("cardTitle", card.getTitle());
                extra.put("cardIcon", card.getIcon());
                extra.put("cardButtons", card.getButtons());
                extra.put("actionUrl", card.getActionUrl());
                extra.put("actionText", card.getActionText());
                params.put("extra", extra);
            }
            default -> {
                // 降级纯文本
                StringBuilder sb = new StringBuilder();
                if (StringUtils.hasText(card.getTitle())) {
                    sb.append("【").append(card.getTitle()).append("】\n");
                }
                sb.append(card.getContent());
                if (card.getButtons() != null) {
                    for (CardMessageDTO.CardButton btn : card.getButtons()) {
                        sb.append("\n").append(btn.getText()).append(": ").append(btn.getUrl());
                    }
                }
                request.setContent(sb.toString());
            }
        }
        request.setParams(params);
        return request;
    }
}
