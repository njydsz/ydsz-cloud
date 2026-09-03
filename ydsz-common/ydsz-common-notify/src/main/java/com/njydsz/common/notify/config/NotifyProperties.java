package com.njydsz.common.notify.config;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    /** SSL 配置 */
    private SslConfig ssl = new SslConfig();

    /** SSL 配置内部类 */
    @Data
    public static class SslConfig {
      /** 是否启用 SSL */
      private boolean enabled = true;

      /** SSL 协议版本 */
      private String protocols = "TLSv1.2";

      /** 是否验证服务端证书 */
      private boolean checkServerIdentity = true;

      /** 信任库路径 */
      private String trustStore;

      /** 信任库密码 */
      private String trustStorePassword;
    }
  }

  /**
   * 短信渠道配置
   *
   * <p><b>配置示例（application.yml）：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     sms:
   *       enabled: true
   *       endpoint: https://api.example.com/sms/send
   *       access-key-id: your-access-key-id
   *       access-key-secret: your-access-key-secret
   *       sign-name: ydsz科技
   *       template-code: SMS_123456
   * }</pre>
   */
  @Data
  public static class SmsConfig {

    /** 是否启用短信渠道 */
    private boolean enabled = false;

    /** 短信 API Endpoint */
    private String endpoint;

    /** Access Key ID（阿里云等） */
    private String accessKeyId;

    /** Access Key Secret（阿里云等） */
    private String accessKeySecret;

    /** 短信签名 */
    private String signName;

    /** 默认模板编码 */
    private String templateCode;

    /** 连接超时时间（毫秒） */
    private int connectionTimeout = 5000;

    /** 读取超时时间（毫秒） */
    private int readTimeout = 10000;
  }

  /**
   * 企业微信渠道配置
   *
   * <p><b>配置示例：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     wecom:
   *       enabled: true
   *       corp-id: wwxxxxxxxxxxxxxxxx
   *       corp-secret: your-corp-secret
   *       agent-id: 1000002
   *       default-party: 2
   * }</pre>
   */
  @Data
  public static class WeComConfig {

    /** 是否启用企业微信渠道 */
    private boolean enabled = false;

    /** 企业 ID */
    private String corpId;

    /** 应用密钥 */
    private String corpSecret;

    /** 应用 AgentId */
    private long agentId;

    /** 默认接收部门 */
    private String defaultParty;

    /** 默认接收人（逗号分隔 userid） */
    private String defaultUser;

    /** 是否开启消息内容校验 */
    private boolean enableMessageCheck = true;
  }

  /**
   * 钉钉渠道配置
   *
   * <p><b>配置示例：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     dingtalk:
   *       enabled: true
   *       app-key: your-app-key
   *       app-secret: your-app-secret
   *       agent-id: 123456
   * }</pre>
   */
  @Data
  public static class DingTalkConfig {

    /** 是否启用钉钉渠道 */
    private boolean enabled = false;

    /** 应用 AppKey */
    private String appKey;

    /** 应用 AppSecret */
    private String appSecret;

    /** 应用 AgentId */
    private long agentId;

    /** 是否使用机器人自定义消息 */
    private boolean useCustomRobot = false;

    /** 机器人 Webhook URL（自定义机器人时使用） */
    private String webhookUrl;
  }

  /**
   * 飞书渠道配置
   *
   * <p><b>配置示例：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     feishu:
   *       enabled: true
   *       app-id: your-app-id
   *       app-secret: your-app-secret
   *       verification-token: your-verification-token
   * }</pre>
   */
  @Data
  public static class FeishuConfig {

    /** 是否启用飞书渠道 */
    private boolean enabled = false;

    /** 应用 App ID */
    private String appId;

    /** 应用 App Secret */
    private String appSecret;

    /** 事件订阅 Verification Token */
    private String verificationToken;

    /** Encrypt Key（加密密钥，可选） */
    private String encryptKey;
  }

  /**
   * 站内信渠道配置
   *
   * <p><b>配置示例：</b>
   *
   * <pre>{@code
   * ydsz:
   *   notify:
   *     insite:
   *       enabled: true
   *       expire-days: 30
   *       max-per-user: 1000
   * }</pre>
   */
  @Data
  public static class InsiteConfig {

    /** 是否启用站内信渠道 */
    private boolean enabled = false;

    /** 站内信过期天数（0 表示永不过期） */
    private int expireDays = 30;

    /** 每用户最大站内信数量 */
    private int maxPerUser = 1000;

    /** 是否允许用户标记已读 */
    private boolean allowReadMark = true;
  }

  /** 渠道降级配置 */
  @Data
  public static class FallbackConfig {

    /** 是否启用渠道降级 */
    private boolean enabled = true;

    /** 降级到备用渠道的顺序（按优先级排列） */
    private List<String> channelOrder = List.of("email", "wecom", "dingtalk");

    /** 触发降级的连续失败次数阈值 */
    private int failureThreshold = 3;

    /** 熔断器快照时间窗口（秒） */
    private int circuitBreakerWindowSeconds = 60;
  }

  /** 去重配置 */
  @Data
  public static class DedupConfig {

    /** 是否启用消息去重 */
    private boolean enabled = true;

    /** 去重时间窗口（秒），在此时间内相同内容不重复发送 */
    private long dedupWindowSeconds = 300;

    /** Redis Key 前缀 */
    private String redisKeyPrefix = "notify:dedup:";
  }

  /** 限流配置 */
  public static class RateLimit {

    /** 是否启用渠道限流 */
    private boolean enabled = true;

    /** 每分钟最大发送条数（全局） */
    private int maxPerMinute = 1000;

    /** 每分钟每个接收者最大发送条数 */
    private int maxPerReceiverPerMinute = 10;

    /** 限流 Redis Key 前缀 */
    private String redisKeyPrefix = "notify:ratelimit:";

    /** 每个渠道默认最大请求次数（未单独配置渠道时生效） */
    private int defaultMaxRequests = 100;

    /** 每个渠道默认时间窗口（秒，未单独配置渠道时生效） */
    private long defaultWindowSeconds = 60;

    /** 各渠道自定义限流配置（key: NotifyChannel） */
    private Map<NotifyChannel, ChannelRateLimit> channelLimits;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxPerMinute() {
      return maxPerMinute;
    }

    public void setMaxPerMinute(int maxPerMinute) {
      this.maxPerMinute = maxPerMinute;
    }

    public int getMaxPerReceiverPerMinute() {
      return maxPerReceiverPerMinute;
    }

    public void setMaxPerReceiverPerMinute(int maxPerReceiverPerMinute) {
      this.maxPerReceiverPerMinute = maxPerReceiverPerMinute;
    }

    public String getRedisKeyPrefix() {
      return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
      this.redisKeyPrefix = redisKeyPrefix;
    }

    public int getDefaultMaxRequests() {
      return defaultMaxRequests;
    }

    public void setDefaultMaxRequests(int defaultMaxRequests) {
      this.defaultMaxRequests = defaultMaxRequests;
    }

    public long getDefaultWindowSeconds() {
      return defaultWindowSeconds;
    }

    public void setDefaultWindowSeconds(long defaultWindowSeconds) {
      this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public Map<NotifyChannel, ChannelRateLimit> getChannelLimits() {
      return channelLimits;
    }

    public void setChannelLimits(Map<NotifyChannel, ChannelRateLimit> channelLimits) {
      this.channelLimits = channelLimits;
    }
  }

  /** 渠道级限流配置 */
  public static class ChannelRateLimit {

    /** 该渠道最大请求次数 */
    private int maxRequests;

    /** 该渠道限流时间窗口（秒） */
    private long windowSeconds;

    // ==================== Getter / Setter ====================

    public int getMaxRequests() {
      return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
      this.maxRequests = maxRequests;
    }

    public long getWindowSeconds() {
      return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
      this.windowSeconds = windowSeconds;
    }
  }

  /** 定时任务配置 */
  @Data
  public static class SchedulerConfig {

    /** 是否启用定时任务 */
    private boolean enabled = true;

    /** 定时发送扫描间隔（毫秒） */
    private long scanIntervalMs = 60000;

    /** 批量读取大小 */
    private int batchSize = 100;

    /** 定时线程池大小 */
    private int threadPoolSize = 2;
  }
}
