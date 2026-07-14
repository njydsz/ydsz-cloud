package com.njydsz.pmis.common.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Outbox 事件模块配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pmis.event.outbox")
public class EventProperties {

    /** 是否启用 Outbox 模式 */
    private boolean enabled = true;

    /** Outbox 表名 */
    private String tableName = "pmis_outbox";

    /** 轮询间隔（秒） */
    private long pollIntervalSeconds = 5;

    /** 每批最大条数 */
    private int batchSize = 100;

    /** 默认最大重试次数 */
    private int maxRetries = 5;

    /** 基础退避秒数 */
    private long baseBackoffSeconds = 10;

    /** 最大退避秒数 */
    private long maxBackoffSeconds = 3600;

    /** 已投递消息保留天数（0=不清理） */
    private int sentRetentionDays = 7;

    /** 是否启用自动清理 */
    private boolean autoCleanup = true;

    /** 清理间隔（小时） */
    private long cleanupIntervalHours = 6;
}
