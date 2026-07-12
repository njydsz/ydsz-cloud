package com.njydsz.pmis.common.email.domain;

import java.time.LocalDateTime;

/**
 * 邮件发送结果封装类
 *
 * <p>封装邮件发送的结果信息，包括发送状态、消息ID、发送时间、收件人列表等。
 * 用于向调用方返回详细的发送结果，支持链式构建。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SendResult result = SendResult.builder()
 *         .success(true)
 *         .messageId("abc123")
 *         .sentAt(LocalDateTime.now())
 *         .recipients(new String[]{"a@example.com", "b@example.com"})
 *         .build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class SendResult {

    /** 是否发送成功 */
    private boolean success;
    /** 错误信息 */
    private String errorMessage;
    /** 消息ID */
    private String messageId;
    /** 发送时间 */
    private LocalDateTime sentAt;
    /** 收件人数组 */
    private String[] recipients;
    /** 抄送人数组 */
    private String[] ccRecipients;
    /** 密送人数组 */
    private String[] bccRecipients;
    /** 邮件主题 */
    private String subject;
    /** 邮件类型 */
    private String emailType;
    /** 追踪ID */
    private String traceId;
    /** 附件数量 */
    private Integer attachmentsCount;

    public SendResult() {
    }

    public SendResult(boolean success, String errorMessage, String messageId, LocalDateTime sentAt,
                      String[] recipients, String[] ccRecipients, String[] bccRecipients,
                      String subject, String emailType, String traceId, Integer attachmentsCount) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.messageId = messageId;
        this.sentAt = sentAt;
        this.recipients = recipients;
        this.ccRecipients = ccRecipients;
        this.bccRecipients = bccRecipients;
        this.subject = subject;
        this.emailType = emailType;
        this.traceId = traceId;
        this.attachmentsCount = attachmentsCount;
    }

    public static SendResultBuilder builder() {
        return new SendResultBuilder();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorDetail() {
        return success ? null : errorMessage;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String[] getRecipients() {
        return recipients;
    }

    public void setRecipients(String[] recipients) {
        this.recipients = recipients;
    }

    public String[] getCcRecipients() {
        return ccRecipients;
    }

    public void setCcRecipients(String[] ccRecipients) {
        this.ccRecipients = ccRecipients;
    }

    public String[] getBccRecipients() {
        return bccRecipients;
    }

    public void setBccRecipients(String[] bccRecipients) {
        this.bccRecipients = bccRecipients;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEmailType() {
        return emailType;
    }

    public void setEmailType(String emailType) {
        this.emailType = emailType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getAttachmentsCount() {
        return attachmentsCount;
    }

    public void setAttachmentsCount(Integer attachmentsCount) {
        this.attachmentsCount = attachmentsCount;
    }

    /**
     * 创建成功结果
     *
     * @param recipients 收件人数组
     * @return 成功的发送结果
     */
    public static SendResult success(String[] recipients) {
        return builder()
                .success(true)
                .recipients(recipients)
                .sentAt(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @param recipients   收件人数组
     * @return 失败的发送结果
     */
    public static SendResult failure(String errorMessage, String[] recipients) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .recipients(recipients)
                .sentAt(LocalDateTime.now())
                .build();
    }

    public static class SendResultBuilder {
        private boolean success;
        private String errorMessage;
        private String messageId;
        private LocalDateTime sentAt;
        private String[] recipients;
        private String[] ccRecipients;
        private String[] bccRecipients;
        private String subject;
        private String emailType;
        private String traceId;
        private Integer attachmentsCount;

        public SendResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public SendResultBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public SendResultBuilder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public SendResultBuilder sentAt(LocalDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public SendResultBuilder recipients(String[] recipients) {
            this.recipients = recipients;
            return this;
        }

        public SendResultBuilder ccRecipients(String[] ccRecipients) {
            this.ccRecipients = ccRecipients;
            return this;
        }

        public SendResultBuilder bccRecipients(String[] bccRecipients) {
            this.bccRecipients = bccRecipients;
            return this;
        }

        public SendResultBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public SendResultBuilder emailType(String emailType) {
            this.emailType = emailType;
            return this;
        }

        public SendResultBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public SendResultBuilder attachmentsCount(Integer attachmentsCount) {
            this.attachmentsCount = attachmentsCount;
            return this;
        }

        public SendResult build() {
            return new SendResult(success, errorMessage, messageId, sentAt,
                    recipients, ccRecipients, bccRecipients, subject, emailType, traceId, attachmentsCount);
        }
    }
}