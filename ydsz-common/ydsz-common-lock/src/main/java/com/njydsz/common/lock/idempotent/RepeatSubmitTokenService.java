package com.njydsz.common.lock.idempotent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 表单重复提交 Token 服务（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService}。</b>
 * 本类保留原实现作为向后兼容，逻辑与安全模块完全一致。
 *
 * <p>提供 Token 的生成、校验和删除功能，用于防止表单重复提交。 Token 存储在 Redis 中，与用户 ID 绑定，一次性使用。
 *
 * <p><b>工作流程：</b>
 *
 * <ol>
 *   <li>前端获取 Token
 *   <li>前端提交表单时携带 Token
 *   <li>后端校验并消费 Token
 *   <li>校验成功后 Token 自动删除
 * </ol>
 *
 * <p><b>Redis Key 格式：</b> {@code ydsz:repeat:token:{userId}:{token}}
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService} 替代
 */
@Slf4j
@Deprecated
public class RepeatSubmitTokenService {

  private static final String TOKEN_PREFIX = "ydsz:repeat:token:";
  private static final String TOKEN_VALUE = "1";
  private static final String INTERVAL_PREFIX = "ydsz:repeat:interval:";

  private final StringRedisTemplate redisTemplate;

  public RepeatSubmitTokenService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public String generateToken(String userId, long ttlMillis) {
    if (!StringUtils.hasText(userId)) {
      throw new IllegalArgumentException("生成防重复提交 Token 需要用户 ID");
    }
    String token = UUID.randomUUID().toString().replace("-", "");
    String redisKey = buildRedisKey(userId, token);
    redisTemplate.opsForValue().set(redisKey, TOKEN_VALUE, ttlMillis, TimeUnit.MILLISECONDS);
    log.debug(
        "[ydsz-lock] [repeat-submit] 生成 Token | userId={}, token={}, ttl={}ms",
        userId, token, ttlMillis);
    return token;
  }

  public boolean acquireInterval(String userId, String businessKey, long intervalMillis) {
    if (!StringUtils.hasText(businessKey) || intervalMillis <= 0) {
      return true;
    }
    if (!StringUtils.hasText(userId)) {
      return true;
    }
    String redisKey = INTERVAL_PREFIX + userId + ":" + businessKey;
    try {
      return Boolean.TRUE.equals(
          redisTemplate
              .opsForValue()
              .setIfAbsent(redisKey, TOKEN_VALUE, intervalMillis, TimeUnit.MILLISECONDS));
    } catch (Exception e) {
      log.warn(
          "[ydsz-lock] [repeat-submit] 获取间隔窗口失败，放行 | key={} | error={}", redisKey, e.getMessage());
      return true;
    }
  }

  public boolean validateAndConsume(String userId, String token) {
    if (!StringUtils.hasText(token)) {
      log.warn("[ydsz-lock] [repeat-submit] Token 为空");
      return false;
    }
    if (!StringUtils.hasText(userId)) {
      log.warn("[ydsz-lock] [repeat-submit] 用户 ID 为空，无法校验 Token");
      return false;
    }
    String redisKey = buildRedisKey(userId, token);
    Boolean deleted = redisTemplate.delete(redisKey);
    if (Boolean.TRUE.equals(deleted)) {
      log.debug("[ydsz-lock] [repeat-submit] Token 校验通过并消费 | userId={}, token={}", userId, token);
      return true;
    } else {
      log.warn("[ydsz-lock] [repeat-submit] Token 无效或已过期 | userId={}, token={}", userId, token);
      return false;
    }
  }

  private String buildRedisKey(String userId, String token) {
    return TOKEN_PREFIX + userId + ":" + token;
  }
}
