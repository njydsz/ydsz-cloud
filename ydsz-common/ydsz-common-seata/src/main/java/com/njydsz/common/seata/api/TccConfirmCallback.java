package com.njydsz.common.seata.api;

/**
 * TCC Confirm 异步回调接口
 *
 * <p>用于异步 Confirm 模式下，业务方定义 Confirm 执行完成后的回调逻辑。
 *
 * <p><b>P1-3 修复</b>：原同步 Confirm 模式下，Confirm 阶段持锁时间长，影响吞吐量。
 * 新增异步 Confirm 接口，Try 完成后立即返回，Confirm 后台异步执行，通过回调通知结果。
 *
 * <p>使用方式：
 * <pre>{@code
 * tccTransactionManager.executeTccAsync("createOrder", tccAction, new TccConfirmCallback&lt;OrderResult&gt;() {
 *     &#64;Override
 *     public void onConfirmSuccess(TccContext context, OrderResult result) {
 *         // Confirm 成功，发送通知、更新状态等
 *     }
 *
 *     &#64;Override
 *     public void onConfirmFailure(TccContext context, Exception error) {
 *         // Confirm 失败，记录日志、触发补偿等
 *     }
 *
 *     &#64;Override
 *     public void onCancelSuccess(TccContext context) {
 *         // Cancel 成功，释放资源确认
 *     }
 *
 *     &#64;Override
 *     public void onCancelFailure(TccContext context, Exception error) {
 *         // Cancel 失败，需要人工介入或告警
 *     }
 * });
 * }</pre>
 *
 * @param <T> Try 阶段返回值类型
 * @author ydsz-team
 * @since 1.3.0
 */
public interface TccConfirmCallback<T> {

    /**
     * Confirm 阶段执行成功时回调
     *
     * <p>当异步 Confirm 成功完成后调用此方法，业务方可执行：
     * <ul>
     *   <li>发送订单确认通知</li>
     *   <li>更新业务状态为"已完成"</li>
     *   <li>释放临时资源</li>
     * </ul>
     *
     * @param context TCC 上下文（包含 xid、branchId 和业务数据）
     * @param result  Try 阶段的返回值
     */
    default void onConfirmSuccess(TccContext context, T result) {
        // 默认空实现，业务方可按需覆盖
    }

    /**
     * Confirm 阶段执行失败时回调
     *
     * <p>当异步 Confirm 失败（重试耗尽）时调用此方法。
     * 此时 TCC 框架已自动执行 Cancel 释放预留资源。
     *
     * @param context TCC 上下文
     * @param error   Confirm 失败的异常信息
     */
    default void onConfirmFailure(TccContext context, Exception error) {
        // 默认空实现，业务方可按需覆盖
    }

    /**
     * Cancel 阶段执行成功时回调
     *
     * <p>当 TCC 框架执行 Cancel 成功后调用，确认资源已释放。
     *
     * @param context TCC 上下文
     */
    default void onCancelSuccess(TccContext context) {
        // 默认空实现，业务方可按需覆盖
    }

    /**
     * Cancel 阶段执行失败时回调
     *
     * <p>当 Cancel 失败时调用，通常需要人工介入或告警处理。
     * Cancel 失败比 Confirm 失败更严重，可能导致资源悬挂。
     *
     * @param context TCC 上下文
     * @param error   Cancel 失败的异常信息
     */
    default void onCancelFailure(TccContext context, Exception error) {
        // 默认空实现，业务方可按需覆盖
    }
}
