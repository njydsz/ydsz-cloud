package com.njydsz.pmis.common.notify.channel;

import com.njydsz.pmis.common.notify.config.NotifyProperties;
import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.template.TemplateEngine;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * 邮件通知发送器
 *
 * <p>实现 {@link NotifyChannelStrategy} 接口，通过 Spring {@link JavaMailSender} 发送邮件。
 * 支持纯文本邮件、HTML 邮件、模板邮件、附件、内联资源、抄送/密送等高级特性。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>纯文本 / HTML 双模式自动切换</li>
 *   <li>抄送(CC)、密送(BCC)、回复地址(Reply-To) 支持</li>
 *   <li>附件发送（Base64 字节数组）</li>
 *   <li>内联资源（HTML 邮件嵌入图片，通过 {@code cid:} 引用）</li>
 *   <li>自定义邮件头（如 List-Unsubscribe、X-Priority）</li>
 *   <li>邮箱地址格式校验</li>
 *   <li>邮件主题前缀（统一品牌标识）</li>
 *   <li>异步发送（基于虚拟线程池）</li>
 *   <li>模板引擎集成（通过 {@link TemplateEngine} 渲染邮件内容）</li>
 * </ul>
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   notify:
 *     email:
 *       enabled: true
 *       smtp-host: smtp.exmail.qq.com
 *       smtp-port: 465
 *       from-mail: noreply@ydsz.com
 *       from-name: ydsz项目管理平台
 *       password: your-auth-code
 *       html-mode: true
 *       default-subject-prefix: "【ydsz项目管理】"
 *       cc: pmo@ydsz.com
 *       bcc: audit@ydsz.com
 *       reply-to: support@ydsz.com
 *       max-attachment-size-mb: 20
 *       ssl:
 *         enabled: true
 *         protocols: TLSv1.2
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(JavaMailSender.class)
@ConditionalOnProperty(prefix = "ydsz.notify.email", name = "enabled", havingValue = "true")
public class EmailNotifySender implements NotifyChannelStrategy {

	private static final Logger log = LoggerFactory.getLogger(EmailNotifySender.class);

	/** 邮箱地址格式正则 */
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

	/** 1MB 字节数，用于附件大小校验 */
	private static final long BYTES_PER_MB = 1024L * 1024L;

	private final JavaMailSender mailSender;
	private final NotifyProperties notifyProperties;
	private final TemplateEngine templateEngine;
	private final ExecutorService virtualThreadExecutor;

	/**
	 * 构造邮件通知发送器
	 *
	 * @param mailSender           Spring JavaMail 发送器
	 * @param notifyProperties     通知配置属性（从中获取邮件渠道配置）
	 * @param templateEngine       模板引擎
	 * @param virtualThreadExecutor 虚拟线程池（用于异步发送）
	 */
	public EmailNotifySender(
			JavaMailSender mailSender,
			NotifyProperties notifyProperties,
			TemplateEngine templateEngine,
			@Qualifier("notifyVirtualThreadExecutor") ExecutorService virtualThreadExecutor) {
		this.mailSender = mailSender;
		this.notifyProperties = notifyProperties;
		this.templateEngine = templateEngine;
		this.virtualThreadExecutor = virtualThreadExecutor;
	}

	/**
	 * 获取邮件渠道配置
	 *
	 * @return 邮件渠道配置
	 */
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
			return NotifySendResult.failure("邮件通知未启用", channelName());
		}
		if (!isValidEmail(receiver)) {
			return NotifySendResult.failure("收件人邮箱地址无效: " + receiver, channelName());
		}
		try {
			String subject = buildSubject(title);
			boolean isHtml = emailConfig().isHtmlMode() && isHtmlContent(content);

			if (isHtml || hasDefaultCcBcc()) {
				sendMimeMail(receiver, subject, content, isHtml, null, null, null);
			} else {
				sendSimpleMail(receiver, subject, content);
			}

			log.debug("邮件通知发送成功: to={}, subject={}", receiver, subject);
			return NotifySendResult.success("email-sent", channelName());
		} catch (Exception e) {
			log.error("邮件通知发送失败: to={}, subject={}, error={}", receiver, title, e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
		if (!isEnabled()) {
			return NotifySendResult.failure("邮件通知未启用", channelName());
		}
		if (!isValidEmail(receiver)) {
			return NotifySendResult.failure("收件人邮箱地址无效: " + receiver, channelName());
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> params = templateParams instanceof Map
					? (Map<String, Object>) templateParams
					: Map.of();

			String content = templateEngine.render(templateCode, params);
			String title = params.containsKey("subject")
					? String.valueOf(params.get("subject"))
					: templateCode;
			return send(receiver, title, content);
		} catch (Exception e) {
			log.error("邮件模板通知发送失败: to={}, template={}, error={}",
					receiver, templateCode, e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	@Override
	public NotifySendResult batchSend(List<String> receivers, String title, String content) {
		if (!isEnabled()) {
			return NotifySendResult.failure("邮件通知未启用", channelName());
		}
		if (receivers == null || receivers.isEmpty()) {
			return NotifySendResult.failure("收件人列表为空", channelName());
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
			return NotifySendResult.success("batch:" + successCount, channelName());
		}
		return NotifySendResult.failure(
				"部分发送失败: 成功" + successCount + "/" + receivers.size(), channelName());
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
			return NotifySendResult.failure("邮件通知未启用", channelName());
		}
		if (message == null || !StringUtils.hasText(message.getTo())) {
			return NotifySendResult.failure("收件人地址为空", channelName());
		}
		if (!isValidEmailList(message.getTo())) {
			return NotifySendResult.failure("收件人邮箱地址无效: " + message.getTo(), channelName());
		}
		try {
			validateAttachmentSize(message);
			String subject = buildSubject(message.getSubject());
			boolean isHtml = message.getHtml() != null
					? message.getHtml()
					: (emailConfig().isHtmlMode() && isHtmlContent(message.getContent()));

			sendMimeMail(
					message.getTo(),
					subject,
					message.getContent(),
					isHtml,
					message.getCc(),
					message.getBcc(),
					message
			);

			log.debug("高级邮件发送成功: to={}, subject={}, attachments={}, inlineResources={}",
					message.getTo(), subject,
					message.getAttachments() != null ? message.getAttachments().size() : 0,
					message.getInlineResources() != null ? message.getInlineResources().size() : 0);
			return NotifySendResult.success("email-sent", channelName());
		} catch (Exception e) {
			log.error("高级邮件发送失败: to={}, subject={}, error={}",
					message.getTo(), message.getSubject(), e.getMessage(), e);
			return NotifySendResult.failure(e.getMessage(), channelName());
		}
	}

	// ==================== 异步发送 ====================

	/**
	 * 异步发送邮件
	 *
	 * @param receiver 收件人邮箱
	 * @param title    邮件主题
	 * @param content  邮件内容
	 * @return 异步发送结果
	 */
	public CompletableFuture<NotifySendResult> sendEmailAsync(String receiver, String title, String content) {
		return CompletableFuture.supplyAsync(() -> send(receiver, title, content), virtualThreadExecutor);
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
	 * @param receivers 收件人邮箱列表
	 * @param title     邮件主题
	 * @param content   邮件内容
	 * @return 异步发送结果
	 */
	public CompletableFuture<NotifySendResult> batchSendEmailAsync(List<String> receivers, String title, String content) {
		return CompletableFuture.supplyAsync(() -> batchSend(receivers, title, content), virtualThreadExecutor);
	}

	// ==================== 内部方法 ====================

	private String channelName() {
		return "邮件";
	}

	/**
	 * 构建邮件主题（添加默认前缀）
	 *
	 * @param subject 原始主题
	 * @return 带前缀的主题
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
	 * @return true 表示内容包含 HTML 标签
	 */
	private boolean isHtmlContent(String content) {
		return content != null && content.contains("<") && content.contains(">");
	}

	/**
	 * 检查是否配置了默认抄送/密送
	 *
	 * @return true 表示有默认抄送或密送配置
	 */
	private boolean hasDefaultCcBcc() {
		return StringUtils.hasText(emailConfig().getCc()) || StringUtils.hasText(emailConfig().getBcc());
	}

	/**
	 * 发送简单纯文本邮件（无附件、无抄送）
	 *
	 * @param to      收件人
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
	 * 发送 MIME 邮件（支持 HTML、附件、内联资源、抄送、密送、自定义头）
	 *
	 * @param to       收件人
	 * @param subject  主题
	 * @param content  内容
	 * @param isHtml   是否 HTML 格式
	 * @param cc       抄送（可为 null，为空时使用默认配置）
	 * @param bcc      密送（可为 null，为空时使用默认配置）
	 * @param message  完整邮件消息体（可为 null，包含附件、内联资源、自定义头）
	 * @throws MessagingException 邮件构建异常
	 */
	private void sendMimeMail(String to, String subject, String content, boolean isHtml,
							   String cc, String bcc, EmailMessage message) throws MessagingException {
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
					helper.addAttachment(attachment.getFilename(), new ByteArrayResource(attachment.getContent()));
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

		mailSender.send(mime);
	}

	/**
	 * 构建发件人地址（含显示名称）
	 *
	 * @return 格式为 "显示名称 <邮箱地址>" 的发件人地址
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
	 * @param addresses 逗号分隔的邮箱地址
	 * @return 邮箱地址数组
	 */
	private String[] parseAddresses(String addresses) {
		return addresses.split("[,;]\\s*");
	}

	/**
	 * 校验邮箱地址格式
	 *
	 * @param email 邮箱地址
	 * @return true 表示格式合法
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
	 * @return true 表示全部格式合法
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
	 * @throws IllegalArgumentException 附件总大小超限时抛出
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
					"附件总大小(" + (totalSize / BYTES_PER_MB) + "MB)超过限制(" + emailConfig().getMaxAttachmentSizeMb() + "MB)");
		}
	}
}
