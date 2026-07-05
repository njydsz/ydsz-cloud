package com.njydsz.pmis.system.channel.impl;

import com.njydsz.pmis.system.channel.MessageChannel;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件通道实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EmailChannel implements MessageChannel {

    /** JavaMail 发送器（未配置邮件时为 null） */
    private final JavaMailSender mailSender;

    /**
     * 构造方法，邮件发送器可选注入。
     *
     * @param mailSender JavaMail 发送器
     */
    public EmailChannel(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** 发件人地址 */
    @Value("${spring.mail.username:noreply@example.com}")
    private String from;

    /**
     * 通道类型
     *
     * @return 通道类型字符串 EMAIL
     */
    @Override
    public String channelType() {
        return "EMAIL";
    }

    /**
     * 发送邮件，自动识别 HTML/纯文本格式。
     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追踪 ID）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (mailSender == null) {
            return MessageResult.fail("EMAIL", "JavaMailSender 未配置");
        }
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail("EMAIL", "收件人邮箱不能为空");
        }
        try {
            String subject = request.getSubject() == null ? "PMIS 通知" : request.getSubject();
            if (request.getContent() != null && request.getContent().contains("<")) {
                // HTML 邮件
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(request.getReceiver());
                helper.setSubject(subject);
                helper.setText(request.getContent(), true);
                mailSender.send(mime);
            } else {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(request.getReceiver());
                msg.setSubject(subject);
                msg.setText(request.getContent());
                mailSender.send(msg);
            }
            String traceId = "EMAIL-" + SnowflakeIdGenerator.nextTraceId();
            log.info("[EMAIL] 发送成功: to={} subject={}", request.getReceiver(), subject);
            return MessageResult.ok("EMAIL", traceId);
        } catch (Exception e) {
            log.error("[EMAIL] 发送失败: to={} reason={}", request.getReceiver(), e.getMessage(), e);
            return MessageResult.fail("EMAIL", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
