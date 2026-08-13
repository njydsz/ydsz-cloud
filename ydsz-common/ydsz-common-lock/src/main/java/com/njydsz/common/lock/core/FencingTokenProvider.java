package com.njydsz.common.lock.core;

import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;


/**
 * 分布式锁 Fencing Token 提供者
 *
 * <p>基于 Redis INCR 命令生成全局单调递增的 fencing token，用于解决分布式锁在过期后
 * 的安全窗口问题。客户端每次操作共享资源时需携带此 token，资源端通过校验 token 的
 * 单调递增性判断操作合法性。
 *
 * <p>参考：Martin Kleppmann《How to do distributed locking》中关于 fencing token 的论述。
 *
 * <p><b>Redis Key 格式：</b>{@code ydsz:lock:fencing:{lockKey}}
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class FencingTokenProvider {

    /**
     * Fencing Token 键前缀
     */
    private static final String FENCING_TOKEN_KEY_PREFIX = "ydsz:lock:fencing:";

    /**
     * Token 键默认过期时间（秒），防止 INCR 产生的历史值长期残留
     */
    private static final long TOKEN_KEY_TTL_SECONDS = 3600L;

    /**
     * Redis 操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造 Fencing Token 提供者
     *
     * @param stringRedisTemplate Redis 操作模板
     */
    public FencingTokenProvider(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取指定锁键的下一个 Fencing Token
     *
     * <p>首次获取锁时生成并返回 token，后续每次调用都会返回新的递减值。
     * Token 键设置 TTL 防止历史值长期残留。
     *
     * @param lockKey 锁键（已含命名空间前缀）
     * @return 单调递增的 fencing token，-1 表示生成失败
     */
    public long nextToken(String lockKey) {
        String tokenKey = buildTokenKey(lockKey);
        try {
            Long token = stringRedisTemplate.opsForValue().increment(tokenKey);
            if (token != null) {
                // 首次创建时设置 TTL
                if (token == 1L) {
                    stringRedisTemplate.expire(tokenKey, TOKEN_KEY_TTL_SECONDS,
                            java.util.concurrent.TimeUnit.SECONDS);
                }
                log.debug("[ydsz-lock] 生成 fencing token | lockKey={} | token={}", lockKey, token);
                return token;
            }
            log.warn("[ydsz-lock] 生成 fencing token 失败，INCR 返回 null | lockKey={}", lockKey);
            return -1L;
        } catch (Exception e) {
            log.error("[ydsz-lock] 生成 fencing token 异常 | lockKey={} | error={}",
                    lockKey, e.getMessage(), e);
            return -1L;
        }
    }

    /**
     * 获取当前 fencing token 值（不递增），用于查询当前状态
     *
     * @param lockKey 锁键
     * @return 当前 token 值，不存在返回 0，异常返回 -1
     */
    public long currentToken(String lockKey) {
        String tokenKey = buildTokenKey(lockKey);
        try {
            Long token = stringRedisTemplate.opsForValue().get(tokenKey) != null
                    ? Long.parseLong(stringRedisTemplate.opsForValue().get(tokenKey))
                    : 0L;
            return token != null ? token : 0L;
        } catch (NumberFormatException e) {
            log.warn("[ydsz-lock] fencing token 值解析异常 | lockKey={}", lockKey);
            return 0L;
        } catch (Exception e) {
            log.error("[ydsz-lock] 查询 fencing token 异常 | lockKey={} | error={}",
                    lockKey, e.getMessage());
            return -1L;
        }
    }

    /**
     * 释放 fencing token 计数（仅在安全场景下调用）
     *
     * @param lockKey 锁键
     * @return true 表示释放成功
     */
    public boolean releaseToken(String lockKey) {
        String tokenKey = buildTokenKey(lockKey);
        try {
            Boolean deleted = stringRedisTemplate.delete(tokenKey);
            log.debug("[ydsz-lock] 释放 fencing token | lockKey={} | result={}", lockKey, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("[ydsz-lock] 释放 fencing token 异常 | lockKey={} | error={}",
                    lockKey, e.getMessage());
            return false;
        }
    }

    /**
     * 构建 token Redis 键
     *
     * @param lockKey 锁键
     * @return token Redis 键
     */
    private String buildTokenKey(String lockKey) {
        return FENCING_TOKEN_KEY_PREFIX + lockKey;
    }
}
