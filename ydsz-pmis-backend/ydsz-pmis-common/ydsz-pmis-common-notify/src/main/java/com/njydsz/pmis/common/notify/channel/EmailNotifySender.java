package com.njydsz.pmis.common.notify.channel;

import com.njydsz.pmis.common.email.domain.Email;
import com.njydsz.pmis.common.email.domain.SendResult;
import com.njydsz.pmis.common.email.service.EmailService;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 邮件通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，负责通过邮件渠道发送通知消息。
 * 基于 pmis-common-notify 模块的 {@link EmailService} 实现邮件发送，
 * 支持普通文本、模板消息和批量发送。仅在 EmailService Bean 存在时自动注册。</p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(EmailService.class)
public class EmailNotifySender implements NotifyChannelStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifySender.class);

    private final EmailService emailService;
    private final ExecutorService virtualThreadExecutor;

    public EmailNotifySender(EmailService emailService,
                             @Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.emailService = emailService;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public NotifyChannel getChannel() {
        return NotifyChannel.EMAIL;
    }

    @Override
    public NotifySendResult send(String receiver, String title, String content) {
        if (!isEnabled()) {
            return NotifySendResult.failure("邮件通知未启用", channelName());
        }
        try {
            Email email = Email.builder()
                    .to(receiver)
                    .subject(title)
                    .content(content)
                    .build();
            SendResult result = emailService.send(email);
            if (result.isSuccess()) {
                return NotifySendResult.success(result.getMessageId(), channelName());
            }
            return NotifySendResult.failure(result.getErrorMessage(), channelName());
        } catch (Exception e) {
            log.error("邮件通知发送失败: receiver={}, error={}", receiver, e.getMessage(), e);
            return NotifySendResult.failure(e.getMessage(), channelName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
        if (!isEnabled()) {
            return NotifySendResult.failure("邮件通知未启用", channelName());
        }
        try {
            Email email = Email.builder()
                    .to(receiver)
                    .subject(templateCode)
                    .template(templateCode)
                    .variables(templateParams instanceof java.util.Map ? (java.util.Map<String, Object>) templateParams : null)
                    .build();
            SendResult result = emailService.send(email);
            if (result.isSuccess()) {
                return NotifySendResult.success(result.getMessageId(), channelName());
            }
            return NotifySendResult.failure(result.getErrorMessage(), channelName());
        } catch (Exception e) {
            log.error("邮件模板通知发送失败: receiver={}, template={}, error={}", receiver, templateCode, e.getMessage(), e);
            return NotifySendResult.failure(e.getMessage(), channelName());
        }
    }

    /**
     * 批量发送邮件通知
     *
     * @param receivers 接收者邮箱列表
     * @param title     邮件主题
     * @param content   邮件内容
     * @return 发送结果
     */
    @Override
    public NotifySendResult batchSend(List<String> receivers, String title, String content) {
        if (!isEnabled()) {
            return NotifySendResult.failure("邮件通知未启用", channelName());
        }
        if (receivers == null || receivers.isEmpty()) {
            return NotifySendResult.failure("收件人列表为空", channelName());
        }
        try {
            List<Email> emails = new ArrayList<>(receivers.size());
            for (String receiver : receivers) {
                emails.add(Email.builder()
                        .to(receiver)
                        .subject(title)
                        .content(content)
                        .build());
            }
            List<SendResult> results = emailService.batchSend(emails);
            long successCount = results.stream().filter(SendResult::isSuccess).count();
            if (successCount == receivers.size()) {
                return NotifySendResult.success("batch:" + successCount, channelName());
            }
            return NotifySendResult.failure(
                    "部分发送失败: 成功" + successCount + "/" + receivers.size(), channelName());
        } catch (Exception e) {
            log.error("邮件批量通知发送失败: count={}, error={}", receivers.size(), e.getMessage(), e);
            return NotifySendResult.failure(e.getMessage(), channelName());
        }
    }

    /**
     * 判断邮件渠道是否启用
     *
     * @return 启用返回 true，否则返回 false
     */
    @Override
    public boolean isEnabled() {
        return emailService != null;
    }

    private String channelName() {
        return "邮件";
    }

    // ==================== 异步邮件发送 ====================

    /**
     * 异步发送邮件，返回 CompletableFuture。
     *
     * @param receiver 接收者
     * @param title    标题
     * @param content  内容
     * @return 异步发送结果
     */
    public CompletableFuture<NotifySendResult> sendEmailAsync(String receiver, String title, String content) {
        return CompletableFuture.supplyAsync(() -> send(receiver, title, content), virtualThreadExecutor);
    }

    /**
     * 批量异步发送邮件，使用 VirtualThread 并行处理。
     *
     * @param receivers 接收者列表
     * @param title     标题
     * @param content   内容
     * @return 异步发送结果
     */
    public CompletableFuture<NotifySendResult> batchSendEmailAsync(List<String> receivers, String title, String content) {
        return CompletableFuture.supplyAsync(() -> batchSend(receivers, title, content), virtualThreadExecutor);
    }
}
