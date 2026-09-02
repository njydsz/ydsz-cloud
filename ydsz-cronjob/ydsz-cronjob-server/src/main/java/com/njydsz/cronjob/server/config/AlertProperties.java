package com.njydsz.cronjob.server.config;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 告警通知配置属性（P5 告警 + 监控）。
 *
 * <p>支持在 application.yml / Nacos 中通过 {@code ydsz.cronjob.alert.*} 前缀进行动态覆盖。
 *
 * <h3>通道配置</h3>
 *
 * <ul>
 *   <li>{@link #getEmail()} 邮件通道（SMTP 或 message-service 转发）
 *   <li>{@link #getDingtalk()} 钉钉群机器人
 *   <li>{@link #getWecom()} 企业微信群机器人
 *   <li>{@link #getWebhook()} 通用 Webhook
 *   <li>{@link #getFeishu()} 飞书群机器人（P1-5 新增）
 *   <li>{@link #getSms()} 短信通知（P1-5 新增）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ydsz.cronjob.alert")
public class AlertProperties {
  /** 默认 HTTP 超时：5 秒 */
  private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(5);


  /** 默认是否启用告警通道（false 时所有 Notifier 直接返回成功，用于本地开发） */
  private boolean enabled = true;

  /** HTTP 请求超时时间（连接 + 读取） */
  private Duration httpTimeout = DEFAULT_HTTP_TIMEOUT;

  /** 邮件通道配置 */
  private Email email = new Email();

  /** 钉钉通道配置 */
  private Dingtalk dingtalk = new Dingtalk();

  /** 企业微信通道配置 */
  private Wecom wecom = new Wecom();

  /** 通用 Webhook 通道配置 */
  private Webhook webhook = new Webhook();

  /** 飞书通道配置（P1-5 新增） */
  private Feishu feishu = new Feishu();

  /** 短信通道配置（P1-5 新增） */
  private Sms sms = new Sms();

  /** 邮件通道配置。 */
  @Data
  public static class Email {
    /** 是否启用邮件通道 */
    private boolean enabled = true;

    /** 发件人邮箱地址（如 alert@ydszsoft.com） */
    private String from = "alert@ydszsoft.com";

    /** 邮件服务转发 URL（NULL 时尝试本地 SMTP） */
    private String serviceUrl;

    /** 邮件主题前缀（如 [YDSZ 告警]） */
    private String subjectPrefix = "[YDSZ 告警]";
  }

  /** 钉钉群机器人配置。 */
  @Data
  public static class Dingtalk {
    /** 是否启用钉钉通道 */
    private boolean enabled = true;

    /** 钉钉机器人 Webhook URL（如 https://oapi.dingtalk.com/robot/send?access_token=xxx） */
    private String webhookUrl;

    /** 钉钉机器人加签密钥（可选，用于安全设置） */
    private String secret;
  }

  /** 企业微信群机器人配置。 */
  @Data
  public static class Wecom {
    /** 是否启用企业微信通道 */
    private boolean enabled = true;

    /** 企业微信机器人 Webhook URL（如 https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx） */
    private String webhookUrl;
  }

  /** 通用 Webhook 配置。 */
  @Data
  public static class Webhook {
    /** 是否启用 Webhook 通道 */
    private boolean enabled = true;

    /** Webhook URL（业务系统自行实现接收逻辑） */
    private String webhookUrl;

    /** 自定义请求头（JSON，如 {"Authorization":"Bearer xxx"}） */
    private String headers;
  }

  /**
   * 飞书群机器人配置（P1-5 新增）。
   *
   * <p>通过飞书自定义机器人 Webhook 推送 interactive card 消息。 默认禁用，需显式设置 {@code enabled=true} 并配置 webhook-url
   * 后启用。
   */
  @Data
  public static class Feishu {
    /** 是否启用飞书通道（默认禁用） */
    private boolean enabled = false;

    /** 飞书机器人 Webhook URL（如 https://open.feishu.cn/open-apis/bot/v2/hook/xxx） */
    private String webhookUrl;
  }

  /**
   * 短信通道配置（P1-5 新增）。
   *
   * <p>简化实现：通过 HTTP Webhook URL 转发短信通知，由业务侧（如 message-service） 调用阿里云/腾讯云短信 API 实际发送，避免 cronjob
   * 模块直接依赖短信 SDK。 默认禁用，需显式设置 {@code enabled=true} 并配置 webhook-url 后启用。
   */
  @Data
  public static class Sms {
    /** 是否启用短信通道（默认禁用） */
    private boolean enabled = false;

    /** 短信转发 Webhook URL（由 message-service 或第三方短信网关提供） */
    private String webhookUrl;

    /** 默认接收手机号列表（逗号分隔，如 13800000000,13900000000） */
    private String phoneNumbers;
  }
}
