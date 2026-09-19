package com.njydsz.common.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 *       table-name: ydsz_com_outbox
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
 * @since 26.09.01
 * @since 26.09.01 精简配置项，移除 schema-validation/sync-publish/alert-threshold 等未验证配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.event.outbox")
public class EventProperties {

  /** 是否启用 Outbox 模式 */
  private boolean isEnabled = true;

  /** Outbox 表名 */
  private String tableName = "ydsz_com_outbox";

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
  private boolean isAutoCleanup = true;

  /** 清理间隔（小时） */
  private long cleanupIntervalHours = 6;

  /** 消息 payload 最大字节数（默认 4MB） */
  private int maxPayloadSizeBytes = 4 * 1024 * 1024;

  /** PROCESSING 状态超时阈值（分钟），超时后回收为 PENDING */
  private int staleProcessingThresholdMinutes = 5;

  /** 投递工作线程数（1=单线程，>1=多线程并行投递） */
  private int workerThreads = 1;

  /** 优雅关闭等待超时（秒） */
  private int awaitTerminationSeconds = 10;

  /** 检测到 NoopEventPublishGateway 时是否启动失败（生产环境应设为 true） */
  private boolean isFailOnNoop = true;

  /** Outbox 队列深度统计缓存时间（秒），减少 countByStatus 全表扫描频率 */
  private long statusCountCacheSeconds = 5;

  // ==================== 归档配置 ====================

  /** Outbox 消息归档配置 */
  private Archive archive = new Archive();

  /**
   * 获取归档配置
   *
   * @return 归档配置对象
   */
  public Archive getArchive() {
    return archive;
  }

  /**
   * 设置归档配置
   *
   * @param archive 归档配置对象
   */
  public void setArchive(Archive archive) {
    this.archive = archive;
  }

  /**
   * Outbox 归档配置（F-4）
   *
   * <p>配置路径：ydsz.event.outbox.archive.*
   */
  @Getter
  @Setter
  public static class Archive {

    /** 是否启用归档功能 */
    private boolean enabled = false;

    /** 归档表名 */
    private String tableName = "ydsz_com_outbox_archive";

    /** SENT 消息投递成功后保留天数（超过后自动归档） */
    private int archiveAfterSentDays = 7;

    /** 归档数据保留天数（超过后自动删除归档数据） */
    private int archiveRetentionDays = 90;

    /** 是否自动清理超期的归档数据 */
    private boolean autoCleanupEnabled = true;

    /** 归档清理任务执行间隔（小时） */
    private long cleanupIntervalHours = 24;
  }

  // ==================== Observation 配置 ====================

  /** Spring 6 Observation API 集成配置（O-1） */
  private Observation observation = new Observation();

  /**
   * 获取 Observation 配置
   *
   * @return Observation 配置对象
   */
  public Observation getObservation() {
    return observation;
  }

  /**
   * 设置 Observation 配置
   *
   * @param observation Observation 配置对象
   */
  public void setObservation(Observation observation) {
    this.observation = observation;
  }

  /**
   * Observation 配置
   *
   * <p>当 classpath 存在 micrometer-observation 且启用时， 通过 Spring 6 Observation API 产出 Trace +
   * Metrics，替代手写的 Counter/Timer/Gauge。
   *
   * <p>配置路径：ydsz.event.outbox.observation.*
   */
  @Getter
  @Setter
  public static class Observation {

    /** 是否启用 Observation（需 micrometer-observation 在 classpath） */
    private boolean enabled = false;

    /** Observation 名称（用于 Trace 和 Metrics 命名） */
    private String name = "ydsz.outbox.publish";

    /** 是否记录批量投递耗时 */
    private boolean recordBatchTimer = true;
  }

  // ==================== 健康检查配置 ====================

  /** 健康检查阈值配置 */
  private Health health = new Health();

  /**
   * 获取健康检查阈值配置
   *
   * @return 健康检查阈值配置
   */
  public Health getHealth() {
    return health;
  }

  /**
   * 设置健康检查阈值配置
   *
   * @param health 健康检查阈值配置
   */
  public void setHealth(Health health) {
    this.health = health;
  }

  /**
   * 健康检查阈值配置
   *
   * <p>配路径：ydsz.event.outbox.health.*
   */
  @Getter
  @Setter
  public static class Health {

    /** PENDING 消息数健康阈值，超过此值标记 DEGRADED */
    private long pendingThreshold = 10000L;

    /** PROCESSING 消息数健康阈值（默认为 pendingThreshold 一半） */
    private long processingThreshold = 5000L;

    /** DEAD_LETTER 消息数健康阈值，超过此值标记 DOWN */
    private long deadLetterThreshold = 10L;
  }
}
