package com.njydsz.pmis.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息引擎全局配置（prefix = {@code pmis.message}）。
 *
 * <p>绑定 {@code application.yml} 中 {@code pmis.message.*} 配置项，
 * 包含通道开关、默认优先级、聚合 / 重试扫描间隔、全局频率上限等。
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
}
