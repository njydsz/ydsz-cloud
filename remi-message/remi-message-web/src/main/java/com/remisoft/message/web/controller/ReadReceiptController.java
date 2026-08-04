package com.remisoft.message.web.controller.receipt;

import java.io.IOException;
import java.util.Base64;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.message.server.service.receipt.ReadReceiptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息已读回执（Read Receipt）Controller。
 *
 * <p>提供<b>邮件追踪像素</b>与<b>短信短链跳转</b>两类 HTTP 回调端点，
 * 用于无登录态场景下收集用户对消息的「已读 / 点击」行为数据，
 * 最终通过 {@link com.remisoft.message.server.service.receipt.ReceiptService} 落地到
 * {@code remi_msg_receipt} 表，更新对应 {@code remi_msg_log} 的 {@code receiptStatus}。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/readReceipt/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>邮件追踪像素</b>：{@code GET /pixel/{encodedMsgId}} — 邮件正文内嵌 {@code <img src=...>},
 *       邮件客户端加载图片时自动触发，标记消息为「已读」并返回 1x1 透明 PNG</li>
 *   <li><b>短信短链跳转</b>：{@code GET /s/{shortCode}} — 短信中插入短链（避免长 URL 占用字符）,
 *       用户点击时 302 重定向到原始 URL，同时标记消息为「已点击」</li>
 * </ul>
 *
 * <p><b>与 ReadStatusController 的区别：</b>
 * <ul>
 *   <li>本 Controller：<b>无登录态</b>，由邮件/短信的端点被动触发（pixel / short link）</li>
 *   <li>ReadStatusController：<b>有登录态</b>，由用户在站内通知中心主动操作（markRead 等）</li>
 * </ul>
 *
 * <p><b>透明 PNG（1x1）：</b>硬编码的 Base64 解码字节，用于邮件追踪回调返回，
 * 避免引入图片资源依赖；最小 67 字节、所有邮件客户端均能正常渲染。
 *
 * <p><b>短链降级：</b>当 {@code handleShortLinkClick} 返回 {@code null}（短链不存在或已过期），
 * 返回 HTTP 404 提示用户，而非重定向到错误页，避免引入外部跳转风险。
 *
 * <p><b>幂等性：</b>两个端点均按「同一 {@code msgId/shortCode} 多次触发只生效一次」处理，
 * 即使被恶意刷新也不会造成重复计费/统计污染。
 *
 * @author remi-team
 * @since 1.0.0
 * @see com.remisoft.message.server.service.receipt.ReadReceiptService 已读回执服务
 */
@Slf4j
@Tag(name = "已读回执", description = "邮件追踪像素与短信短链回调")
@RestController
@RequestMapping("/api/v1/message/readReceipt")
@RequiredArgsConstructor
public class ReadReceiptController {

    /** 已读回执服务 */
    private final ReadReceiptService readReceiptService;

    /** 1x1 透明 PNG 字节 */
    private static final byte[] TRANSPARENT_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    /**
     * 邮件追踪像素端点。
     *
     * <p>邮件客户端加载此图片时触发已读回执，返回 1x1 透明 PNG。
     *
     * @param encodedMsgId Base64 编码的消息 ID
     * @return 1x1 透明 PNG
     */
    @Operation(summary = "邮件追踪像素")
    @GetMapping(value = "/pixel/{encodedMsgId}", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] trackingPixel(@PathVariable String encodedMsgId) {
        try {
            String msgId = new String(Base64.getUrlDecoder().decode(encodedMsgId));
            readReceiptService.handleEmailRead(msgId);
        } catch (Exception e) {
            log.debug("[ReadReceipt] 像素回调解析失败: {}", e.getMessage());
        }
        return TRANSPARENT_PNG;
    }

    /**
     * 短链跳转端点。
     *
     * <p>用户点击短信中的短链时，标记消息已读并 302 重定向到原始 URL。
     *
     * @param shortCode 短链 code
     * @param response  HTTP 响应
     */
    @Operation(summary = "短链跳转")
    @GetMapping("/s/{shortCode}")
    public void shortLinkRedirect(@PathVariable String shortCode, HttpServletResponse response) {
        String originalUrl = readReceiptService.handleShortLinkClick(shortCode);
        if (originalUrl != null) {
            try {
                response.sendRedirect(originalUrl);
            } catch (IOException e) {
                log.warn("[ReadReceipt] 重定向失败: {}", e.getMessage());
            }
        } else {
            response.setStatus(404);
        }
    }
}
