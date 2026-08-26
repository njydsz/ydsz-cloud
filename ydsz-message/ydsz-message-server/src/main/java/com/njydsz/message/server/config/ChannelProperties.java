package com.njydsz.message.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通道相关配置（prefix = {@code ydsz}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code ydsz.webhook.*} 与 {@code ydsz.channel.*} 配置项， 覆盖 Webhook / HMAC / 企业微信 / Post 群机器人的默认地址、密钥与超时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ydsz")
public class ChannelProperties {

  /** 默认connectTimeout值（可被配置文件覆盖） */
  private static final int DEFAULT_CONNECT_TIMEOUT = 5000;

  /** 默认readTimeout值（可被配置文件覆盖） */
  private static final int DEFAULT_READ_TIMEOUT = 10000;

  /** Webhook 通道兜底配置 */
  private WebhookConfig webhook = new WebhookConfig();

  /** 群机器人通道配置组 */
  private ChannelGroup channel = new ChannelGroup();

  /** Webhook 通道配置。 */
  @Data
  public static class WebhookConfig {
    /** 默认 Webhook URL（兜底） */
    private String defaultUrl = "";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;

    /**
     * 签名密钥（可选，P1-F1 新增）。
     *
     * <p>配置后在发送请求时自动添加 HMAC-SHA256 签名头：
     *
     * <ul>
     *   <li>签名内容：timestamp + "\n" + secret
     *   <li>请求头：X-Webhook-Timestamp / X-Webhook-Signature
     * </ul>
     *
     * 单次发送时也可通过 {@code params.webhookSecret} 覆盖。
     */
    private String secret = "";
  }

  /** 群机器人通道配置组。 */
  @Data
  public static class ChannelGroup {
    /** HMAC 签名群机器人配置 */
    private HmacConfig hmac = new HmacConfig();

    /** P0-2: HMAC 工作通知(企业内部应用)配置 */
    private HmacWorkConfig hmacWork = new HmacWorkConfig();

    /** 企业微信群机器人配置 */
    private WechatWorkConfig wechatWork = new WechatWorkConfig();

    /** P0-2: 企业微信应用消息(企业内部应用)配置 */
    private WeComAppConfig wecomApp = new WeComAppConfig();

    /** Post 消息群机器人配置 */
    private PostConfig post = new PostConfig();
  }

  /** HMAC 签名群机器人配置。 */
  @Data
  public static class HmacConfig {
    /** 默认 access_token（兜底） */
    private String defaultToken = "";

    /** 加签密钥（可选，配置后启用加签安全模式） */
    private String secret = "";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /**
   * P0-2: HMAC 工作通知(企业内部应用)配置。
   *
   * <p>通过开放平台企业内部应用发送工作通知,需要:
   *
   * <ul>
   *   <li>AppKey + AppSecret → 获取 access_token
   *   <li>AgentId → 企业应用 ID
   *   <li>receiver 为 userId
   * </ul>
   *
   * access_token 缓存在 Redis,有效期 7200s,提前 300 s 续期。
   */
  @Data
  public static class HmacWorkConfig {
    /** 是否启用工作通知通道(未配置 AppKey 时降级 mock) */
    private boolean enabled = false;

    /** 应用 AppKey */
    private String appKey;

    /** 应用 AppSecret */
    private String appSecret;

    /** 应用 AgentId */
    private Long agentId;

    /** API base URL */
    private String baseUrl = "https://oapi.example.com";

    /** 连接超时(毫秒) */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时(毫秒) */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /** 企业微信群机器人配置。 */
  @Data
  public static class WechatWorkConfig {
    /** 默认 key（兜底） */
    private String defaultKey = "";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /**
   * P0-2: 企业微信应用消息(企业内部应用)配置。
   *
   * <p>通过企业微信开放平台企业内部应用发送应用消息,需要:
   *
   * <ul>
   *   <li>CorpID + CorpSecret → 获取 access_token
   *   <li>AgentId → 企业应用 ID
   *   <li>receiver 为企业微信 userId
   * </ul>
   *
   * access_token 缓存在 Redis,有效期 7200s,提前 300s 续期。
   */
  @Data
  public static class WeComAppConfig {
    /** 是否启用企微应用消息通道(未配置 CorpID 时降级 mock) */
    private boolean enabled = false;

    /** 企业微信 CorpID */
    private String corpId;

    /** 企业微信应用 Secret */
    private String corpSecret;

    /** 企业微信应用 AgentId */
    private Integer agentId;

    /** 企业微信 API base URL */
    private String baseUrl = "https://qyapi.weixin.qq.com";

    /** 连接超时(毫秒) */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时(毫秒) */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /** Post 消息群机器人配置。 */
  @Data
  public static class PostConfig {
    /** 默认 hook（兜底，可为完整 URL 或 hook ID） */
    private String defaultHook = "";

    /** 加签密钥（可选） */
    private String secret = "";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }
}
