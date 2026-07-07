package com.njydsz.pmis.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息引擎全局配置（prefix = {@code pmis.message}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code pmis.message.*} 配置项，
 * 包含通道开关、默认优先级、聚合 / 重试扫描间隔、全局频率上限、
 * 多维度限流（P2-5: receiver/templateCode/tenant）等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "pmis.message")
public class MessageProperties {

    /** 通道全局开关：key 为通道大写名（SMS/EMAIL/...），value 为是否启用 */
    private Map<String, Boolean> channelEnabled;

    /** 默认发送优先级 */
    private String defaultPriority = "NORMAL";

    /** 聚合扫描间隔（毫秒） */
    private long aggregateScanIntervalMs = 60000L;

    /** 重试扫描间隔（毫秒） */
    private long retryScanIntervalMs = 30000L;

    /** 全局每日发送上限（单用户单通道，0 表示不限） */
    private int globalDailyLimit = 0;

    /** 全局每小时发送上限（单用户单通道，0 表示不限） */
    private int globalHourlyLimit = 0;

    /** P2-5: 多维度限流配置 */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 多维度限流配置（P2-5）。
     *
     * <p>支持 receiver / templateCode / tenant 三个维度的令牌桶限流，
     * 各维度独立配置 permits（每秒令牌数），任一维度超限即拒绝发送。
     * 维度间为 AND 关系：所有启用的维度都通过才允许发送。
     */
    @Data
    public static class RateLimitConfig {
        /** receiver 维度限流开关（避免同一接收人被轰炸） */
        private boolean receiverEnabled = true;
        /** receiver 维度每秒令牌数（同一 receiver 每秒最多发送条数） */
        private int receiverPermits = 10;

        /** templateCode 维度限流开关（避免单一模板占满配额） */
        private boolean templateEnabled = true;
        /** templateCode 维度每秒令牌数 */
        private int templatePermits = 100;

        /** tenant 维度限流开关（多租户配额隔离） */
        private boolean tenantEnabled = true;
        /** tenant 维度每秒令牌数 */
        private int tenantPermits = 1000;
    }
}
