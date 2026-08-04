package com.remisoft.message.server.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 通道相关配置（prefix = {@code remi}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code remi.webhook.*} 与 {@code remi.channel.*} 配置项，
 * 覆盖 Webhook / 钉钉 / 企业微信 / 飞书群机器人的默认地址、密钥与超时。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "remi")
public class ChannelProperties {

    /** Webhook 通道兜底配置 */
    private WebhookConfig webhook = new WebhookConfig();

    /** 群机器人通道配置组 */
    private ChannelGroup channel = new ChannelGroup();

    /**
     * Webhook 通道配置。
     */
    @Data
    public static class WebhookConfig {
        /** 默认 Webhook URL（兜底） */
        private String defaultUrl = "";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /**
     * 群机器人通道配置组。
     */
    @Data
    public static class ChannelGroup {
        /** 钉钉群机器人配置 */
        private DingTalkConfig dingtalk = new DingTalkConfig();
        /** P0-2: 钉钉工作通知(企业内部应用)配置 */
        private DingTalkWorkConfig dingtalkWork = new DingTalkWorkConfig();
        /** 企业微信群机器人配置 */
        private WechatWorkConfig wechatWork = new WechatWorkConfig();
        /** P0-2: 企业微信应用消息(企业内部应用)配置 */
        private WeComAppConfig wecomApp = new WeComAppConfig();
        /** 飞书群机器人配置 */
        private FeishuConfig feishu = new FeishuConfig();
    }

    /**
     * 钉钉群机器人配置。
     */
    @Data
    public static class DingTalkConfig {
        /** 默认 access_token（兜底） */
        private String defaultToken = "";
        /** 加签密钥（可选，配置后启用加签安全模式） */
        private String secret = "";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /**
     * P0-2: 钉钉工作通知(企业内部应用)配置。
     *
     * <p>通过钉钉开放平台企业内部应用发送工作通知,需要:
     * <ul>
     *   <li>AppKey + AppSecret → 获取 access_token</li>
     *   <li>AgentId → 企业应用 ID</li>
     *   <li>receiver 为钉钉 userId</li>
     * </ul>
     * access_token 缓存在 Redis,有效期 7200s,提前 300s 续期。
     */
    @Data
    public static class DingTalkWorkConfig {
        /** 是否启用工作通知通道(未配置 AppKey 时降级 mock) */
        private boolean enabled = false;
        /** 钉钉应用 AppKey */
        private String appKey;
        /** 钉钉应用 AppSecret */
        private String appSecret;
        /** 钉钉应用 AgentId */
        private Long agentId;
        /** 钉钉 API base URL */
        private String baseUrl = "https://oapi.dingtalk.com";
        /** 连接超时(毫秒) */
        private int connectTimeout = 5000;
        /** 读取超时(毫秒) */
        private int readTimeout = 10000;
    }

    /**
     * 企业微信群机器人配置。
     */
    @Data
    public static class WechatWorkConfig {
        /** 默认 key（兜底） */
        private String defaultKey = "";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }

    /**
     * P0-2: 企业微信应用消息(企业内部应用)配置。
     *
     * <p>通过企业微信开放平台企业内部应用发送应用消息,需要:
     * <ul>
     *   <li>CorpID + CorpSecret → 获取 access_token</li>
     *   <li>AgentId → 企业应用 ID</li>
     *   <li>receiver 为企业微信 userId</li>
     * </ul>
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
        private int connectTimeout = 5000;
        /** 读取超时(毫秒) */
        private int readTimeout = 10000;
    }

    /**
     * 飞书群机器人配置。
     */
    @Data
    public static class FeishuConfig {
        /** 默认 hook（兜底，可为完整 URL 或 hook ID） */
        private String defaultHook = "";
        /** 加签密钥（可选） */
        private String secret = "";
        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;
        /** 读取超时（毫秒） */
        private int readTimeout = 10000;
    }
}
