package com.njydsz.message.server.service.receipt;

/**
 * 全通道已读回执 Service
 *
 * <p>为邮件和短信通道提供主动探测的"已读"回执能力。常规通道(IN_APP/PUSH/IM)的消息已读状态
 * 由前端主动调用 {@code ReadStatusSyncService.markRead} 触发,而邮件和短信因用户离线,
 * 需通过链路追踪技术主动探测"用户是否打开/点击"。
 *
 * <p><b>实现方式：</b>
 * <ul>
 *   <li><b>邮件：</b>在 HTML 正文末尾注入 1x1 透明 GIF 追踪像素 {@code <img src="/msg/track/{msgId}.gif"/>},
 *       用户打开邮件时浏览器请求该像素 URL 触发回执</li>
 *   <li><b>短信：</b>将原始 URL 替换为短链 {@code https://s.ydsz.cn/{shortCode}},用户点击短链时
 *       触发回执后 302 跳转到目标 URL</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>注入追踪元素</b>：{@link #injectEmailTrackingPixel} / {@link #generateShortLink}</li>
 *   <li><b>回执回调处理</b>：{@link #handleEmailRead} / {@link #handleShortLinkClick}</li>
 *   <li><b>状态查询</b>：{@link #isRead}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.server.receipt.ReceiptService 通用回执 Service
 * @see ReadStatusSyncService 已读状态同步服务(IN_APP/PUSH/IM)
 */
public interface ReadReceiptService {

    /**
     * 为邮件内容注入追踪像素。
     *
     * @param htmlContent 邮件 HTML 内容
     * @param msgId       消息 ID
     * @return 注入追踪像素后的 HTML
     */
    String injectEmailTrackingPixel(String htmlContent, String msgId);

    /**
     * 生成短信短链。
     *
     * @param originalUrl 原始 URL
     * @param msgId       消息 ID
     * @return 短链 URL
     */
    String generateShortLink(String originalUrl, String msgId);

    /**
     * 处理追踪像素请求（邮件已读回调）。
     *
     * @param msgId 消息 ID
     */
    void handleEmailRead(String msgId);

    /**
     * 处理短链点击（短信已读回调），返回目标 URL 用于重定向。
     *
     * @param shortCode 短链 code
     * @return 目标 URL
     */
    String handleShortLinkClick(String shortCode);

    /**
     * 查询消息已读状态。
     *
     * @param msgId 消息 ID
     * @return true 表示已读
     */
    boolean isRead(String msgId);
}
