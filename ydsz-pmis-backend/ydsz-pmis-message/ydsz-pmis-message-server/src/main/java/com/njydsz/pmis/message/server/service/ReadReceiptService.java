package com.njydsz.pmis.message.server.service.receipt;

/**
 * 全通道已读回执服务�? *
 * <p>P2-12: 为邮件和短信通道提供已读回执能力�? * <ul>
 *   <li>邮件：在 HTML 正文末尾注入 1x1 透明追踪像素，用户打开邮件时请求像�?URL 触发回执</li>
 *   <li>短信：将原始 URL 替换为短链，用户点击短链时触发回执并跳转目标 URL</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface ReadReceiptService {

    /**
     * 为邮件内容注入追踪像素�?     *
     * @param htmlContent 邮件 HTML 内容
     * @param msgId       消息 ID
     * @return 注入追踪像素后的 HTML
     */
    String injectEmailTrackingPixel(String htmlContent, String msgId);

    /**
     * 生成短信短链�?     *
     * @param originalUrl 原始 URL
     * @param msgId       消息 ID
     * @return 短链 URL
     */
    String generateShortLink(String originalUrl, String msgId);

    /**
     * 处理追踪像素请求（邮件已读回调）�?     *
     * @param msgId 消息 ID
     */
    void handleEmailRead(String msgId);

    /**
     * 处理短链点击（短信已读回调），返回目�?URL 用于重定向�?     *
     * @param shortCode 短链 code
     * @return 目标 URL
     */
    String handleShortLinkClick(String shortCode);

    /**
     * 查询消息已读状态�?     *
     * @param msgId 消息 ID
     * @return true 表示已读
     */
    boolean isRead(String msgId);
}
