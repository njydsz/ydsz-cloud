package com.njydsz.common.notify.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 通知模块简化配置属性类
 *
 * <p>提供核心通知能力的精简配置入口，适用于仅需邮件和基础 IM 通知的场景。 高级功能（DKIM 签名、邮件追踪、渠道降级等）请直接使用 {@link NotifyProperties}。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify-lite:
 *     enabled: true
 *     email:
 *       enabled: true
 *       smtp-host: smtp.exmail.qq.com
 *       smtp-port: 465
 *       from-mail: noreply@ydsz.com
 *       from-name: ydsz项目管理平台
 *       password: your-password
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see NotifyProperties
 */
@Data
@ConfigurationProperties(prefix = "ydsz.notify-lite")
public class NotifyLiteProperties {

  /** 是否启用通知模块 */
  private boolean enabled = true;

  /** 邮件渠道配置 */
  private EmailConfig email = new EmailConfig();

  /** 企业微信渠道配置 */
  private WeComConfig wecom = new WeComConfig();

  /** 钉钉渠道配置 */
  private DingTalkConfig dingtalk = new DingTalkConfig();

  /** 飞书渠道配置 */
  private FeishuConfig feishu = new FeishuConfig();

  /** 邮件渠道简化配置 */
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

    /** 是否需要认证 */
    private boolean auth = true;

    /** 是否启用 STARTTLS */
    private boolean starttls;

    /** 默认是否以 HTML 模式发送 */
    private boolean htmlMode = true;

    /** 默认邮件主题前缀 */
    private String defaultSubjectPrefix = "";

    /** 最大附件总大小（MB） */
    private int maxAttachmentSizeMb = 20;

    /** 额外 JavaMail 属性 */
    private Map<String, String> properties = new HashMap<>();
  }

  /** 企业微信渠道简化配置 */
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
  }

  /** 钉钉渠道简化配置 */
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

  /** 飞书渠道简化配置 */
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
}
