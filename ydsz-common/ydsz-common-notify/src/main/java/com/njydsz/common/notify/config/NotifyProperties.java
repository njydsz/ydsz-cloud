package com.njydsz.common.notify.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

  // ==================== Getter / Setter ====================

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public EmailConfig getEmail() {
    return email;
  }

  public void setEmail(EmailConfig email) {
    this.email = email;
  }

  public SmsConfig getSms() {
    return sms;
  }

  public void setSms(SmsConfig sms) {
    this.sms = sms;
  }

  public WeComConfig getWecom() {
    return wecom;
  }

  public void setWecom(WeComConfig wecom) {
    this.wecom = wecom;
  }

  public DingTalkConfig getDingtalk() {
    return dingtalk;
  }

  public void setDingtalk(DingTalkConfig dingtalk) {
    this.dingtalk = dingtalk;
  }

  public FeishuConfig getFeishu() {
    return feishu;
  }

  public void setFeishu(FeishuConfig feishu) {
    this.feishu = feishu;
  }

  public InsiteConfig getInsite() {
    return insite;
  }

  public void setInsite(InsiteConfig insite) {
    this.insite = insite;
  }

  public FallbackConfig getFallback() {
    return fallback;
  }

  public void setFallback(FallbackConfig fallback) {
    this.fallback = fallback;
  }

  public DedupConfig getDedup() {
    return dedup;
  }

  public void setDedup(DedupConfig dedup) {
    this.dedup = dedup;
  }

  public RetryQueueConfig getRetryQueue() {
    return retryQueue;
  }

  public void setRetryQueue(RetryQueueConfig retryQueue) {
    this.retryQueue = retryQueue;
  }

  public RateLimit getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(RateLimit rateLimit) {
    this.rateLimit = rateLimit;
  }

  public SchedulerConfig getScheduler() {
    return scheduler;
  }

  public void setScheduler(SchedulerConfig scheduler) {
    this.scheduler = scheduler;
  }

  // ==================== Inner Classes ====================

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
   *       security:
   *         sanitize-html: true
   *         list-unsubscribe: https://ydsz.com/unsubscribe
   *         jasypt-key: your-jasypt-key
   *       tracking:
   *         enabled: true
   *         pixel-base-url: https://ydsz.com/api/notify/track/open
   *       dkim:
   *         enabled: true
   *         domain: ydsz.com
   *         selector: default
   *         private-key: "MIIEvQIBADANBgkqh..."
   * }</pre>
   */
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

    /** 邮件安全配置（XSS 清洗、退订头、加密密钥） */
    private SecurityConfig security;

    /** 邮件追踪配置（已读回执像素） */
    private TrackingConfig tracking;

    /** DKIM 签名配置 */
    private DkimConfig dkim;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSmtpHost() {
      return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
      this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
      return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
      this.smtpPort = smtpPort;
    }

    public String getFromMail() {
      return fromMail;
    }

    public void setFromMail(String fromMail) {
      this.fromMail = fromMail;
    }

    public String getFromName() {
      return fromName;
    }

    public void setFromName(String fromName) {
      this.fromName = fromName;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public int getConnectionTimeout() {
      return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
    }

    public int getTimeout() {
      return timeout;
    }

    public void setTimeout(int timeout) {
      this.timeout = timeout;
    }

    public int getWriteTimeout() {
      return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
      this.writeTimeout = writeTimeout;
    }

    public boolean isAuth() {
      return auth;
    }

    public void setAuth(boolean auth) {
      this.auth = auth;
    }

    public boolean isStarttls() {
      return starttls;
    }

    public void setStarttls(boolean starttls) {
      this.starttls = starttls;
    }

    public boolean isDebug() {
      return debug;
    }

    public void setDebug(boolean debug) {
      this.debug = debug;
    }

    public String getEncoding() {
      return encoding;
    }

    public void setEncoding(String encoding) {
      this.encoding = encoding;
    }

    public boolean isHtmlMode() {
      return htmlMode;
    }

    public void setHtmlMode(boolean htmlMode) {
      this.htmlMode = htmlMode;
    }

    public String getDefaultSubjectPrefix() {
      return defaultSubjectPrefix;
    }

    public void setDefaultSubjectPrefix(String defaultSubjectPrefix) {
      this.defaultSubjectPrefix = defaultSubjectPrefix;
    }

    public String getCc() {
      return cc;
    }

    public void setCc(String cc) {
      this.cc = cc;
    }

    public String getBcc() {
      return bcc;
    }

    public void setBcc(String bcc) {
      this.bcc = bcc;
    }

    public String getReplyTo() {
      return replyTo;
    }

    public void setReplyTo(String replyTo) {
      this.replyTo = replyTo;
    }

    public int getMaxAttachmentSizeMb() {
      return maxAttachmentSizeMb;
    }

    public void setMaxAttachmentSizeMb(int maxAttachmentSizeMb) {
      this.maxAttachmentSizeMb = maxAttachmentSizeMb;
    }

    public Map<String, String> getProperties() {
      return properties;
    }

    public void setProperties(Map<String, String> properties) {
      this.properties = properties;
    }

    public SslConfig getSsl() {
      return ssl;
    }

    public void setSsl(SslConfig ssl) {
      this.ssl = ssl;
    }

    public SecurityConfig getSecurity() {
      return security;
    }

    public void setSecurity(SecurityConfig security) {
      this.security = security;
    }

    public TrackingConfig getTracking() {
      return tracking;
    }

    public void setTracking(TrackingConfig tracking) {
      this.tracking = tracking;
    }

    public DkimConfig getDkim() {
      return dkim;
    }

    public void setDkim(DkimConfig dkim) {
      this.dkim = dkim;
    }
  }

  /** SSL 配置内部类 */
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

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getProtocols() {
      return protocols;
    }

    public void setProtocols(String protocols) {
      this.protocols = protocols;
    }

    public boolean isCheckServerIdentity() {
      return checkServerIdentity;
    }

    public void setCheckServerIdentity(boolean checkServerIdentity) {
      this.checkServerIdentity = checkServerIdentity;
    }

    public String getTrustStore() {
      return trustStore;
    }

    public void setTrustStore(String trustStore) {
      this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
      return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
    }
  }

  /** 邮件安全配置（XSS 清洗、退订头、加密密钥） */
  public static class SecurityConfig {
    /** 是否对 HTML 邮件内容执行 XSS 清洗 */
    private boolean sanitizeHtml = true;

    /** List-Unsubscribe 退订头 URL */
    private String listUnsubscribe;

    /** Jasypt 加密密钥（用于 SMTP 密码解密） */
    private String jasyptKey;

    // ==================== Getter / Setter ====================

    public boolean isSanitizeHtml() {
      return sanitizeHtml;
    }

    public void setSanitizeHtml(boolean sanitizeHtml) {
      this.sanitizeHtml = sanitizeHtml;
    }

    public String getListUnsubscribe() {
      return listUnsubscribe;
    }

    public void setListUnsubscribe(String listUnsubscribe) {
      this.listUnsubscribe = listUnsubscribe;
    }

    public String getJasyptKey() {
      return jasyptKey;
    }

    public void setJasyptKey(String jasyptKey) {
      this.jasyptKey = jasyptKey;
    }
  }

  /** 邮件追踪配置（已读回执像素） */
  public static class TrackingConfig {
    /** 是否启用邮件追踪 */
    private boolean enabled;

    /** 追踪像素基础 URL */
    private String pixelBaseUrl;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getPixelBaseUrl() {
      return pixelBaseUrl;
    }

    public void setPixelBaseUrl(String pixelBaseUrl) {
      this.pixelBaseUrl = pixelBaseUrl;
    }
  }

  /** DKIM 签名配置 */
  public static class DkimConfig {
    /** 是否启用 DKIM 签名 */
    private boolean enabled;

    /** 签名域名 */
    private String domain;

    /** 选择器 */
    private String selector;

    /** RSA 私钥（PEM 格式，Base64 编码） */
    private String privateKey;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getDomain() {
      return domain;
    }

    public void setDomain(String domain) {
      this.domain = domain;
    }

    public String getSelector() {
      return selector;
    }

    public void setSelector(String selector) {
      this.selector = selector;
    }

    public String getPrivateKey() {
      return privateKey;
    }

    public void setPrivateKey(String privateKey) {
      this.privateKey = privateKey;
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

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
      return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
      return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
      this.accessKeySecret = accessKeySecret;
    }

    public String getSignName() {
      return signName;
    }

    public void setSignName(String signName) {
      this.signName = signName;
    }

    public String getTemplateCode() {
      return templateCode;
    }

    public void setTemplateCode(String templateCode) {
      this.templateCode = templateCode;
    }

    public int getConnectionTimeout() {
      return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
      this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
    }
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

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getCorpId() {
      return corpId;
    }

    public void setCorpId(String corpId) {
      this.corpId = corpId;
    }

    public String getCorpSecret() {
      return corpSecret;
    }

    public void setCorpSecret(String corpSecret) {
      this.corpSecret = corpSecret;
    }

    public long getAgentId() {
      return agentId;
    }

    public void setAgentId(long agentId) {
      this.agentId = agentId;
    }

    public String getDefaultParty() {
      return defaultParty;
    }

    public void setDefaultParty(String defaultParty) {
      this.defaultParty = defaultParty;
    }

    public String getDefaultUser() {
      return defaultUser;
    }

    public void setDefaultUser(String defaultUser) {
      this.defaultUser = defaultUser;
    }

    public boolean isEnableMessageCheck() {
      return enableMessageCheck;
    }

    public void setEnableMessageCheck(boolean enableMessageCheck) {
      this.enableMessageCheck = enableMessageCheck;
    }
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

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getAppKey() {
      return appKey;
    }

    public void setAppKey(String appKey) {
      this.appKey = appKey;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public long getAgentId() {
      return agentId;
    }

    public void setAgentId(long agentId) {
      this.agentId = agentId;
    }

    public boolean isUseCustomRobot() {
      return useCustomRobot;
    }

    public void setUseCustomRobot(boolean useCustomRobot) {
      this.useCustomRobot = useCustomRobot;
    }

    public String getWebhookUrl() {
      return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
    }
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

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public String getVerificationToken() {
      return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
      this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
      return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
      this.encryptKey = encryptKey;
    }
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
  public static class InsiteConfig {

    /** 是否启用站内信渠道 */
    private boolean enabled = false;

    /** 站内信过期天数（0 表示永不过期） */
    private int expireDays = 30;

    /** 每用户最大站内信数量 */
    private int maxPerUser = 1000;

    /** 是否允许用户标记已读 */
    private boolean allowReadMark = true;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getExpireDays() {
      return expireDays;
    }

    public void setExpireDays(int expireDays) {
      this.expireDays = expireDays;
    }

    /**
     * 过期分钟数（别名，= expireDays * 24 * 60）
     *
     * @return 过期分钟数
     */
    public int getExpireMinutes() {
      return expireDays * 24 * 60;
    }

    public int getMaxPerUser() {
      return maxPerUser;
    }

    public void setMaxPerUser(int maxPerUser) {
      this.maxPerUser = maxPerUser;
    }

    /**
     * 每用户最大队列长度（别名，等同于 maxPerUser）
     *
     * @return 每用户最大站内信数量
     */
    public int getMaxQueueSize() {
      return maxPerUser;
    }

    public boolean isAllowReadMark() {
      return allowReadMark;
    }

    public void setAllowReadMark(boolean allowReadMark) {
      this.allowReadMark = allowReadMark;
    }
  }

  /** 渠道降级配置 */
  public static class FallbackConfig {

    /** 是否启用渠道降级 */
    private boolean enabled = true;

    /** 降级链配置（key: 主渠道, value: 按优先级排序的备用渠道列表） */
    private Map<NotifyChannel, List<NotifyChannel>> chains;

    /** 触发降级的连续失败次数阈值 */
    private int failureThreshold = 3;

    /** 熔断器快照时间窗口（秒） */
    private int circuitBreakerWindowSeconds = 60;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Map<NotifyChannel, List<NotifyChannel>> getChains() {
      return chains;
    }

    public void setChains(Map<NotifyChannel, List<NotifyChannel>> chains) {
      this.chains = chains;
    }

    public int getFailureThreshold() {
      return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
      this.failureThreshold = failureThreshold;
    }

    public int getCircuitBreakerWindowSeconds() {
      return circuitBreakerWindowSeconds;
    }

    public void setCircuitBreakerWindowSeconds(int circuitBreakerWindowSeconds) {
      this.circuitBreakerWindowSeconds = circuitBreakerWindowSeconds;
    }
  }

  /** 去重配置 */
  public static class DedupConfig {

    /** 是否启用消息去重 */
    private boolean enabled = true;

    /** 去重时间窗口（秒），在此时间内相同内容不重复发送 */
    private long windowSeconds = 300;

    /** Redis Key 前缀 */
    private String redisKeyPrefix = "notify:dedup:";

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getWindowSeconds() {
      return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
      this.windowSeconds = windowSeconds;
    }

    public String getRedisKeyPrefix() {
      return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
      this.redisKeyPrefix = redisKeyPrefix;
    }
  }

  /** 重试队列配置 */
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

    // ==================== Getter / Setter ====================

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int capacity) {
      this.capacity = capacity;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public boolean isPersistent() {
      return persistent;
    }

    public void setPersistent(boolean persistent) {
      this.persistent = persistent;
    }

    public String getRedisKeyPrefix() {
      return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
      this.redisKeyPrefix = redisKeyPrefix;
    }
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
  public static class SchedulerConfig {

    /** 是否启用定时任务 */
    private boolean enabled = true;

    /** 定时发送扫描间隔（毫秒） */
    private long scanIntervalMs = 60000;

    /** 批量读取大小 */
    private int batchSize = 100;

    /** 定时线程池大小 */
    private int threadPoolSize = 2;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getScanIntervalMs() {
      return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
      this.scanIntervalMs = scanIntervalMs;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getThreadPoolSize() {
      return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
      this.threadPoolSize = threadPoolSize;
    }
  }
}
