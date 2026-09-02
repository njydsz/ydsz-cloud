package com.njydsz.common.lock.idempotent;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 Redis SET NX EX 的幂等策略默认实现
 *
 * <p>使用 Lua 脚本保证 acquire/release 的原子性：
 *
 * <ul>
 *   <li>acquire：生成 UUID token，SET key token NX EX ttl，成功返回 token
 *   <li>release：Lua 脚本校验 token 匹配后 DEL，避免误删他人持有的锁
 *   <li>exists：检查 key 是否存在
 * </ul>
 *
 * <p><b>降级策略（fail-open / fail-closed 可配置）：</b>Redis 不可用时， 通过 {@code failOpen} 开关控制：
 *
 * <ul>
 *   <li>{@code failOpen=true}（默认）：降级放行（返回非 null token），避免拖垮主流程； 适用于防重复点击等非关键幂等场景
 *   <li>{@code failOpen=false}：抛出 {@link IdempotentUnavailableException} 拒绝请求，
 *       严格保证幂等语义；适用于资金类等强幂等场景
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisIdempotentStrategy implements IdempotentStrategy {

  /** Redis SET NX EX 原子 Lua 脚本 */
  private static final String ACQUIRE_LUA =
      "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

  /** 释放幂等锁的 Lua 脚本：仅当 value 匹配时才 DEL */
  private static final String RELEASE_LUA =
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

  private static final RedisScript<Long> ACQUIRE_SCRIPT =
      new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);
  private static final RedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(RELEASE_LUA, Long.class);

  private final StringRedisTemplate redisTemplate;

  /** Redis 不可用时的降级策略（true=fail-open 放行，false=fail-closed 拒绝） */
  private final boolean failOpen;

  /**
   * 构造 Redis 幂等策略（默认 fail-open 降级放行）
   *
   * @param redisTemplate Redis 客户端
   */
  public RedisIdempotentStrategy(StringRedisTemplate redisTemplate) {
    this(redisTemplate, true);
  }

  /**
   * 构造 Redis 幂等策略（可指定降级策略）
   *
   * @param redisTemplate Redis 客户端
   * @param failOpen true-fail-open 放行；false-fail-closed 拒绝
   */
  public RedisIdempotentStrategy(StringRedisTemplate redisTemplate, boolean failOpen) {
    this.redisTemplate = redisTemplate;
    this.failOpen = failOpen;
  }

  @Override
  public String acquire(String key, long expireMillis) {
    if (expireMillis <= 0) {
      log.warn(
          "[ydsz-lock] [idempotent] [redis] expireMillis={} 非法，降级放行 key={}", expireMillis, key);
      return IdGenerator.nextIdStr();
    }
    long expireSeconds = Math.max(1, expireMillis / 1000);
    String token = IdGenerator.nextIdStr();
    try {
      Long ok =
          redisTemplate.execute(
              ACQUIRE_SCRIPT, Collections.singletonList(key), token, String.valueOf(expireSeconds));
      if (ok != null && ok == 1L) {
        return token;
      }
      return null;
    } catch (Exception e) {
      if (failOpen) {
        log.warn(
            "[ydsz-lock] [idempotent] [redis] Redis 不可用，fail-open 降级放行 key={} cause={}",
            key,
            e.getMessage());
        return token;
      }
      // fail-closed：Redis 不可用时拒绝请求，严格保证幂等语义
      log.error(
          "[ydsz-lock] [idempotent] [redis] Redis 不可用且 fail-closed 已启用，拒绝请求 key={} cause={}",
          key,
          e.getMessage());
      throw new IdempotentUnavailableException("幂等检查依赖的 Redis 不可用，已拒绝请求: " + key, e);
    }
  }

  @Override
  public boolean release(String key, String token) {
    if (token == null || token.isEmpty()) {
      return false;
    }
    try {
      Long result = redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
      return Long.valueOf(1L).equals(result);
    } catch (Exception e) {
      log.warn("[ydsz-lock] [idempotent] [redis] 释放幂等锁失败 key={} cause={}", key, e.getMessage());
      return false;
    }
  }

  @Override
  public boolean exists(String key) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    } catch (Exception e) {
      log.warn("[ydsz-lock] [idempotent] [redis] 检查幂等键失败 key={} cause={}", key, e.getMessage());
      return false;
    }
  }
}
