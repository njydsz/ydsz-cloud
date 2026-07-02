package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.UUID;

/**
 * 邮件通道实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EmailChannel implements MessageChannel {

    private final JavaMailSender mailSender;

    public EmailChannel(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username:noreply@example.com}")
    private String from;

    @Override
    public String channelType() {
        return "EMAIL";
    }

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
            String traceId = "EMAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("[EMAIL] 发送成功: to={} subject={}", request.getReceiver(), subject);
            return MessageResult.ok("EMAIL", traceId);
        } catch (Exception e) {
            log.error("[EMAIL] 发送失败: to={} reason={}", request.getReceiver(), e.getMessage(), e);
            return MessageResult.fail("EMAIL", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
