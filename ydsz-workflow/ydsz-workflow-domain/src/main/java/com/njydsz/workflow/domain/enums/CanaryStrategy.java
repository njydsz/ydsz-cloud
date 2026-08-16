package com.njydsz.workflow.domain.enums;

/**
 * 金丝雀（灰度）策略枚举
 *
 * <p>定义流程定义灰度发布时的流量切分策略，对标 Argo Rollouts / Flagger 的灰度策略。 策略在 {@code ydsz_flow_canary.strategy}
 * 字段中持久化，由 {@code FlowCanaryService} 在启动流程时按策略分流。
 *
 * <p><b>策略说明：</b>
 *
 * <ul>
 *   <li>{@link #USER_HASH} — 按用户 ID 哈希取模，保证同一用户始终落到同一版本（推荐，避免用户看到流程定义频繁变化）
 *   <li>{@link #RANDOM} — 纯随机分发，简单但同一用户可能命中不同版本
 *   <li>{@link #WHITELIST} — 白名单用户优先灰度版本，其余用户走稳定版本（适合内部测试）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum CanaryStrategy {

  /** 用户哈希（按用户 ID 哈希分流） */
  USER_HASH,

  /** 随机分流 */
  RANDOM,

  /** 白名单 */
  WHITELIST,
}
