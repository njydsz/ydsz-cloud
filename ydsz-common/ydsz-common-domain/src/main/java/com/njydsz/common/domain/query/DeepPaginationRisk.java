package com.njydsz.common.domain.query;

/**
 * 深度分页风险评估结果。
 *
 * <p>由 {@link PageQuery#assessPaginationRisk()} 返回，标识当前分页查询是否存在深度分页风险。
 * 业务方可据此决定是否允许查询、发出告警或强制拒绝。
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see PageQuery#assessPaginationRisk()
 */
public enum DeepPaginationRisk {

    /**
     * 安全：offset 在安全范围内，可正常使用 offset 分页。
     */
    SAFE,

    /**
     * 警告：offset 超过警告阈值（默认 10000），建议改用游标分页。
     *
     * <p>调用方应记录 WARN 日志，提示开发者关注性能风险。
     */
    WARN,

    /**
     * 拒绝：offset 超过拒绝阈值（默认 50000），将抛出 {@link DeepPaginationException}。
     *
     * <p>强制调用方改用游标分页（{@link CursorPage}），防止慢查询拖垮数据库。
     */
    REJECT
}
