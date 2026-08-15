package com.njydsz.common.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Outbox 事件模块配置属性
 *
 * <p>配置前缀：{@code ydsz.event.outbox}
 *
 * <pre>{@code
 * ydsz:
 *   event:
 *     outbox:
 *       enabled: true
 *       table-name: ydsz_outbox
 *       poll-interval-seconds: 5
 *       batch-size: 100
 *       max-retries: 5
 *       base-backoff-seconds: 10
 *       max-backoff-seconds: 3600
 *       sent-retention-days: 7
 *       auto-cleanup: true
 *       cleanup-interval-hours: 6
 *       max-payload-size-bytes: 4194304
 *       worker-threads: 1
 *       fail-on-noop: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.7.0 精简配置项，移除 schema-validation/sync-publish/alert-threshold 等未验证配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.event.outbox")
public class EventProperties {

    /** 是否启用 Outbox 模式 */
    private boolean enabled = true;

    /** Outbox 表名 */
    private String tableName = "ydsz_outbox";

    /** 轮询间隔（秒） */
    private long pollIntervalSeconds = 5;

    /** 每批最大条数 */
    private int batchSize = 100;

    /** 默认最大重试次数 */
    private int maxRetries = 5;

    /** 基础退避秒数（用于指数退避计算） */
    private long baseBackoffSeconds = 10;

    /** 最大退避秒数（退避上限） */
    private long maxBackoffSeconds = 3600;

    /** 已投递消息保留天数（0=不清理） */
    private int sentRetentionDays = 7;

    /** 是否启用自动清理已投递消息 */
    private boolean autoCleanup = true;

    /** 清理间隔（小时） */
    private long cleanupIntervalHours = 6;

    /** 消息 payload 最大字节数（默认 4MB） */
    private int maxPayloadSizeBytes = 4 * 1024 * 1024;

    /** PROCESSING 状态超时阈值（分钟），超时后回收为 PENDING */
    private int staleProcessingThresholdMinutes = 5;

    /** 投递工作线程数（1=单线程，>1=多线程并行投递） */
    private int workerThreads = 1;

    /** 检测到 NoopEventPublishGateway 时是否启动失败（生产环境应设为 true） */
    private boolean failOnNoop = true;

    /** Outbox 队列深度统计缓存时间（秒），减少 countByStatus 全表扫描频率 */
    private long statusCountCacheSeconds = 5;
}
