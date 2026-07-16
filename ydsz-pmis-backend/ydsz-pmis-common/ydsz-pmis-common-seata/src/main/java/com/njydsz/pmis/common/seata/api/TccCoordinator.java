package com.njydsz.pmis.common.seata.api;

/**
 * TCC 事务协调器接口（异步 Confirm/Cancel）
 *
 * <p><b>P0-5 预留</b>：当前 {@link com.njydsz.pmis.common.seata.impl.TccTransactionManager}
 * 为同步 TCC 实现（Try→Confirm 在同一线程完成）。此接口为异步 TCC 协调器的扩展点：
 * <ul>
 *   <li>Try 阶段由业务调用方同步执行</li>
 *   <li>Confirm/Cancel 由后台协调器异步驱动（模拟 Seata TC 角色）</li>
 *   <li>JVM 崩溃后通过 {@link TccTransactionLogStore} 恢复未完成的事务</li>
 * </ul>
 *
 * <p>实现类可基于：
 * <ul>
 *   <li>Spring {@code ApplicationEventPublisher} 发布 Confirm/Cancel 事件</li>
 *   <li>MQ 消息驱动异步 Confirm/Cancel</li>
 *   <li>{@code @Async} + 线程池异步执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public interface TccCoordinator {

    /**
     * 异步触发 Confirm
     *
     * @param xid       全局事务 ID
     * @param branchId  分支事务 ID
     * @param tccAction TCC 动作
     * @param context   TCC 上下文
     * @param <T>       返回值类型
     */
    <T> void scheduleConfirm(String xid, String branchId, TccAction<T> tccAction, TccContext context);

    /**
     * 异步触发 Cancel
     *
     * @param xid       全局事务 ID
     * @param branchId  分支事务 ID
     * @param tccAction TCC 动作
     * @param context   TCC 上下文
     * @param <T>       返回值类型
     */
    <T> void scheduleCancel(String xid, String branchId, TccAction<T> tccAction, TccContext context);
}
