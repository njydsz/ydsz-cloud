package com.njydsz.common.lock.idempotent;

import com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy;

/**
 * 幂等策略接口（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy}。</b>
 * 本类仅为向后兼容保留。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy} 替代
 */
@Deprecated
@SuppressWarnings("all")
public interface IdempotentStrategy extends com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy {
  // 继承 safe 模块的 IdempotentStrategy，保持向后兼容
}
