package com.njydsz.pmis.common.seata.api;

/**
 * TCC 分支事务状态
 *
 * <p>用于解决 TCC 三大经典问题（空回滚、悬挂、幂等）：
 * <ul>
 *   <li>{@link #INIT} - Try 尚未执行</li>
 *   <li>{@link #TRYING} - Try 正在执行</li>
 *   <li>{@link #TRIED} - Try 执行完成，等待 Confirm/Cancel</li>
 *   <li>{@link #CONFIRMING} - Confirm 正在执行</li>
 *   <li>{@link #CONFIRMED} - Confirm 执行完成（终态）</li>
 *   <li>{@link #CANCELLING} - Cancel 正在执行</li>
 *   <li>{@link #CANCELLED} - Cancel 执行完成（终态）</li>
 * </ul>
 *
 * <p>状态流转规则：
 * <pre>
 *   INIT → TRYING → TRIED → CONFIRMING → CONFIRMED（终态）
 *                    ↓
 *               CANCELLING → CANCELLED（终态）
 * </pre>
 *
 * <p>防悬挂：Try 前检查状态，若已为 CANCELLED 则跳过（悬挂保护）。
 * <p>防空回滚：Cancel 前检查状态，若为 INIT/TRYING 则跳过（空回滚保护）。
 * <p>幂等：Confirm/Cancel 前检查状态，若已为终态则跳过（幂等保护）。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public enum TccBranchStatus {

    /** Try 尚未执行 */
    INIT,

    /** Try 正在执行 */
    TRYING,

    /** Try 执行完成，等待 Confirm/Cancel */
    TRIED,

    /** Confirm 正在执行 */
    CONFIRMING,

    /** Confirm 执行完成（终态） */
    CONFIRMED,

    /** Cancel 正在执行 */
    CANCELLING,

    /** Cancel 执行完成（终态） */
    CANCELLED;

    /**
     * 是否为终态
     */
    public boolean isFinal() {
        return this == CONFIRMED || this == CANCELLED;
    }

    /**
     * 是否允许执行 Confirm（幂等检查）
     */
    public boolean canConfirm() {
        return this == TRIED || this == CONFIRMING;
    }

    /**
     * 是否允许执行 Cancel（幂等检查）
     */
    public boolean canCancel() {
        return this == TRIED || this == CANCELLING;
    }

    /**
     * 是否允许执行 Try（悬挂检查：已 Cancel 的分支不允许再 Try）
     */
    public boolean canTry() {
        return this == INIT || this == TRYING;
    }
}
