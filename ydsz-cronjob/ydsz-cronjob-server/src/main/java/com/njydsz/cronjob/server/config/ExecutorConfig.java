package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 执行器配置。
 *
 * <p>控制任务执行节点的注册、心跳、优雅下线、并发限制、线程池隔离等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ExecutorConfig {

  /** 默认heartbeatIntervalSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 10;

  /** 默认offlineThresholdSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_OFFLINE_THRESHOLD_SECONDS = 30;

  /** 默认drainTimeoutSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_DRAIN_TIMEOUT_SECONDS = 60;

  /** 默认maxConcurrent值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_CONCURRENT = 16;

  /** 默认queueCapacity值（可被配置文件覆盖） */
  private static final int DEFAULT_QUEUE_CAPACITY = 32;

  /** 默认tenantPoolSize值（可被配置文件覆盖） */
  private static final int DEFAULT_TENANT_POOL_SIZE = 10;

  /** 默认tenantPoolQueueCapacity值（可被配置文件覆盖） */
  private static final int DEFAULT_TENANT_POOL_QUEUE_CAPACITY = 200;

  /** 默认isolationBuckets值（可被配置文件覆盖） */
  private static final int DEFAULT_ISOLATION_BUCKETS = 8;

  /** 启动时注册到 ydsz_job_node 表 */
  private boolean registerOnStartup = true;

  /** 心跳上报间隔（秒，默认 10s） */
  private long heartbeatIntervalSeconds = DEFAULT_HEARTBEAT_INTERVAL_SECONDS;

  /** 节点离线判定阈值（秒，超过此时间无心跳视为离线） */
  private long offlineThresholdSeconds = DEFAULT_OFFLINE_THRESHOLD_SECONDS;

  /** 优雅下线时排空在执行任务 */
  private boolean drainOnShutdown = true;

  /** 排空超时时间（秒） */
  private long drainTimeoutSeconds = DEFAULT_DRAIN_TIMEOUT_SECONDS;

  /** 单节点最大并发任务数 */
  private int maxConcurrent = DEFAULT_MAX_CONCURRENT;

  /** P1-7: 执行线程池队列容量（0=无队列，SynchronousQueue；>0=有界队列） */
  private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

  /** P1-7: 线程名前缀 */
  private String threadNamePrefix = "job-exec-";

  /**
   * P2-5: 线程池隔离策略。
   *
   * <ul>
   *   <li>{@code none}（默认）：所有租户共享全局线程池。非 SaaS 场景推荐，配置简单， 全局池 + CallerRunsPolicy 提供自然背压；集群级并发由
   *       GlobalConcurrencyController 限制
   *   <li>{@code tenant}：按 tenantId 隔离，每个租户独立线程池。SaaS 多租户场景推荐， 彻底隔离 noisy
   *       neighbor，但租户数过多时存在线程膨胀风险（租户数 × tenantPoolSize）
   *   <li>{@code job_group}：按 jobGroup 隔离，每个分组独立线程池。 适合按业务域划分执行资源的场景（如核心业务 vs 离线任务）
   * </ul>
   *
   * <p><b>P1-O1 决策：</b>非 SaaS 场景保持 {@code none}（默认）。 全局线程池 + Semaphore（GlobalConcurrencyController
   * 通过 Redis 计数器实现） 已足够控制并发，无需为单租户引入额外的线程池分裂开销。 SaaS 场景可按需启用 {@code tenant}，建议配合租户级上限防止线程爆炸。
   */
  private String isolationStrategy = "none";

  /** P2-5: 每个租户/分组独立线程池的核心线程数 */
  private int tenantPoolSize = DEFAULT_TENANT_POOL_SIZE;

  /** P2-5: 每个租户/分组独立线程池的队列容量 */
  private int tenantPoolQueueCapacity = DEFAULT_TENANT_POOL_QUEUE_CAPACITY;

  /**
   * P2-5: 分桶隔离的桶数量（默认 8）。
   *
   * <p>使用固定数量的分桶池，通过哈希将租户/分组映射到对应分桶，避免无限创建线程池导致的资源耗尽问题。 仅当 {@code isolationStrategy} 为 {@code
   * tenant} 或 {@code job_group} 时生效。
   */
  private int isolationBuckets = DEFAULT_ISOLATION_BUCKETS;

  /**
   * 获取执行线程池队列容量（兼容旧方法名）。
   *
   * @return 队列容量
   */
  public int getExecutorQueueCapacity() {
    return queueCapacity;
  }

  /**
   * 设置执行线程池队列容量（兼容旧方法名）。
   *
   * @param capacity 队列容量
   */
  public void setExecutorQueueCapacity(int capacity) {
    this.queueCapacity = capacity;
  }

  /**
   * 获取分桶隔离的桶数量。
   *
   * <p>Lombok @Data 应自动生成，此处显式声明以确保跨模块编译可见性。
   *
   * @return 分桶数量
   */
  public int getIsolationBuckets() {
    return isolationBuckets;
  }

  /**
   * 获取每个租户/分组独立线程池的核心线程数。
   *
   * @return 核心线程数
   */
  public int getTenantPoolSize() {
    return tenantPoolSize;
  }

  /**
   * 获取每个租户/分组独立线程池的队列容量。
   *
   * @return 队列容量
   */
  public int getTenantPoolQueueCapacity() {
    return tenantPoolQueueCapacity;
  }
}
