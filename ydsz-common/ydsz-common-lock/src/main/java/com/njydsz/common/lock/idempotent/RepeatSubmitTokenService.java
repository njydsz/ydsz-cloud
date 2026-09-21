package com.njydsz.common.lock.idempotent;

import com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService;

/**
 * 表单重复提交 Token 服务（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService}。</b>
 * 本类继承自安全模块的实现，仅为向后兼容保留。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService} 替代
 */
@Deprecated
@SuppressWarnings("all")
public class RepeatSubmitTokenService extends com.njydsz.common.safe.idempotent.strategy.RepeatSubmitTokenService {

  public RepeatSubmitTokenService(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
    super(redisTemplate);
  }
}
