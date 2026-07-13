package com.njydsz.pmis.common.notify.provider;

import com.njydsz.pmis.common.notify.channel.EmailMessage;
import com.njydsz.pmis.common.notify.core.NotifySendResult;

/**
 * 邮件发送提供商抽象接口（P2-10）
 *
 * <p>将邮件发送能力抽象为接口，支持多 SMTP 提供商（如腾讯企业邮箱、阿里云邮件推送、
 * AWS SES、SendGrid 等）的动态切换。
 *
 * <p>每个提供商实现此接口，通过 {@code ydsz.notify.email.provider} 配置项选择当前使用的提供商。
 * 在运行时可动态切换提供商，实现故障转移和灰度迁移。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component
 * public class TencentExmailProvider implements EmailProvider {
 *     @Override
 *     public String getName() { return "tencent-exmail"; }
 *
 *     @Override
 *     public NotifySendResult send(EmailMessage message) {
 *         // 腾讯企业邮箱发送实现
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface EmailProvider {

	/**
	 * 获取提供商名称
	 *
	 * @return 提供商唯一标识（如 "tencent-exmail"、"aliyun-dm"、"aws-ses"）
	 */
	String getName();

	/**
	 * 发送邮件
	 *
	 * @param message 邮件消息
	 * @return 发送结果
	 */
	NotifySendResult send(EmailMessage message);

	/**
	 * 检查提供商是否可用
	 *
	 * @return true 表示提供商配置完整且可用
	 */
	boolean isAvailable();

	/**
	 * 获取提供商优先级（数字越小优先级越高）
	 *
	 * <p>当多个提供商同时可用时，按优先级选择。
	 *
	 * @return 优先级
	 */
	default int getPriority() {
		return 100;
	}
}
