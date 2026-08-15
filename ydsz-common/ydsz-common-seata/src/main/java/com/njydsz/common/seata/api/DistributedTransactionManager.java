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
 * <p>使用示例：
 * <pre>{@code
 * &#64;Service
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
 * }
 * }</pre>
 *
 * <p><b>设计原则</b>：本接口仅暴露核心事务能力，suspend/resume 等长事务语义
 * 通过独立的子接口实现，避免接口膨胀。
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
}
