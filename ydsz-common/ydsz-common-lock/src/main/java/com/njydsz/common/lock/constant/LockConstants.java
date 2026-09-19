package com.njydsz.common.lock.constant;

/**
 * 分布式锁常量类
 *
 * <p>集中管理分散在各处的锁键前缀、后缀、分隔符等常量，避免"魔法字符串"在多处硬编码 导致命名规则变更时需全网排查。
 *
 * <p>常量按使用场景分组：锁键前缀/后缀、等待队列、发布订阅频道、续期缓存键、业务前缀。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class LockConstants {

  private LockConstants() {
    throw new AssertionError("常量类禁止实例化");
  }

  // ==================== 锁键分段 ====================

  /** 锁键分段前缀（命名空间分隔符后的固定段） */
  public static final String LOCK_SEGMENT = "lock:";

  /** 公平锁等待队列键后缀 */
  public static final String FAIR_QUEUE_SUFFIX = ":fair:queue";

  /** 读写锁读锁键前缀段 */
  public static final String READ_LOCK_SEGMENT = "rlock:";

  /** 读写锁写锁键前缀段 */
  public static final String WRITE_LOCK_SEGMENT = "wlock:";

  /** 信号量键前缀段 */
  public static final String SEMAPHORE_SEGMENT = "semaphore:";

  // ==================== 多 Key 联锁 ====================

  /** 多 Key 联锁子锁索引后缀模板（{index} 为子锁序号） */
  public static final String MULTI_LOCK_SUFFIX = ":multi:";

  // ==================== 发布订阅频道 ====================

  /** 锁释放通知频道前缀（频道格式：prefix + lockKey） */
  public static final String RELEASE_CHANNEL_PREFIX = "ydsz:lock:release:";

  // ==================== 分布式调度 ====================

  /** 分布式定时任务锁键前缀 */
  public static final String SCHEDULE_LOCK_PREFIX = "ydsz:schedule:";

  // ==================== 续期缓存键前缀 ====================

  /** 读锁续期任务缓存键前缀 */
  public static final String RENEWAL_KEY_READ = "R";

  /** 写锁续期任务缓存键前缀 */
  public static final String RENEWAL_KEY_WRITE = "W";

  // ==================== 公平锁队列 ====================

  /** 公平锁等待队列默认过期时间（秒），1 小时 */
  public static final long QUEUE_EXPIRE_SECONDS = 3600L;

  /** 公平锁队列条目的最大等待时间（毫秒），超时后被自动清理（P0-F2） */
  public static final long MAX_QUEUE_WAIT_MILLIS = 30_000L;

  // ==================== 公平锁 Hash 字段名 ====================

  /** 公平锁 Hash 中记录当前持有者的字段名 */
  public static final String FAIR_LOCK_OWNER_FIELD = "owner";

  /** 公平锁 Hash 中记录重入计数的字段名 */
  public static final String FAIR_LOCK_COUNT_FIELD = "__count";

  /** 公平锁 Hash 中记录租约时间的字段名 */
  public static final String FAIR_LOCK_LEASE_FIELD = "__leaseTime";

  // ==================== 可重入锁 Hash 字段名 ====================

  /** 可重入锁 Hash 中记录租约时间的字段名 */
  public static final String REENTRANT_LEASE_FIELD = "__ydsz_lease_ms__";

  // ==================== 客户端标识 ====================

  /** 本地降级锁值前缀 */
  public static final String LOCAL_LOCK_VALUE_PREFIX = "local-lock:";

  // ==================== 幂等键 ====================

  /** 幂等键默认 Redis 前缀 */
  public static final String IDEMPOTENT_KEY_PREFIX = "ydsz:idem:";

  /** 幂等键默认 TTL（秒） */
  public static final int IDEMPOTENT_DEFAULT_TTL_SECONDS = 5;
}
