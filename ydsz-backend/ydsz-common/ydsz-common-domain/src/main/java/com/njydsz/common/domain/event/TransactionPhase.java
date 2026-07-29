package com.njydsz.common.domain.event;

/**
 * 事务阶段枚举 — 领域事件在事务生命周期中的发布时机。
 *
 * <p>用于 {@link DomainEventPublisher#publishWithPhase(DomainEvent, TransactionPhase)}
 * 指定事件在事务的哪个阶段发布。
 *
 * <p><b>各阶段语义：</b>
 * <table>
 *   <tr><th>阶段</th><th>触发时机</th><th>典型场景</th></tr>
 *   <tr><td>{@link #BEFORE_COMMIT}</td><td>事务提交前</td><td>事务内验证事件、发送预处理通知</td></tr>
 *   <tr><td>{@link #AFTER_COMMIT}</td><td>事务成功提交后</td><td>发布业务变更事件（推荐默认选择）</td></tr>
 *   <tr><td>{@link #AFTER_ROLLBACK}</td><td>事务回滚后</td><td>补偿操作、失败告警、清理缓存</td></tr>
 *   <tr><td>{@link #AFTER_COMPLETION}</td><td>事务完成后（无论提交或回滚）</td><td>资源清理、审计日志记录</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.1.0
 *
 * @see DomainEventPublisher
 */
public enum TransactionPhase {

    /**
     * 事务提交前 — 在 {@code TransactionSynchronization.beforeCommit()} 回调中发布。
     *
     * <p>适用于需要在事务内同步验证事件、或发送预处理通知的场景。
     * 此阶段事件发布失败不会导致事务回滚。
     */
    BEFORE_COMMIT,

    /**
     * 事务成功提交后 — 在 {@code TransactionSynchronization.afterCommit()} 回调中发布。
     *
     * <p>推荐用于发布业务变更事件，确保数据库变更和事件发布的一致性。
     * 避免事务回滚但事件已发出的问题。
     */
    AFTER_COMMIT,

    /**
     * 事务回滚后 — 在 {@code TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)} 回调中发布。
     *
     * <p>适用于补偿操作、失败告警、清理缓存等场景。
     * 事件表示业务操作失败回滚。
     */
    AFTER_ROLLBACK,

    /**
     * 事务完成后 — 在 {@code TransactionSynchronization.afterCompletion()} 回调中发布（无论提交或回滚）。
     *
     * <p>适用于资源清理、审计日志记录等无论事务成功与否都需要执行的场景。
     */
    AFTER_COMPLETION
}
