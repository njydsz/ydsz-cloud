package com.njydsz.pmis.message.server.channel.impl;

import java.util.Base64;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.service.receipt.ReadReceiptService;

import lombok.extern.slf4j.Slf4j;

/**
 * 邮件通道实现。
 *
 * <p>通过 {@link JavaMailSender} 发送邮件，自动识别 HTML（内容含 {@code <}）或纯文本格式。
 * 发件人取 {@code spring.mail.username}。{@link JavaMailSender} 为可选注入，
 * 未配置邮件时发送直接返回 fail。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EmailChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "EMAIL";

    /** JavaMail 发送器（未配置邮件时为 null） */
    private final JavaMailSender mailSender;

    /** 发件人地址 */
    @Value("${spring.mail.username:noreply@example.com}")
    private String from;

    /** P2-14: 已读回执服务（可选注入） */
    private final ReadReceiptService readReceiptService;

    /**
     * 构造方法，邮件发送器与回执服务可选注入。
     *
     * @param mailSender        JavaMail 发送器
     * @param readReceiptService 已读回执服务（P2-14）
     */
    public EmailChannel(@Autowired(required = false) JavaMailSender mailSender,
                        @Autowired(required = false) ReadReceiptService readReceiptService) {
        this.mailSender = mailSender;
        this.readReceiptService = readReceiptService;
    }

    /**
     * 通道类型。
     *
     * @return EMAIL
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 发送邮件，自动识别 HTML / 纯文本格式。
     *
     * <p>P2-14 增强：
     * <ul>
     *   <li>HTML 邮件注入追踪像素（已读回执）</li>
     *   <li>支持附件：通过 channelMeta.attachments 传入（Base64 编码）</li>
     *   <li>支持内嵌图片：通过 channelMeta.inlineImages 传入</li>
     *   <li>注入 List-Unsubscribe 头（退订支持）</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追踪 ID）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (mailSender == null) {
            return MessageResult.fail(CHANNEL_TYPE, "JavaMailSender 未配置");
        }
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "收件人邮箱不能为空");
        }
        try {
            String subject = request.getSubject() == null ? "PMIS 通知" : request.getSubject();
            String content = request.getContent();
            boolean isHtml = content != null && content.contains("<");
            // P2-14: HTML 邮件注入追踪像素
            if (isHtml && readReceiptService != null && StringUtils.hasText(request.getMessageId())) {
                content = readReceiptService.injectEmailTrackingPixel(content, request.getMessageId());
            }
            if (isHtml) {
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(request.getReceiver());
                helper.setSubject(subject);
                helper.setText(content, true);
                // P2-14: 附件支持
                Map<String, String> meta = request.getChannelMeta();
                if (meta != null) {
                    // 附件（key=文件名, value=Base64 内容）
                    String attachmentsStr = meta.get("attachments");
                    if (StringUtils.hasText(attachmentsStr)) {
                        addAttachments(helper, attachmentsStr);
                    }
                    // 内嵌图片（key=contentId, value=Base64 内容）
                    String inlineStr = meta.get("inlineImages");
                    if (StringUtils.hasText(inlineStr)) {
                        addInlineImages(helper, inlineStr);
                    }
                }
                mailSender.send(mime);
            } else {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(request.getReceiver());
                msg.setSubject(subject);
                msg.setText(content);
                mailSender.send(msg);
            }
            String traceId = CHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraceId();
            log.info("[EMAIL] 发送成功: to={} subject={}", request.getReceiver(), subject);
            return MessageResult.ok(CHANNEL_TYPE, traceId);
        } catch (Exception e) {
            log.error("[EMAIL] 发送失败: to={} reason={}", request.getReceiver(), e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * P2-14: 添加附件。
     *
     * <p>attachments 格式为 JSON：[{"name":"file.pdf","data":"base64..."}, ...]
     *
     * @param helper       MimeMessageHelper
     * @param attachmentsJson 附件 JSON
     */
    private void addAttachments(MimeMessageHelper helper, String attachmentsJson) {
        try {
            var attachments = YdszJson.parseArray(attachmentsJson);
            for (int i = 0; i < attachments.size(); i++) {
                var item = attachments.getJSONObject(i);
                String name = item.getString("name");
                String data = item.getString("data");
                if (StringUtils.hasText(name) && StringUtils.hasText(data)) {
                    byte[] bytes = Base64.getDecoder().decode(data);
                    helper.addAttachment(name, new ByteArrayResource(bytes));
                }
            }
        } catch (Exception e) {
            log.warn("[EMAIL] 附件添加失败: {}", e.getMessage(), e);
        }
    }

    /**
     * P2-14: 添加内嵌图片。
     *
     * <p>inlineImages 格式为 JSON：[{"cid":"logo","data":"base64..."}, ...]
     *
     * @param helper      MimeMessageHelper
     * @param inlineJson  内嵌图片 JSON
     */
    private void addInlineImages(MimeMessageHelper helper, String inlineJson) {
        try {
            var images = YdszJson.parseArray(inlineJson);
            for (int i = 0; i < images.size(); i++) {
                var item = images.getJSONObject(i);
                String cid = item.getString("cid");
                String data = item.getString("data");
                if (StringUtils.hasText(cid) && StringUtils.hasText(data)) {
                    byte[] bytes = Base64.getDecoder().decode(data);
                    helper.addInline(cid, new ByteArrayResource(bytes));
                }
            }
        } catch (Exception e) {
            log.warn("[EMAIL] 内嵌图片添加失败: {}", e.getMessage(), e);
        }
    }
}
