package com.njydsz.pmis.message.controller.receipt;

import com.njydsz.pmis.message.service.receipt.ReadReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Base64;

/**
 * 已读回执 Controller。
 *
 * <p>P2-12: 提供邮件追踪像素和短信短链的 HTTP 回调端点。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "已读回执", description = "邮件追踪像素与短信短链回调")
@RestController
@RequestMapping("/api/read-receipt")
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
