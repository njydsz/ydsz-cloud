package com.njydsz.common.lock.admin;

import java.util.List;
import java.util.Set;

/**
 * 分布式锁运维管理接口（ydsz-common-lock）
 *
 * <p>封装运维场景下的锁查询、强制释放、键搜索等操作，供业务主应用模块（*-web）的运维端点调用。
 *
 * <p><b>设计定位：</b>业务端点（Controller）通过本接口操作分布式锁，
 * 不再直接持有 {@code StringRedisTemplate}，所有锁元的查询与变更收敛到 common-lock 模块。
 *
 * <p>与 {@link com.njydsz.common.lock.core.DistributedLocker} 的区别：
 * {@code DistributedLocker} 面向业务编程式加锁/解锁场景（需要 lockValue 校验）；
 * {@code DistributedLockAdmin} 面向运维管理场景（按 key 全局操作，用于死锁恢复、运维排查）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface DistributedLockAdmin {

  /**
   * 查询指定的分布式锁是否存在（是否被持有）
   *
   * @param lockKey 锁的 Redis 键（已含命名空间与 "lock:" 前缀）
   * @return true 表示锁存在且被持有
   */
  boolean exists(String lockKey);

  /**
   * 获取指定锁的剩余过期时间（TTL）
   *
   * @param lockKey 锁的 Redis 键（已含命名空间与 "lock:" 前缀）
   * @return 剩余过期时间（毫秒），-1 表示锁不存在或未被持有
   */
  long getTtl(String lockKey);

  /**
   * 获取指定锁的原始值（运维诊断）
   *
   * <p>返回 Redis 中锁键对应的字符串值，锁不存在时返回 null。
   *
   * @param lockKey 锁的 Redis 键（已含命名空间与 "lock:" 前缀）
   * @return 锁的原始字符串值；不存在返回 null
   */
  String getLockValue(String lockKey);

  /**
   * 强制释放指定锁（死锁恢复）
   *
   * <p>本方法会：取消看门狗续期任务 → 直接删除 Redis 键，不再需要 lockValue 校验。
   * 仅应在确认锁持有者已宕机或业务已安全的情况下使用。
   *
   * @param lockKey 锁的 Redis 键（已含命名空间与 "lock:" 前缀）
   * @return true 表示键已存在并被删除；false 表示键不存在
   */
  boolean forceUnlock(String lockKey);

  /**
   * 按模式搜索锁键（使用 Redis KEYS 命令，生产环境慎用）
   *
   * <p><b>注意：</b>本方法底层使用 {@code KEYS} 命令，时间复杂度 O(N)，
   * 大集群上可能造成阻塞。建议仅在测试环境或锁数量有限的生产运维场景使用。
   *
   * @param pattern 搜索模式（如 "app:lock:*"）
   * @return 匹配的锁键集合；无匹配返回空集合；pattern 为 null 或空返回空集合
   */
  Set<String> searchKeys(String pattern);

  /**
   * 按模式渐进式扫描锁键（使用 Redis SCAN 命令，生产安全）
   *
   * <p>基于 {@code SCAN} 渐进式遍历，单次返回约 {@code batchSize} 个匹配键。
   * 调用方应循环调用本方法直至返回空列表为止。
   *
   * @param pattern   搜索模式（如 "app:lock:*"）
   * @param batchSize 单次扫描的期望数量（实际返回可能略小于该值）
   * @return 本次扫描得到的键列表；无更多匹配时返回空列表；pattern 为 null 或空返回空列表
   */
  List<String> scanKeys(String pattern, int batchSize);
}
