package com.njydsz.pmis.common.lock.idempotent;

/**
 * 幂等策略接口
 *
 * <p>定义幂等锁的获取、释放和检查语义。获取成功返回 token（用于安全释放），
 * 释放时校验 token 匹配后删除，避免误删他人持有的锁。
 *
 * @since 1.0.0
 */
public interface IdempotentStrategy {

    /**
     * 尝试获取幂等锁
     *
     * @param key           幂等键
     * @param expireMillis  过期时间（毫秒）
     * @return 获取成功返回 token（用于后续释放），获取失败（已被占用）返回 null
     */
    String acquire(String key, long expireMillis);

    /**
     * 释放幂等锁（仅当 token 匹配时才删除）
     *
     * @param key   幂等键
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
