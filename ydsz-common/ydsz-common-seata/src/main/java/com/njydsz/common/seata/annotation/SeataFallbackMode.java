package com.njydsz.common.seata.annotation;

/**
 * Seata 事务降级策略枚举。
 *
 * <p>当 Seata Server 不可用或全局事务失败时的降级行为：
 * <ul>
 *   <li>{@link #FAIL} — 直接抛出异常（默认，强一致场景）</li>
 *   <li>{@link #LOCAL_TRANSACTION} — 降级为本地事务 + Outbox 最终一致性</li>
 *   <li>{@link #SKIP} — 跳过分布式事务，仅执行本地逻辑（弱一致，记录 WARN 日志）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public enum SeataFallbackMode {

    /** 直接抛出异常，不降级（默认） */
    FAIL,

    /** 降级为本地事务（通过 Outbox 保障最终一致性） */
    LOCAL_TRANSACTION,

    /** 跳过分布式事务，仅执行本地逻辑并记录 WARN 日志 */
    SKIP;
}
