package com.njydsz.cronjob.server.core;

/**
 * 任务锁 key 构造工具（P0-11）。
 *
 * <p>集中管理任务锁 key 的构造逻辑，确保分片任务与非分片任务使用一致的 key 格式， 避免不同 Scanner/Dispatcher 自行拼接导致的分片锁释放失败问题。
 *
 * <h3>锁 key 格式</h3>
 *
 * <ul>
 *   <li>非分片任务：{@code ydsz:job:lock:{jobKey}}
 *   <li>分片任务：{@code ydsz:job:lock:{jobKey}:shard:{shardIndex}}
 * </ul>
 *
 * <p>所有需要构造或释放任务锁的组件（DefaultTaskDispatcher / FailoverScanner / TimeoutMonitor / SelfHealingScanner
 * / JobNodeReaper / JobServiceImpl 等） 都应通过本工具类构造锁 key，禁止在业务代码中直接拼接。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LockKeyUtil {

  /** 任务锁 key 前缀 */
  public static final String JOB_LOCK_PREFIX = "ydsz:job:lock:";

  /** 分片锁后缀格式 */
  private static final String SHARD_SUFFIX_FORMAT = ":shard:%d";

  /**
   * 释放分布式锁的 Lua 脚本（CAS 删除，确保只有持有者能释放）。
   *
   * <p>所有需要释放任务锁的组件（DefaultTaskDispatcher / FailoverScanner / TimeoutMonitor /
   * SelfHealingScanner）都应使用此常量，禁止各自定义重复脚本。
   */
  public static final String RELEASE_LOCK_SCRIPT =
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('del', KEYS[1]) "
          + "else "
          + "  return 0 "
          + "end";

  private LockKeyUtil() {
    // 工具类禁止实例化
  }

  /**
   * 构建任务锁 key（不含分片）。
   *
   * @param jobKey 任务 key，非空
   * @return 锁 key，如 {@code ydsz:job:lock:order-sync}
   */
  public static String buildJobLockKey(String jobKey) {
    return JOB_LOCK_PREFIX + jobKey;
  }

  /**
   * 构建任务分片锁 key。
   *
   * <p>分片索引为 {@code null} 或负数时退化为普通任务锁 key， 用于兼容非分片任务调用同一方法的场景。
   *
   * @param jobKey 任务 key，非空
   * @param shardIndex 分片索引（{@code null} 或 < 0 表示非分片任务）
   * @return 分片任务返回 {@code ydsz:job:lock:{jobKey}:shard:{shardIndex}}， 非分片任务返回 {@code
   *     ydsz:job:lock:{jobKey}}
   */
  public static String buildJobLockKey(String jobKey, Integer shardIndex) {
    if (shardIndex == null || shardIndex < 0) {
      return JOB_LOCK_PREFIX + jobKey;
    }
    return JOB_LOCK_PREFIX + jobKey + String.format(SHARD_SUFFIX_FORMAT, shardIndex);
  }
}
