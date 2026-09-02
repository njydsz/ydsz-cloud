package com.njydsz.common.notify.config.NotifyProperties;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 通知模块配置属性类
 *
 * <p>绑定 application.yml 中 ydsz.notify 前缀的配置项， 支持邮件、短信、企业微信、钉钉、飞书、站内信等多种通知渠道的配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.notify")
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

  /** 渠道降级配置 */
  private FallbackConfig fallback = new FallbackConfig();

  /** 去重配置 */
  private DedupConfig dedup = new DedupConfig();

  /** 重试队列配置 */
  private RetryQueueConfig retryQueue = new RetryQueueConfig();

  /** 限流配置 */
  private RateLimit rateLimit = new RateLimit();

  /** 定时任务配置 */
  private SchedulerConfig scheduler = new SchedulerConfig();

  /** 重试队列配置 */
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
   *
   * <p><b>配置示例（application.yml）：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     email:
   *       enabled: true
   *       smtp-host: smtp.exmail.qq.com
   *       smtp-port: 465
   *       from-mail: noreply@ydsz.com
   *       from-name: ydsz项目管理平台
   *       password: your-password-or-auth-code
   *       auth: true
   *       starttls: false
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

    /** 默认是否以 HTML 模式发送（false 时发送纯文本） */
    private boolean htmlMode = true;

    /** 默认邮件主题前缀，如 "【ydsz项目管理】" */
    private String defaultSubjectPrefix = "";

    /** 默认抄送地址（逗号分隔多个地址） */
    private String cc;

    /** 默认密送地址（逗号分隔多个地址） */
    private String bcc;

    /** 默认回复地址 */
    private String replyTo;

    /** 最大附件总大小（MB），超过则拒绝发送 */
    private int maxAttachmentSizeMb = 20;

    /** 额外 JavaMail 属性（如 mail.smtp.quitwait、mail.smtp.localhost 等） */
    private Map<String, String> properties = new HashMap<>(16);