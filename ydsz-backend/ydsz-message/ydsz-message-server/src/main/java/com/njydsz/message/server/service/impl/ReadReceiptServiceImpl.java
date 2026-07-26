package com.njydsz.message.server.service.impl.receipt;

import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.util.id.SnowflakeUtils;
import com.njydsz.message.server.service.receipt.ReadReceiptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全通道已读回执服务实现。
 *
 * <p>P2-12: 实现邮件追踪像素和短信短链的生成与回调处理。
 *
 * <p>邮件追踪像素：
 * <ul>
 *   <li>生成：在 HTML {@code </body>} 前注入 {@code <img src="https://domain/api/read-receipt/pixel/{base64(msgId)}" />}</li>
 *   <li>回调：GET 请求像素 URL → 标记消息已读 → 返回 1x1 透明 PNG</li>
 * </ul>
 *
 * <p>短信短链：
 * <ul>
 *   <li>生成：Redis 存储映射 {@code ydsz:shortlink:{code} → originalUrl}，TTL 7 天</li>
 *   <li>回调：GET 请求短链 URL → 标记消息已读 → 302 重定向到原始 URL</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadReceiptServiceImpl implements ReadReceiptService {

    /** Redis 模板（短链映射 / 已读状态） */
    private final RedisService redisService;

    /** 追踪像素基础 URL */
    @Value("${ydsz.message.tracking.base-url:https://ydsz.example.com}")
    private String trackingBaseUrl;

    /** 短链基础 URL */
    @Value("${ydsz.message.shortlink.base-url:https://s.ydsz.example.com}")
    private String shortlinkBaseUrl;

    /** 短链映射 Redis key 前缀 */
    private static final String SHORTLINK_PREFIX = "ydsz:shortlink:";
    /** 已读状态 Redis key 前缀 */
    private static final String READ_STATUS_PREFIX = "ydsz:read:";
    /** 短链消息 ID 映射前缀 */
    private static final String SHORTLINK_MSG_PREFIX = "ydsz:shortlink:msg:";
    /** 短链 TTL（7 天） */
    private static final long SHORTLINK_TTL = 7 * 24 * 3600L;

    @Override
    public String injectEmailTrackingPixel(String htmlContent, String msgId) {
        if (!StringUtils.hasText(htmlContent) || !StringUtils.hasText(msgId)) {
            return htmlContent;
        }
        // 仅对 HTML 内容注入
        if (!htmlContent.trim().toLowerCase().contains("<html") && !htmlContent.trim().toLowerCase().contains("<body")) {
            // 非 HTML 格式，不注入
            return htmlContent;
        }
        String encodedMsgId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(msgId.getBytes());
        String pixelUrl = trackingBaseUrl + "/api/read-receipt/pixel/" + encodedMsgId;
        String pixel = "<img src=\"" + pixelUrl + "\" width=\"1\" height=\"1\" "
                + "style=\"display:none;border:0;outline:none;\" alt=\"\" />";
        // 在 </body> 前注入，无 </body> 则追加到末尾
        String lower = htmlContent.toLowerCase();
        int bodyCloseIdx = lower.lastIndexOf("</body>");
        if (bodyCloseIdx >= 0) {
            return htmlContent.substring(0, bodyCloseIdx) + pixel + htmlContent.substring(bodyCloseIdx);
        }
        return htmlContent + pixel;
    }

    @Override
    public String generateShortLink(String originalUrl, String msgId) {
        if (!StringUtils.hasText(originalUrl)) {
            return originalUrl;
        }
        String code = SnowflakeUtils.nextIdStr();
        String shortUrl = shortlinkBaseUrl + "/s/" + code;
        try {
            redisService.set(SHORTLINK_PREFIX + code, originalUrl, Duration.ofSeconds(SHORTLINK_TTL));
            if (StringUtils.hasText(msgId)) {
                redisService.set(SHORTLINK_MSG_PREFIX + code, msgId, Duration.ofSeconds(SHORTLINK_TTL));
            }
            log.debug("[ReadReceipt] 短链生成: code={} url={} msgId={}", code, originalUrl, msgId);
        } catch (Exception e) {
            log.warn("[ReadReceipt] 短链生成失败,返回原始 URL: {}", e.getMessage(), e);
            return originalUrl;
        }
        return shortUrl;
    }

    @Override
    public void handleEmailRead(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            return;
        }
        try {
            redisService.set(READ_STATUS_PREFIX + "email:" + msgId, "1", Duration.ofDays(30));
            log.info("[ReadReceipt] 邮件已读: msgId={}", msgId);
        } catch (Exception e) {
            log.warn("[ReadReceipt] 邮件已读标记失败: msgId={} err={}", msgId, e.getMessage(), e);
        }
    }

    @Override
    public String handleShortLinkClick(String shortCode) {
        if (!StringUtils.hasText(shortCode)) {
            return null;
        }
        try {
            String originalUrl = redisService.get(SHORTLINK_PREFIX + shortCode, String.class);
            if (originalUrl == null) {
                log.warn("[ReadReceipt] 短链不存在或已过期: code={}", shortCode);
                return null;
            }
            // 标记消息已读
            String msgId = redisService.get(SHORTLINK_MSG_PREFIX + shortCode, String.class);
            if (StringUtils.hasText(msgId)) {
                redisService.set(READ_STATUS_PREFIX + "sms:" + msgId, "1", Duration.ofDays(30));
                log.info("[ReadReceipt] 短信已读: msgId={} code={}", msgId, shortCode);
            }
            return originalUrl;
        } catch (Exception e) {
            log.warn("[ReadReceipt] 短链点击处理失败: code={} err={}", shortCode, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isRead(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            return false;
        }
        try {
            Boolean emailRead = redisService.hasKey(READ_STATUS_PREFIX + "email:" + msgId);
            Boolean smsRead = redisService.hasKey(READ_STATUS_PREFIX + "sms:" + msgId);
            return Boolean.TRUE.equals(emailRead) || Boolean.TRUE.equals(smsRead);
        } catch (Exception e) {
            return false;
        }
    }
}
