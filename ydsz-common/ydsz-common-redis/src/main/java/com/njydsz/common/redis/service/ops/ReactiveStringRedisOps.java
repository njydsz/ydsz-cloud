package com.njydsz.common.redis.service.ops;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 响应式 String 操作组件
 *
 * <p>封装 {@link ReactiveStringRedisTemplate} 的高频 String 操作，为 WebFlux / Reactor 场景
 * 提供统一的响应式 Redis 访问入口。消除业务模块（如 ydsz-gateway）直接注入
 * {@link ReactiveStringRedisTemplate} 的重复代码，落点于 ydsz-common-redis。
 *
 * <p><b>覆盖操作：</b>
 *
 * <ul>
 *   <li>原子计数：{@link #increment} / {@link #decrement}</li>
 *   <li>String 读写：{@link #get} / {@link #set} / {@link #setIfAbsent}</li>
 *   <li>键管理：{@link #expire} / {@link #hasKey} / {@link #delete}</li>
 * </ul>
 *
 * <p><b>与同步 {@link RedisStringOps} 的分工：</b>本组件仅提供响应式 API，
 * 同步场景继续使用 RedisStringOps。两者底层共享同一个 {@code ReactiveRedisConnectionFactory}。
 *
 * @author ydsz-team
 * @since 26.09.22
 * @see RedisStringOps
 */
@Slf4j
@RequiredArgsConstructor
public class ReactiveStringRedisOps {

  /** 响应式 Redis 模板 */
  private final ReactiveStringRedisTemplate redisTemplate;

  /**
   * 原子递增键值（INCR 语义）。
   *
   * <p>若键不存在，先创建并初始化为 0，再执行递增。
   *
   * @param key Redis 键
   * @return 递增后的值
   */
  public Mono<Long> increment(String key) {
    return redisTemplate.opsForValue().increment(key);
  }

  /**
   * 原子递减键值（DECR 语义）。
   *
   * <p>若键不存在，先创建并初始化为 0，再执行递减。
   *
   * @param key Redis 键
   * @return 递减后的值
   */
  public Mono<Long> decrement(String key) {
    return redisTemplate.opsForValue().decrement(key);
  }

  /**
   * 获取键值。
   *
   * @param key Redis 键
   * @return 值；键不存在返回 {@code Mono.empty()}
   */
  public Mono<String> get(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  /**
   * 设置键值。
   *
   * @param key Redis 键
   * @param value 值
   * @return 是否成功
   */
  public Mono<Boolean> set(String key, String value) {
    return redisTemplate.opsForValue().set(key, value);
  }

  /**
   * 设置键值（含过期时间）。
   *
   * @param key Redis 键
   * @param value 值
   * @param duration 过期时间
   * @return 是否成功
   */
  public Mono<Boolean> set(String key, String value, Duration duration) {
    ReactiveValueOperations<String, String> ops = redisTemplate.opsForValue();
    return ops.set(key, value, duration);
  }

  /**
   * 仅当键不存在时设置（SETNX 语义）。
   *
   * @param key Redis 键
   * @param value 值
   * @return true=设置成功（键不存在）；false=键已存在
   */
  public Mono<Boolean> setIfAbsent(String key, String value) {
    return redisTemplate.opsForValue().setIfAbsent(key, value);
  }

  /**
   * 仅当键不存在时设置（含过期时间）。
   *
   * @param key Redis 键
   * @param value 值
   * @param duration 过期时间
   * @return true=设置成功（键不存在）；false=键已存在
   */
  public Mono<Boolean> setIfAbsent(String key, String value, Duration duration) {
    return redisTemplate.opsForValue().setIfAbsent(key, value, duration);
  }

  /**
   * 设置键的过期时间。
   *
   * @param key Redis 键
   * @param duration 过期时长
   * @return true=设置成功；false=键不存在
   */
  public Mono<Boolean> expire(String key, Duration duration) {
    return redisTemplate.expire(key, duration);
  }

  /**
   * 检查键是否存在。
   *
   * @param key Redis 键
   * @return true=存在；false=不存在
   */
  public Mono<Boolean> hasKey(String key) {
    return redisTemplate.hasKey(key);
  }

  /**
   * 删除键。
   *
   * @param key Redis 键
   * @return true=删除成功；false=键不存在
   */
  public Mono<Long> delete(String key) {
    return redisTemplate.delete(key);
  }

  /**
   * 获取响应式 Redis 模板（供需要直接访问底层 API 的场景）。
   *
   * <p>谨慎使用：仅在上述封装方法无法满足需求时直接访问。
   *
   * @return 响应式 Redis 模板
   */
  public ReactiveStringRedisTemplate getTemplate() {
    return redisTemplate;
  }
}
