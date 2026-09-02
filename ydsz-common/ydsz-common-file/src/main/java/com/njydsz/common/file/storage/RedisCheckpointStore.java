package com.njydsz.common.file.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.util.string.StringUtils;

/**
 * 基于 Redis 的检查点存储实现
 *
 * <p>支持多实例部署时断点续传检查点共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisCheckpointStore implements CheckpointStore {

  /** Redis 键前缀 */
  private static final String REDIS_KEY_PREFIX = "ydsz:file:checkpoint:";

  /** Redis 操作模板 */
  private final StringRedisTemplate stringRedisTemplate;

  /** 默认检查点 TTL（24 小时） */
  private static final long DEFAULT_CHECKPOINT_TTL_SECONDS = 24 * 3600;

  /**
   * 构造 Redis 检查点存储
   *
   * @param stringRedisTemplate Redis 操作模板
   */
  public RedisCheckpointStore(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public void save(String bucketName, String objectName, String checkpoint, long ttlSeconds) {
    if (StringUtils.isBlank(bucketName) || StringUtils.isBlank(objectName)) {
      return;
    }
    try {
      String key = buildKey(bucketName, objectName);
      long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_CHECKPOINT_TTL_SECONDS;
      stringRedisTemplate.opsForValue().set(key, checkpoint, Duration.ofSeconds(effectiveTtl));
    } catch (Exception e) {
      log.warn(
          "[Storage] RedisCheckpointStore save failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
    }
  }

  @Override
  public String get(String bucketName, String objectName) {
    if (StringUtils.isBlank(bucketName) || StringUtils.isBlank(objectName)) {
      return null;
    }
    try {
      String key = buildKey(bucketName, objectName);
      return stringRedisTemplate.opsForValue().get(key);
    } catch (Exception e) {
      log.warn(
          "[Storage] RedisCheckpointStore get failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
      return null;
    }
  }

  @Override
  public void remove(String bucketName, String objectName) {
    if (StringUtils.isBlank(bucketName) || StringUtils.isBlank(objectName)) {
      return;
    }
    try {
      String key = buildKey(bucketName, objectName);
      stringRedisTemplate.delete(key);
    } catch (Exception e) {
      log.warn(
          "[Storage] RedisCheckpointStore remove failed, bucket={}, object={}, message={}",
          bucketName,
          objectName,
          e.getMessage());
    }
  }

  @Override
  public String buildKey(String bucketName, String objectName) {
    String safeBucket = URLEncoder.encode(bucketName, StandardCharsets.UTF_8).replace("+", "%20");
    String safeObjectName =
        URLEncoder.encode(objectName, StandardCharsets.UTF_8).replace("+", "%20");
    return REDIS_KEY_PREFIX + safeBucket + ":" + safeObjectName;
  }
}
