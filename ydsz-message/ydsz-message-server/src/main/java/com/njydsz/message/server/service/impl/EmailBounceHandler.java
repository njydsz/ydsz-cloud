package com.njydsz.message.server.service.impl;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 邮件退信处理器。
 *
 * <p>处理 SMTP 退信事件。
 *
 * <p>标记邮箱失效。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailBounceHandler {

  private final RedisStringOps redisStringOps;

  /** 退信黑名单 Key 前缀 */
  private static final String BOUNCE_KEY_PREFIX = "email:bounce:";

  /** 退信黑名单 TTL（天） */
  private static final long BOUNCE_TTL_DAYS = 90L;

  /**
   * 记录退信。
   *
   * @param email 退信邮箱
   * @param bounceReason 退信原因
   */
  public void recordBounce(String email, String bounceReason) {
    if (email == null || email.isBlank()) {
      return;
    }
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    redisStringOps.set(
        key, bounceReason != null ? bounceReason : "unknown", Duration.ofDays(BOUNCE_TTL_DAYS));
    log.warn("[Bounce] 邮件退信已记录: email={} reason={}", email, bounceReason);
  }

  /**
   * 检查邮箱是否在退信黑名单中。
   *
   * @param email 邮箱地址
   * @return true 表示在黑名单中，应跳过发送
   */
  public boolean isBounced(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    return Boolean.TRUE.equals(redisStringOps.hasKey(key));
  }

  /**
   * 从黑名单中移除（用户更新邮箱后可手动清除）。
   *
   * @param email 邮箱地址
   */
  public void removeFromBounceList(String email) {
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    redisStringOps.del(key);
    log.info("[Bounce] 邮箱已从退信黑名单移除: email={}", email);
  }

  /**
   * 获取退信原因。
   *
   * @param email 邮箱地址
   * @return 退信原因，null 表示不在黑名单中
   */
  public String getBounceReason(String email) {
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    return redisStringOps.get(key, String.class);
  }
}
