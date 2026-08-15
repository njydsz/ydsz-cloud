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
 *       default-priority: 5
 *       default-schema-version: v1.0.0
 *       stale-processing-threshold-minutes: 5
 *       pending-alert-threshold: 10000
 *       dead-letter-alert-threshold: 10
 *       enable-tenant-isolation: true
 *       enable-sync-publish: false
 *       auto-dedup: false
 *       worker-threads: 1
 *       fail-on-noop: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
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

    /** 默认优先级（0-9，9 最高） */
    private int defaultPriority = 5;

    /** 默认 Schema 版本号 */
    private String defaultSchemaVersion = "v1.0.0";

    /** PROCESSING 状态超时阈值（分钟），超时后回收为 PENDING */
    private int staleProcessingThresholdMinutes = 5;

    /** PENDING 积压告警阈值 */
    private long pendingAlertThreshold = 10000;

    /** DEAD_LETTER 告警阈值 */
    private long deadLetterAlertThreshold = 10;

    /** 是否启用租户隔离 */
    private boolean enableTenantIsolation = true;

    /** 是否启用同步投递模式（事务提交后立即投递） */
    private boolean enableSyncPublish = false;

    /** 是否自动生成幂等去重 ID（基于内容 SHA-256 哈希，默认关闭） */
    private boolean autoDedup = false;

    /** 投递工作线程数（1=单线程，>1=多线程并行投递） */
    private int workerThreads = 1;

    /** 检测到 NoopEventPublishGateway 时是否启动失败（生产环境应设为 true） */
    private boolean failOnNoop = true;

    /** 是否在事务提交后将领域事件发布为 Spring 事件（供进程内 @EventListener 订阅） */
    private boolean enableDomainEventPublish = true;

    /** Outbox 队列深度统计缓存时间（秒），减少 countByStatus 全表扫描频率 */
    private long statusCountCacheSeconds = 5;
}
