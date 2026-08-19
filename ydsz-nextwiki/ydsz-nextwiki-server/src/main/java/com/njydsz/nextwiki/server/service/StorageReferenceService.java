package com.njydsz.nextwiki.server.service;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis INCR/DECR 的存储引用计数服务实现。
 *
 * <p>维护 storageKey → 引用计数的映射。秒传、复制场景下多 FileNodeVO 共享同一 storageKey，
 * 需通过引用计数确保物理对象仅在最后一个引用移除后才被安全删除，避免悬空引用/误删。
 *
 * <p>原子性保证：并发场景下增加 / 减少通过 Redis INCR/DECR 原子操作保证计数准确。
 *
 * <p>Key 格式：{@code wiki:ref:{storageKey}}，TTL 30 天防孤儿 key 堆积。
 *
 * <ul>
 *   <li>首次 INCR 时设置 TTL（仅 key 不存在时）
 *   <li>DECR 归零时主动删除 key 并返回 0，调用方可安全物理删除
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageReferenceService {

  /** Redis key 前缀 */
  private static final String KEY_PREFIX = "wiki:ref:";

  /** key 生存时间（30 天），防止孤儿 key 永久堆积 */
  private static final long TTL_DAYS = 30L;

  private final StringRedisTemplate redisTemplate;

  /**
   * 增加 storageKey 的引用计数（普通上传 / 秒传命中 / 复制时调用）。
   *
   * @param storageKey 底层存储对象键，不可为 {@code null} / blank
   * @return 增加后的当前计数
   */
  public long increment(String storageKey) {
    String key = buildKey(storageKey);
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }
    log.info("[StorageReference] 引用++ : storageKey={}, count={}", storageKey, count);
    return count != null ? count : 0L;
  }

  /**
   * 减少 storageKey 的引用计数（删除 / 覆盖时调用）。
   *
   * <p>返回 0 表示最后一个引用已移除，调用方可安全执行物理删除。
   *
   * @param storageKey 底层存储对象键，不可为 {@code null} / blank
   * @return 减少后的当前计数
   */
  public long decrement(String storageKey) {
    String key = buildKey(storageKey);
    Long count = redisTemplate.opsForValue().decrement(key);
    if (count != null && count <= 0L) {
      redisTemplate.delete(key);
      log.info("[StorageReference] 引用归零，已清除 key: storageKey={}", storageKey);
      return 0L;
    }
    log.info("[StorageReference] 引用-- : storageKey={}, count={}", storageKey, count);
    return count != null ? count : 0L;
  }

  /**
   * 查询 storageKey 的当前引用计数。
   *
   * @param storageKey 底层存储对象键
   * @return 当前计数；key 不存在返回 0
   */
  public long getCount(String storageKey) {
    String key = buildKey(storageKey);
    String value = redisTemplate.opsForValue().get(key);
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      log.warn("[StorageReference] 计数值异常，返回 0: storageKey={}, value={}", storageKey, value);
      return 0L;
    }
  }

  // ==================== 私有方法 ====================

  /** 构建 Redis key */
  private String buildKey(String storageKey) {
    return KEY_PREFIX + storageKey;
  }
}
