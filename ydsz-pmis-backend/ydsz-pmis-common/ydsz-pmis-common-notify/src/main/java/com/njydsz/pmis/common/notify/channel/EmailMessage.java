package com.njydsz.pmis.common.notify.channel;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 邮件消息体
 *
 * <p>封装邮件发送所需的完整信息，支持收件人、抄送、密送、附件、内联资源等高级特性。
 * 通过 {@link EmailNotifySender#sendEmail(EmailMessage)} 方法发送。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * EmailMessage message = EmailMessage.builder()
 *     .to("user@example.com")
 *     .cc("pmo@example.com")
 *     .subject("项目立项审批通知")
 *     .content("<h2>立项申请</h2><p>项目 XXX 已提交审批，请及时处理。</p>")
 *     .html(true)
 *     .attachment(EmailMessage.Attachment.builder()
 *         .filename("立项报告.pdf")
 *         .content(pdfBytes)
 *         .contentType("application/pdf")
 *         .build())
 *     .build();
 * NotifySendResult result = emailNotifySender.sendEmail(message);
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@Builder
public class EmailMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 收件人地址（支持多个，逗号分隔） */
	private String to;

	/** 抄送地址（支持多个，逗号分隔，可为空） */
	private String cc;

	/** 密送地址（支持多个，逗号分隔，可为空） */
	private String bcc;

	/** 回复地址（可为空，为空时使用发件人地址） */
	private String replyTo;

	/** 邮件主题 */
	private String subject;

	/** 邮件内容 */
	private String content;

	/** 是否 HTML 格式（true=HTML，false=纯文本，null=由配置决定） */
	private Boolean html;

	/** 附件列表 */
	private List<Attachment> attachments;

	/** 内联资源列表（用于 HTML 邮件中嵌入图片等，key=Content-ID） */
	private List<InlineResource> inlineResources;

	/** 自定义邮件头 */
	private Map<String, String> headers;

	/**
	 * 邮件附件
	 *
	 * @author Marvin Lee
	 * @email limw1888@126.com
	 * @version 3.5.0
	 * @since 1.0.0
	 */
	@Data
	@Builder
	public static class Attachment implements Serializable {

		private static final long serialVersionUID = 1L;

		/** 文件名 */
		private String filename;

		/** 文件内容（字节数组） */
		private byte[] content;

		/** MIME 类型（如 application/pdf，可为空自动推断） */
		private String contentType;
	}

	/**
	 * 内联资源（用于 HTML 邮件中通过 cid: 引用）
	 *
	 * @author Marvin Lee
	 * @email limw1888@126.com
	 * @version 3.5.0
	 * @since 1.0.0
	 */
	@Data
	@Builder
	public static class InlineResource implements Serializable {

		private static final long serialVersionUID = 1L;

		/** Content-ID（HTML 中通过 src="cid:contentId" 引用） */
		private String contentId;

		/** 资源内容（字节数组） */
		private byte[] content;

		/** MIME 类型（如 image/png） */
		private String contentType;
	}
}
