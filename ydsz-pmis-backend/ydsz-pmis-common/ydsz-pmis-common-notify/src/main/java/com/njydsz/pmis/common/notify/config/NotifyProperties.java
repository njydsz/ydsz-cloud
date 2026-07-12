package com.njydsz.pmis.common.notify.config;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知模块配置属性类
 *
 * <p>绑定 application.yml 中 pmis.notify 前缀的配置项，
 * 支持邮件、短信、企业微信、钉钉、飞书、站内信等多种通知渠道的配置。</p>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.notify")
public class NotifyProperties {

	/** 是否启用通知模块 */
	private boolean enabled = true;

	/** 邮件渠道配置 */
	private EmailConfig email = new EmailConfig();

	/** 短信渠道配置 */
	private SmsConfig sms = new SmsConfig();

	/** 企业微信渠道配置 */
	private WeComConfig wecom = new WeComConfig();

	/** 钉钉渠道配置 */
	private DingTalkConfig dingtalk = new DingTalkConfig();

	/** 飞书渠道配置 */
	private FeishuConfig feishu = new FeishuConfig();

	/** 站内信渠道配置 */
	private InsiteConfig insite = new InsiteConfig();

	/** 重试队列配置 */
	private RetryQueueConfig retryQueue = new RetryQueueConfig();

	/** 限流配置 */
	private RateLimit rateLimit = new RateLimit();

	/**
	 * 重试队列配置
	 */
	@Data
	public static class RetryQueueConfig {

		/** 队列容量 */
		@Min(1)
		private int capacity = 10000;

		/** 最大重试次数 */
		@Min(0)
		@Max(10)
		private int maxRetries = 5;

		/** 批量处理大小 */
		@Min(1)
		private int batchSize = 100;

		/** 是否使用 Redis 持久化重试队列，开启后服务重启不会丢失待重试消息 */
		private boolean persistent = false;

		/** Redis Key 前缀 */
		private String redisKeyPrefix = "notify:retry:";
	}

	/**
	 * 邮件渠道配置
	 */
	@Data
	public static class EmailConfig {

		/** 是否启用邮件渠道 */
		private boolean enabled = true;

		/** SMTP 主机地址 */
		private String smtpHost;

		/** SMTP 端口 */
		private int smtpPort = 465;

		/** 发件人邮箱地址 */
		private String fromMail;

		/** 发件人显示名称 */
		private String fromName;

		/** 邮箱密码/授权码 */
		private String password;

		/** 连接超时时间（毫秒） */
		private int connectionTimeout = 10000;

		/** 读取超时时间（毫秒） */
		private int timeout = 10000;

		/** 写入超时时间（毫秒） */
		private int writeTimeout = 10000;

		/** 是否需要认证 */
		private boolean auth = true;

		/** 是否启用 STARTTLS */
		private boolean starttls;

		/** 是否启用调试模式 */
		private boolean debug;

		/** 编码格式 */
		private String encoding = "UTF-8";

		/** SSL 配置 */
		private SslConfig ssl = new SslConfig();
	}

	/**
	 * SSL 配置
	 */
	@Data
	public static class SslConfig {

		/** 是否启用 SSL */
		private boolean enabled = true;

		/** SSL 端口 */
		private Integer sslPort = 465;

		/** SSL 协议版本 */
		private String protocols = "TLSv1.2";

		/** 是否校验服务器身份 */
		private boolean checkServerIdentity = true;

		/** 信任存储路径 */
		private String trustStorePath;
	}

	/**
	 * 短信渠道配置
	 */
	@Data
	public static class SmsConfig {

		/** 是否启用短信渠道 */
		private boolean enabled;

		/** 短信服务提供商 */
		private String provider = "aliyun";

		/** AccessKey ID */
		private String accessKeyId;

		/** AccessKey Secret */
		private String accessKeySecret;

		/** 短信签名 */
		private String signName;

		/** 短信模板映射，key=模板编码，value=模板ID */
		private Map<String, String> templates = new HashMap<>();
	}

	/**
	 * 企业微信渠道配置
	 */
	@Data
	public static class WeComConfig {

		/** 是否启用企业微信渠道 */
		private boolean enabled;

		/** 企业ID */
		private String corpId;

		/** 企业密钥 */
		private String corpSecret;

		/** 应用ID */
		private String agentId;

		/** Webhook 密钥 */
		private String webhookKey;

		/** Token 刷新间隔（秒） */
		private long tokenRefreshInterval = 7200;
	}

	/**
	 * 钉钉渠道配置
	 */
	@Data
	public static class DingTalkConfig {

		/** 是否启用钉钉渠道 */
		private boolean enabled;

		/** 应用 Key */
		private String appKey;

		/** 应用密钥 */
		private String appSecret;

		/** Webhook 地址 */
		private String webhookUrl;

		/** Webhook 签名密钥 */
		private String webhookSecret;
	}

	/**
	 * 飞书渠道配置
	 */
	@Data
	public static class FeishuConfig {

		/** 是否启用飞书渠道 */
		private boolean enabled;

		/** 应用 ID */
		private String appId;

		/** 应用密钥 */
		private String appSecret;

		/** Webhook 地址 */
		private String webhookUrl;

		/** 加密密钥 */
		private String encryptKey;
	}

	/**
	 * 站内信渠道配置
	 */
	@Data
	public static class InsiteConfig {

		/** 是否启用站内信渠道 */
		private boolean enabled = true;

		/** 存储类型 */
		private String storageType = "redis";

		/** 最大队列大小 */
		private int maxQueueSize = 10000;

		/** 过期时间（分钟） */
		private long expireMinutes = 1440;
	}

	/**
	 * 限流配置
	 *
	 * <p>控制通知发送频率，防止渠道过载。
	 *
	 * <p><b>配置示例（application.yml）：</b>
	 * <pre>{@code
	 * remi:
	 *   notify:
	 *     rate-limit:
	 *       enabled: true
	 *       default-max-requests: 100
	 *       default-window-seconds: 60
	 *       channel-limits:
	 *         EMAIL:
	 *           max-requests: 200
	 *           window-seconds: 60
	 *         SMS:
	 *           max-requests: 50
	 *           window-seconds: 60
	 *         WECOM:
	 *           max-requests: 100
	 *           window-seconds: 60
	 * }</pre>
	 */
	@Getter
	@Setter
	public static class RateLimit {

		/** 是否启用限流 */
		private boolean enabled = true;

		/** 默认最大请求数（每个渠道） */
		@Min(1)
		private int defaultMaxRequests = 100;

		/** 默认时间窗口（秒） */
		@Min(1)
		private int defaultWindowSeconds = 60;

		/** 渠道级限流配置，key=渠道枚举，value=渠道限流配置 */
		private Map<NotifyChannel, ChannelRateLimit> channelLimits = new HashMap<>();
	}

	/**
	 * 渠道级限流配置
	 */
	@Getter
	@Setter
	public static class ChannelRateLimit {

		/** 最大请求数 */
		@Min(1)
		private int maxRequests = 100;

		/** 时间窗口（秒） */
		@Min(1)
		private int windowSeconds = 60;
	}
}
