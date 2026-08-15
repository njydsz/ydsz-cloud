package com.njydsz.common.seata.api;

import java.util.concurrent.Callable;

/**
 * 分布式事务管理器统一接口
 *
 * <p>提供统一的分布式事务抽象，底层实现可以是：
 * <ul>
 *   <li><b>Seata AT</b> - 自动补偿型，通过 undo_log 自动回滚</li>
 *   <li><b>TCC</b> - Try-Confirm-Cancel，业务层手动补偿</li>
 *   <li><b>SAGA</b> - 长事务编排，通过状态机驱动补偿</li>
 *   <li><b>Local</b> - 本地事务降级（单机模式下使用）</li>
 * </ul>
 *
 * <p><b>P1-5 新增</b>：suspend/resume 方法支持长事务场景下的事务挂起与恢复。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final DistributedTransactionManager txManager;
 *
 *     public void createOrder(OrderDTO dto) throws Exception {
 *         txManager.execute("createOrder", TransactionType.TCC, () -> {
 *             orderMapper.insert(dto);
 *             inventoryMapper.deduct(dto.getSkuId(), dto.getQty());
 *             accountMapper.deduct(dto.getUserId(), dto.getAmount());
 *             return null;
 *         });
 *     }
 *
 *     // 长事务场景：跨多个请求的事务
 *     public TxHandle beginLongTransaction(String txName) {
 *         txManager.begin(txName, TransactionType.SAGA);
 *         return txManager.suspend();
 *     }
 *
 *     public void resumeAndCommit(TxHandle handle) throws Exception {
 *         txManager.resume(handle);
 *         txManager.doCommit();
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DistributedTransactionManager {

    /**
     * 执行分布式事务
     *
     * @param transactionName 事务名称（用于日志和监控）
     * @param type            事务类型
     * @param action          业务操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception;

    /**
     * 执行分布式事务（带补偿动作）
     *
     * @param transactionName 事务名称
     * @param action          正向操作
     * @param compensation    补偿操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    <T> T executeWithCompensation(String transactionName,
                                   Callable<T> action,
                                   Runnable compensation) throws Exception;

    /**
     * 开始事务（P1-5 新增，用于长事务场景）
     *
     * <p>与 {@code execute} 不同，begin 仅开启事务并绑定上下文，不执行业务提交/回滚。
     * 事务上下文可通过 {@link #suspend()} 挂起后在后续操作中恢复。
     *
     * @param transactionName 事务名称
     * @param type            事务类型
     * @throws Exception 开始事务异常
     */
    default void begin(String transactionName, TransactionType type) throws Exception {
        // 默认实现：子类可覆盖提供真正的 begin 语义
    }

    /**
     * 挂起当前事务（P1-5 新增）
     *
     * <p>暂停当前事务上下文，返回事务句柄用于后续恢复。
     * 挂定时事务不会自动提交或回滚，上下文被保存到返回的句柄中。
     *
     * @return 事务句柄，可用于 {@link #resume(TxHandle)} 恢复
     * @throws Exception 挂起异常
     */
    default TxHandle suspend() throws Exception {
        // 默认实现返回 null，子类可覆盖提供真正的挂起语义
        return null;
    }

    /**
     * 恢复事务（P1-5 新增）
     *
     * <p>通过句柄恢复之前挂起的事务上下文，使后续操作在恢复的事务中执行。
     *
     * @param handle 事务句柄（由 suspend 返回）
     * @throws Exception 恢复异常
     */
    default void resume(TxHandle handle) throws Exception {
        // 默认实现空操作，子类可覆盖
    }

    /**
     * 提交当前事务（P1-5 新增）
     *
     * <p>提交通过 begin 开启的事务。前提是事务已通过 suspend 挂起或处于 begin 后的状态。
     *
     * @throws Exception 提交异常
     */
    default void doCommit() throws Exception {
        // 默认实现空操作，子类可覆盖
    }

    /**
     * 回滚当前事务（P1-5 新增）
     *
     * <p>回滚通过 begin 开启的事务。
     *
     * @throws Exception 回滚异常
     */
    default void doRollback() throws Exception {
        // 默认实现空操作，子类可覆盖
    }

    /**
     * 获取当前事务类型
     *
     * @return 当前配置的事务类型
     */
    TransactionType getCurrentType();

    /**
     * 获取全局事务 XID（如有）
     *
     * @return 全局事务 ID，无事务上下文时返回 null
     */
    String getCurrentXid();

    /**
     * 事务句柄（P1-5 新增）
     *
     * <p>用于在 suspend/resume 模式间传递事务上下文。
     * 实现类可根据需要扩展此接口。
     */
    interface TxHandle {
        /**
         * 获取句柄关联的 XID
         */
        String getXid();

        /**
         * 获取事务类型
         */
        TransactionType getType();

        /**
         * 获取事务名称
         */
        String getTransactionName();

        /**
         * 获取挂起时间戳
         */
        long getSuspendTimestamp();
    }
}
