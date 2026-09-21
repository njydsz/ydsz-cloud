package com.njydsz.common.lock.idempotent;

/**
 * 幂等策略接口（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy}。</b>
 * 本类仅为向后兼容保留，API 完全一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy} 替代
 */
@Deprecated
public interface IdempotentStrategy {

  /**
   * 尝试获取幂等锁
   *
   * @param key 幂等键
   * @param expireMillis 过期时间（毫秒）
   * @return 获取成功返回 token（用于后续释放），获取失败（已被占用）返回 null
   */
  String acquire(String key, long expireMillis);

  /**
   * 释放幂等锁（仅当 token 匹配时才删除）
   *
   * @param key 幂等键
   * @param token 获取锁时返回的 token
   * @return true-释放成功，false-token 不匹配或锁已过期
   */
  boolean release(String key, String token);

  /**
   * 检查幂等键是否存在
   *
   * @param key 幂等键
   * @return true-存在，false-不存在
   */
  boolean exists(String key);
}
