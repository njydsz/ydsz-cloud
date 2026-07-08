package com.njydsz.pmis.message.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通道相关配置（prefix = {@code pmis}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code pmis.webhook.*} 与 {@code pmis.channel.*} 配置项，
 * 覆盖 Webhook / 钉钉 / 企业微信 / 飞书群机器人的默认地址、密钥与超时。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "pmis")
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
        /** 企业微信群机器人配置 */
        private WechatWorkConfig wechatWork = new WechatWorkConfig();
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
