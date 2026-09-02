package com.njydsz.common.lock.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 分布式锁配置属性类
 *
 * <p>配置值通过 application.yml 中的 ydsz.lock 前缀注入。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   lock:
 *     enabled: true
 *     fallback-enabled: true
 *     max-renew-times: 100
 *     acquire-pool:
 *       core-size: 4
 *       max-size: 32
 *       queue-capacity: 256
 *     scheduler-pool-size: 2
 *     idempotent:
 *       default-ttl-seconds: 5
 *       key-prefix: ydsz:idem:
 * }</pre>
 *
 * <p><b>兼容性说明：</b> 当前配置前缀为 {@code ydsz.lock}，历史版本曾使用 {@code ydsz.distributed-lock} 前缀， 已统一迁移至
 * {@code ydsz.lock}，旧前缀不再支持。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.lock")
public class LockProperties {

  /** 默认 WatchDog 最大续期次数（约 30 分钟） */
  private static final int DEFAULT_MAX_RENEW_TIMES = 100;

  /** 默认调度线程池大小 */
  private static final int DEFAULT_SCHEDULER_POOL_SIZE = 2;

  /** 默认锁超时时间（秒） */
  private static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 30;

  /** 默认锁获取线程池核心线程数 */
  private static final int DEFAULT_ACQUIRE_CORE_SIZE = 4;

  /** 默认锁获取线程池最大线程数 */
  private static final int DEFAULT_ACQUIRE_MAX_SIZE = 32;

  /** 默认锁获取线程池队列容量 */
  private static final int DEFAULT_ACQUIRE_QUEUE_CAPACITY = 256;

  /** 默认多 Key 联锁最大续期次数 */
  private static final int DEFAULT_MULTI_LOCK_MAX_RENEW = 30;

  /** 默认多 Key 联锁续期间隔（秒） */
  private static final long DEFAULT_MULTI_LOCK_RENEW_INTERVAL = 10;

  /** 默认幂等锁过期时间（秒） */
  private static final int DEFAULT_IDEMPOTENT_TTL_SECONDS = 5;

  /** 是否启用分布式锁功能（控制整个分布式锁模块的开关，默认开启） */
  private boolean enabled = true;

  /**
   * 是否启用锁降级策略（Redis 不可用时降级为本地 ReentrantLock）
   *
   * <p>默认 false：Redis 不可用时快速失败，避免静默破坏分布式互斥性
   */
  private boolean fallbackEnabled = false;

  /**
   * WatchDog 最大续期次数（默认 100 次，约 30 分钟）
   *
   * <p>续期次数超过限制后停止续期，锁自动过期，防止业务线程卡死导致锁永不释放
   */
  private int maxRenewTimes = DEFAULT_MAX_RENEW_TIMES;

  /** 锁获取线程池配置 */
  private ThreadPool acquirePool = new ThreadPool();

  /** 调度线程池大小（用于 WatchDog 续期和信号量超时调度） */
  @Min(1)
  private int schedulerPoolSize = DEFAULT_SCHEDULER_POOL_SIZE;

  /**
   * 是否启用 WatchDog 自动续期功能
   *
   * <p>默认启用，设为 false 后加锁时不会启动续期任务， 锁将在 leaseTime 到期后自动过期释放
   */
  private boolean watchdogEnabled = true;

  /**
   * 锁键命名空间前缀（通常使用 ${spring.application.name}）
   *
   * <p>设置后，锁键会自动添加前缀：${namespace}:lock:${userKey}
   *
   * <p>用于多应用共享 Redis 时的锁键隔离，避免不同应用间的锁键冲突
   */
  private String namespace;

  /** 锁默认超时时间（秒），默认 30 秒 */
  @Min(1)
  private int defaultLockTimeoutSeconds = DEFAULT_LOCK_TIMEOUT_SECONDS;

  /**
   * 多 Key 联锁（RedisMultiLock）专用配置
   *
   * <p>当业务需要同时锁定多个资源时启用，框架内部使用 {@code RedisMultiLock} 实现。
   */
  private MultiLock multiLock = new MultiLock();

  /** 幂等配置 */
  private Idempotent idempotent = new Idempotent();

  /**
   * 锁获取线程池配置。
   *
   * <p>用于执行阻塞式锁获取与释放任务，避免在业务线程上直接阻塞； 核心/最大线程数与队列容量决定锁竞争激烈时的排队能力。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @Data
  public static class ThreadPool {
    /** 核心线程数 */
    private int coreSize = DEFAULT_ACQUIRE_CORE_SIZE;

    /** 最大线程数 */
    private int maxSize = DEFAULT_ACQUIRE_MAX_SIZE;

    /** 队列容量 */
    private int queueCapacity = DEFAULT_ACQUIRE_QUEUE_CAPACITY;
  }

  /** 多 Key 联锁配置 */
  @Data
  public static class MultiLock {
    /** 多 Key 联锁最大续期次数，默认 30 次（即最长约 10 分钟） */
    @Min(1)
    private int maxRenewCount = DEFAULT_MULTI_LOCK_MAX_RENEW;

    /**
     * 多 Key 联锁续期间隔（秒），默认 10 秒
     *
     * <p>每次续期后等待此时间再次续期
     */
    @Min(1)
    private long renewIntervalSeconds = DEFAULT_MULTI_LOCK_RENEW_INTERVAL;
  }

  /** 幂等配置 */
  @Data
  public static class Idempotent {
    /**
     * 幂等锁默认过期时间（秒），默认 5 秒
     *
     * <p>覆盖大部分重复点击场景
     */
    @Min(1)
    private int defaultTtlSeconds = DEFAULT_IDEMPOTENT_TTL_SECONDS;

    /**
     * 幂等键 Redis 前缀
     *
     * <p>所有幂等键统一以此前缀开头，便于排查和清理
     */
    private String keyPrefix = "ydsz:idem:";

    /**
     * Redis 不可用时的降级策略，默认 {@code true}（fail-open 放行）
     *
     * <p>权衡说明：
     *
     * <ul>
     *   <li>{@code true}（fail-open）：Redis 抖动时接口放行，幂等语义临时失效， 但保证业务主流程可用（适用于非关键幂等场景，如防重复点击）
     *   <li>{@code false}（fail-closed）：Redis 不可用时拒绝请求（抛异常）， 幂等语义严格保证，但 Redis
     *       故障会导致接口不可用（适用于资金类等强幂等场景）
     * </ul>
     *
     * <p>可通过配置 {@code ydsz.lock.idempotent.fail-open} 调整。
     */
    private boolean failOpen = true;
  }
}
