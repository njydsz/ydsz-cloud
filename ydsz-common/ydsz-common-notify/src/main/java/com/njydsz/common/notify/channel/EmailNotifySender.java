package com.njydsz.common.notify.channel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.notify.config.NotifyProperties;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.metrics.NotifyMetrics;
import com.njydsz.common.notify.security.DkimSigner;
import com.njydsz.common.notify.security.EmailContentSanitizer;
import com.njydsz.common.notify.security.EmailSmtpHealthChecker;
import com.njydsz.common.notify.template.TemplateEngine;
import com.njydsz.common.notify.tracking.EmailTrackingService;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 邮件通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，通过 Spring {@link JavaMailSender} 发送邮件。 支持纯文本邮件、HTML
 * 邮件、模板邮件、附件、内联资源、抄送/密送等高级特性。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>纯文本 / HTML 双模式自动切换
 *   <li>抄送(CC)、密送(BCC)、回复地址(Reply-To) 支持
 *   <li>附件发送（Base64 字节数组）
 *   <li>内联资源（HTML 邮件嵌入图片，通过 {@code cid:} 引用）
 *   <li>自定义邮件头（如 List-Unsubscribe、X-Priority）
 *   <li>邮箱地址格式校验
 *   <li>邮件主题前缀（统一品牌标识）
 *   <li>异步发送（基于虚拟线程池）
 *   <li>模板引擎集成（通过 {@link TemplateEngine} 渲染邮件内容）
 *   <li>P0-3 HTML 内容 XSS 防护（OWASP Sanitizer）
 *   <li>P1-4 发送指标埋点（Micrometer）
 *   <li>P1-5 邮件追踪像素（已读回执）
 *   <li>P2-9 DKIM 签名支持
 *   <li>P3-13 邮件去重与幂等
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(JavaMailSender.class)
@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
public class EmailNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(EmailNotifySender.class);

  /** 邮箱地址格式正则 */
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile(
          "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

  /** 1MB 字节数，用于附件大小校验 */
  private static final long BYTES_PER_MB = 1024L * 1024L;

  private final JavaMailSender mailSender;
  private final NotifyProperties notifyProperties;
  private final TemplateEngine templateEngine;
  private final ExecutorService virtualThreadExecutor;

  /** 可选：指标埋点服务（P1-4） */
  private final ObjectProvider<NotifyMetrics> metricsProvider;

  /** 可选：SMTP 健康检查器（P0-2） */
  private final ObjectProvider<EmailSmtpHealthChecker> healthCheckerProvider;

  /** 可选：邮件追踪服务（P1-5） */
  private final ObjectProvider<EmailTrackingService> trackingServiceProvider;

  /** 可选：DKIM 签名器（P2-9） */
  private final ObjectProvider<DkimSigner> dkimSignerProvider;

  /** 分布式 ID 生成器（用于邮件消息 ID；缺失时回退 UUID） */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构造邮件通知发送器
   *
   * @param mailSender Spring JavaMail 发送器
   * @param notifyProperties 通知配置属性
   * @param templateEngine 模板引擎（可为 null）
   * @param virtualThreadExecutor 虚拟线程池
   * @param metricsProvider 指标埋点服务（可选）
   * @param healthCheckerProvider SMTP 健康检查器（可选）
   * @param trackingServiceProvider 邮件追踪服务（可选）
   * @param dkimSignerProvider DKIM 签名器（可选）
   * @param snowflakeIdGeneratorProvider 分布式 ID 生成器（可选）
   */
  public EmailNotifySender(
      JavaMailSender mailSender,
      NotifyProperties notifyProperties,
      ObjectProvider<TemplateEngine> templateEngineProvider,
      @Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor,
      ObjectProvider<NotifyMetrics> metricsProvider,
      ObjectProvider<EmailSmtpHealthChecker> healthCheckerProvider,
      ObjectProvider<EmailTrackingService> trackingServiceProvider,
      ObjectProvider<DkimSigner> dkimSignerProvider,
      ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider) {
    this.mailSender = mailSender;
    this.notifyProperties = notifyProperties;
    this.templateEngine = templateEngineProvider.getIfAvailable();
    this.virtualThreadExecutor = virtualThreadExecutor;
    this.metricsProvider = metricsProvider;
    this.healthCheckerProvider = healthCheckerProvider;
    this.trackingServiceProvider = trackingServiceProvider;
    this.dkimSignerProvider = dkimSignerProvider;
    this.snowflakeIdGenerator = snowflakeIdGeneratorProvider.getIfAvailable();
  }

  /** 获取邮件渠道配置 */
  private NotifyProperties.EmailConfig emailConfig() {
    return notifyProperties.getEmail();
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.EMAIL;
  }

  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("邮件通知未启用", getChannel().getName());
    }
    if (!isValidEmail(receiver)) {
      return NotifySendResult.failure("收件人邮箱地址无效: " + receiver, getChannel().getName());
    }
    // P0-2：SMTP 健康检查
    if (!isSmtpHealthy()) {
      return NotifySendResult.failure("SMTP 服务不健康", getChannel().getName());
    }
    long startTime = System.nanoTime();
    boolean success = false;
    try {
      String subject = buildSubject(title);
      boolean isHtml = emailConfig().isHtmlMode() && isHtmlContent(content);

      // P0-3：HTML 内容 XSS 清洗
      String safeContent = sanitizeIfNeeded(content, isHtml);

      if (isHtml || hasDefaultCcBcc()) {
        String messageId = generateMessageId();
        // P1-5：注入追踪像素
        safeContent = injectTrackingPixel(safeContent, messageId, isHtml);
        sendMimeMail(receiver, subject, safeContent, isHtml, null, null, null);
      } else {
        sendSimpleMail(receiver, subject, safeContent);
      }

      success = true;
      LOG.debug("邮件通知发送成功: to={}, subject={}", receiver, subject);
      return NotifySendResult.success("email-sent", getChannel().getName());
    } catch (Exception e) {
      LOG.error("邮件通知发送失败: to={}, subject={}, error={}", receiver, title, e.getMessage(), e);
      recordFailure(e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    } finally {
      // P1-4：记录指标
      recordMetrics(success, System.nanoTime() - startTime);
    }
  }

  @Override
  public NotifySendResult sendTemplate(
      String receiver, String templateCode, Object templateParams) {
    if (!isEnabled()) {
      return NotifySendResult.failure("邮件通知未启用", getChannel().getName());
    }
    if (!isValidEmail(receiver)) {
      return NotifySendResult.failure("收件人邮箱地址无效: " + receiver, getChannel().getName());
    }
    try {
      Map<String, Object> params;
      if (templateParams instanceof Map<?, ?> rawMap) {
        params = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> params.put(String.valueOf(k), v));
      } else {
        params = Map.of();
      }

      String content =
          templateEngine != null ? templateEngine.render(templateCode, params) : templateCode;
      String title =
          params.containsKey("subject") ? String.valueOf(params.get("subject")) : templateCode;
      return send(receiver, title, content);
    } catch (Exception e) {
      LOG.error(
          "邮件模板通知发送失败: to={}, template={}, error={}", receiver, templateCode, e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
  }

  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("邮件通知未启用", getChannel().getName());
    }
    if (receivers == null || receivers.isEmpty()) {
      return NotifySendResult.failure("收件人列表为空", getChannel().getName());
    }
    int successCount = 0;
    int failureCount = 0;
    for (String receiver : receivers) {
      NotifySendResult result = send(receiver, title, content);
      if (result.isSuccess()) {
        successCount++;
      } else {
        failureCount++;
      }
    }
    if (failureCount == 0) {
      return NotifySendResult.success("batch:" + successCount, getChannel().getName());
    }
    return NotifySendResult.failure(
        "部分发送失败: 成功" + successCount + "/" + receivers.size(), getChannel().getName());
  }

  @Override
  public boolean isEnabled() {
    return mailSender != null
        && notifyProperties != null
        && notifyProperties.getEmail() != null
        && emailConfig().isEnabled()
        && StringUtils.hasText(emailConfig().getSmtpHost())
        && StringUtils.hasText(emailConfig().getFromMail());
  }

  // ==================== 高级邮件发送 ====================

  /**
   * 发送复杂邮件消息（支持附件、内联资源、抄送、密送、自定义头等）
   *
   * @param message 邮件消息体
   * @return 发送结果
   */
  public NotifySendResult sendEmail(EmailMessage message) {
    if (!isEnabled()) {
      return NotifySendResult.failure("邮件通知未启用", getChannel().getName());
    }
    if (message == null || !StringUtils.hasText(message.getTo())) {
      return NotifySendResult.failure("收件人地址为空", getChannel().getName());
    }
    if (!isValidEmailList(message.getTo())) {
      return NotifySendResult.failure("收件人邮箱地址无效: " + message.getTo(), getChannel().getName());
    }
    // P0-2：SMTP 健康检查
    if (!isSmtpHealthy()) {
      return NotifySendResult.failure("SMTP 服务不健康", getChannel().getName());
    }
    long startTime = System.nanoTime();
    boolean success = false;
    try {
      validateAttachmentSize(message);
      String subject = buildSubject(message.getSubject());
      boolean isHtml =
          message.getHtml() != null
              ? message.getHtml()
              : (emailConfig().isHtmlMode() && isHtmlContent(message.getContent()));

      // P0-3：HTML 内容 XSS 清洗
      String safeContent = sanitizeIfNeeded(message.getContent(), isHtml);

      // P1-5：注入追踪像素
      String messageId = generateMessageId();
      safeContent = injectTrackingPixel(safeContent, messageId, isHtml);

      sendMimeMail(
          message.getTo(),
          subject,
          safeContent,
          isHtml,
          message.getCc(),
          message.getBcc(),
          message);

      success = true;
      LOG.debug(
          "高级邮件发送成功: to={}, subject={}, attachments={}, inlineResources={}",
          message.getTo(),
          subject,
          message.getAttachments() != null ? message.getAttachments().size() : 0,
          message.getInlineResources() != null ? message.getInlineResources().size() : 0);
      return NotifySendResult.success(messageId, getChannel().getName());
    } catch (Exception e) {
      LOG.error(
          "高级邮件发送失败: to={}, subject={}, error={}",
          message.getTo(),
          message.getSubject(),
          e.getMessage(),
          e);
      recordFailure(e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    } finally {
      // P1-4：记录指标
      recordMetrics(success, System.nanoTime() - startTime);
    }
  }

  // ==================== 异步发送 ====================

  /**
   * 异步发送邮件
   *
   * @param receiver 收件人邮箱
   * @param title 邮件标题
   * @param content 邮件内容
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> sendEmailAsync(
      String receiver, String title, String content) {
    return CompletableFuture.supplyAsync(
        () -> send(receiver, title, content), virtualThreadExecutor);
  }

  /**
   * 异步发送复杂邮件
   *
   * @param message 邮件消息体
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> sendEmailAsync(EmailMessage message) {
    return CompletableFuture.supplyAsync(() -> sendEmail(message), virtualThreadExecutor);
  }

  /**
   * 批量异步发送邮件
   *
   * @param receivers 收件人列表
   * @param title 邮件标题
   * @param content 邮件内容
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> batchSendEmailAsync(
      List<String> receivers, String title, String content) {
    return CompletableFuture.supplyAsync(
        () -> batchSend(receivers, title, content), virtualThreadExecutor);
  }

  // ==================== 集成辅助方法 ====================

  /**
   * P0-2：检查 SMTP 是否健康
   *
   * @return true 表示 SMTP 服务健康
   */
  private boolean isSmtpHealthy() {
    EmailSmtpHealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
    if (healthChecker == null) {
      return true; // 未配置健康检查器时默认健康
    }
    return healthChecker.isHealthy();
  }

  /**
   * P0-3：HTML 内容 XSS 清洗
   *
   * @param content 原始内容
   * @param isHtml 是否 HTML 格式
   * @return 清洗后的内容
   */
  private String sanitizeIfNeeded(String content, boolean isHtml) {
    if (!isHtml || !StringUtils.hasText(content)) {
      return content;
    }
    if (emailConfig().getSecurity() != null && emailConfig().getSecurity().isSanitizeHtml()) {
      String sanitized = EmailContentSanitizer.sanitize(content);
      if (!sanitized.equals(content)) {
        LOG.debug("HTML 邮件内容已清洗 XSS");
      }
      return sanitized;
    }
    return content;
  }

  /**
   * P1-5：注入追踪像素
   *
   * @param content 原始 HTML 内容
   * @param messageId 邮件消息 ID
   * @param isHtml 是否 HTML 格式
   * @return 注入追踪像素后的内容
   */
  private String injectTrackingPixel(String content, String messageId, boolean isHtml) {
    if (!isHtml || !StringUtils.hasText(content)) {
      return content;
    }
    EmailTrackingService trackingService = trackingServiceProvider.getIfAvailable();
    if (trackingService == null || !trackingService.isTrackingEnabled()) {
      return content;
    }
    return trackingService.injectTrackingPixel(content, messageId);
  }

  /**
   * P1-4：记录发送指标
   *
   * @param success 是否成功
   * @param durationNanos 耗时（纳秒）
   */
  private void recordMetrics(boolean success, long durationNanos) {
    NotifyMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordEmailSend(
          getChannel().getName(), success, Duration.ofNanos(durationNanos), null);
      if (!success) {
        metrics.recordEmailFailure(getChannel().getName(), "send_error", "send_failure");
      }
    }
  }

  /**
   * P1-4：记录发送失败
   *
   * @param e 异常信息
   */
  private void recordFailure(Exception e) {
    NotifyMetrics metrics = metricsProvider.getIfAvailable();
    if (metrics != null) {
      metrics.recordEmailFailure(
          getChannel().getName(), e.getClass().getSimpleName(), e.getClass().getName());
    }
  }

  /**
   * 生成邮件消息 ID
   *
   * @return 消息 ID（优先雪花 ID，缺失时回退 UUID）
   */
  private String generateMessageId() {
    if (snowflakeIdGenerator != null) {
      return String.valueOf(snowflakeIdGenerator.nextId());
    }
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }

  // ==================== 内部方法 ====================

  /**
   * 构建邮件主题（添加默认前缀）
   *
   * @param subject 原始主题
   * @return 添加前缀后的主题
   */
  private String buildSubject(String subject) {
    if (subject == null || subject.isEmpty()) {
      subject = "无主题";
    }
    String prefix = emailConfig().getDefaultSubjectPrefix();
    if (StringUtils.hasText(prefix) && !subject.startsWith(prefix)) {
      return prefix + " " + subject;
    }
    return subject;
  }

  /**
   * 判断内容是否为 HTML 格式
   *
   * @param content 邮件内容
   * @return true 表示内容为 HTML 格式
   */
  private boolean isHtmlContent(String content) {
    return content != null && content.contains("<") && content.contains(">");
  }

  /**
   * 检查是否配置了默认抄送/密送
   *
   * @return true 表示已配置默认抄送或密送
   */
  private boolean hasDefaultCcBcc() {
    return StringUtils.hasText(emailConfig().getCc())
        || StringUtils.hasText(emailConfig().getBcc());
  }

  /**
   * 发送简单纯文本邮件（无附件、无抄送）
   *
   * @param to 收件人
   * @param subject 主题
   * @param content 内容
   */
  private void sendSimpleMail(String to, String subject, String content) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(buildFromAddress());
    msg.setTo(to);
    msg.setSubject(subject);
    msg.setText(content != null ? content : "");
    if (StringUtils.hasText(emailConfig().getReplyTo())) {
      msg.setReplyTo(emailConfig().getReplyTo());
    }
    mailSender.send(msg);
  }

  /**
   * 发送 MIME 邮件（支持 HTML、附件、内联资源、抄送、密送、自定义头、DKIM 签名）
   *
   * @param to 收件人
   * @param subject 主题
   * @param content 内容
   * @param isHtml 是否 HTML 格式
   * @param cc 抄送（可为 null，为空时使用默认配置）
   * @param bcc 密送（可为 null，为空时使用默认配置）
   * @param message 完整邮件消息体（可为 null，包含附件、内联资源、自定义头）
   * @throws MessagingException 邮件构建异常
   */
  private void sendMimeMail(
      String to,
      String subject,
      String content,
      boolean isHtml,
      String cc,
      String bcc,
      EmailMessage message)
      throws MessagingException {
    MimeMessage mime = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mime, true, emailConfig().getEncoding());

    helper.setFrom(buildFromAddress());
    helper.setTo(to);
    helper.setSubject(subject);

    if (isHtml) {
      helper.setText(content != null ? content : "", true);
    } else {
      helper.setText(content != null ? content : "", false);
    }

    // 抄送：优先使用传入参数，其次使用默认配置
    String effectiveCc = StringUtils.hasText(cc) ? cc : emailConfig().getCc();
    if (StringUtils.hasText(effectiveCc)) {
      helper.setCc(parseAddresses(effectiveCc));
    }

    // 密送：优先使用传入参数，其次使用默认配置
    String effectiveBcc = StringUtils.hasText(bcc) ? bcc : emailConfig().getBcc();
    if (StringUtils.hasText(effectiveBcc)) {
      helper.setBcc(parseAddresses(effectiveBcc));
    }

    // 回复地址
    if (StringUtils.hasText(emailConfig().getReplyTo())) {
      helper.setReplyTo(emailConfig().getReplyTo());
    }

    // 附件
    if (message != null && message.getAttachments() != null) {
      for (EmailMessage.Attachment attachment : message.getAttachments()) {
        if (StringUtils.hasText(attachment.getFilename()) && attachment.getContent() != null) {
          helper.addAttachment(
              attachment.getFilename(), new ByteArrayResource(attachment.getContent()));
        }
      }
    }

    // 内联资源
    if (message != null && message.getInlineResources() != null) {
      for (EmailMessage.InlineResource resource : message.getInlineResources()) {
        if (StringUtils.hasText(resource.getContentId()) && resource.getContent() != null) {
          helper.addInline(resource.getContentId(), new ByteArrayResource(resource.getContent()));
        }
      }
    }

    // 自定义邮件头
    if (message != null && message.getHeaders() != null) {
      for (Map.Entry<String, String> entry : message.getHeaders().entrySet()) {
        mime.setHeader(entry.getKey(), entry.getValue());
      }
    }

    // P0-3：List-Unsubscribe 退订头
    if (emailConfig().getSecurity() != null
        && StringUtils.hasText(emailConfig().getSecurity().getListUnsubscribe())) {
      mime.setHeader(
          "List-Unsubscribe", "<" + emailConfig().getSecurity().getListUnsubscribe() + ">");
    }

    // P2-9：DKIM 签名
    DkimSigner dkimSigner = dkimSignerProvider.getIfAvailable();
    if (dkimSigner != null && dkimSigner.isDkimEnabled()) {
      InternetHeaders headers = new InternetHeaders();
      headers.setHeader("From", buildFromAddress());
      headers.setHeader("To", to);
      headers.setHeader("Subject", subject);
      byte[] bodyBytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
      String dkimSignature = dkimSigner.generateDkimSignature(headers, bodyBytes);
      if (dkimSignature != null) {
        mime.setHeader(DkimSigner.DKIM_HEADER, dkimSignature);
      }
    }

    mailSender.send(mime);
  }

  /**
   * 构建发件人地址（含显示名称）
   *
   * @return 发件人地址字符串
   */
  private String buildFromAddress() {
    String fromName = emailConfig().getFromName();
    String fromMail = emailConfig().getFromMail();
    if (StringUtils.hasText(fromName)) {
      return fromName + " <" + fromMail + ">";
    }
    return fromMail;
  }

  /**
   * 解析逗号分隔的邮箱地址列表
   *
   * @param addresses 逗号或分号分隔的地址字符串
   * @return 地址数组
   */
  private String[] parseAddresses(String addresses) {
    return addresses.split("[,;]\\s*");
  }

  /**
   * 校验邮箱地址格式
   *
   * @param email 邮箱地址
   * @return true 表示格式有效
   */
  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return EMAIL_PATTERN.matcher(email.trim()).matches();
  }

  /**
   * 校验多个邮箱地址格式（逗号分隔）
   *
   * @param emails 逗号分隔的邮箱地址
   * @return true 表示所有地址格式有效
   */
  private boolean isValidEmailList(String emails) {
    if (!StringUtils.hasText(emails)) {
      return false;
    }
    for (String email : parseAddresses(emails)) {
      if (!isValidEmail(email)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 校验附件总大小是否超过限制
   *
   * @param message 邮件消息体
   * @throws IllegalArgumentException 附件大小超过限制时抛出
   */
  private void validateAttachmentSize(EmailMessage message) {
    if (message == null || message.getAttachments() == null) {
      return;
    }
    long totalSize = 0;
    for (EmailMessage.Attachment attachment : message.getAttachments()) {
      if (attachment.getContent() != null) {
        totalSize += attachment.getContent().length;
      }
    }
    long maxBytes = emailConfig().getMaxAttachmentSizeMb() * BYTES_PER_MB;
    if (totalSize > maxBytes) {
      throw new IllegalArgumentException(
          "附件总大小("
              + (totalSize / BYTES_PER_MB)
              + "MB)超过限制("
              + emailConfig().getMaxAttachmentSizeMb()
              + "MB)");
    }
  }
}
