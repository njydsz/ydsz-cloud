package com.njydsz.pmis.common.notification;

/**
 * 消息通知发送接口
 *
 * <p>统一通知发送抽象，支持短信/邮件/站内信/IM 等多种渠道。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface NotificationSender {

    /**
     * 发送通知
     *
     * @param channel  通知渠道（SMS/EMAIL/WEBHOOK/IM）
     * @param to       接收方（手机号/邮箱/URL）
     * @param title    标题
     * @param content  内容
     * @param template 模板编码（可选）
     * @return 发送结果（消息 ID）
     */
    String send(String channel, String to, String title, String content, String template);

    /**
     * 批量发送通知
     *
     * @param channel  通知渠道
     * @param toList   接收方列表
     * @param title    标题
     * @param content  内容
     * @return 发送结果列表
     */
    default java.util.List<String> batchSend(String channel, java.util.List<String> toList,
                                              String title, String content) {
        return toList.stream()
                .map(to -> send(channel, to, title, content, null))
                .toList();
    }
}
