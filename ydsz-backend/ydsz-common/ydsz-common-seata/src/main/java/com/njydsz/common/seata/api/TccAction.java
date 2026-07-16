package com.njydsz.common.seata.api;

/**
 * TCC 业务接口
 *
 * <p>实现此接口定义 TCC 三阶段的业务逻辑：
 * <ul>
 *   <li>{@link #tryAction} - 预留资源（如冻结库存、冻结余额）</li>
 *   <li>{@link #confirmAction} - 确认提交（如扣减冻结的库存、扣减冻结的余额）</li>
 *   <li>{@link #cancelAction} - 取消预留（如释放冻结的库存、释放冻结的余额）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @Component
 * public class OrderTccAction implements TccAction<OrderResult> {
 *     @Override
 *     public OrderResult tryAction(TccContext context) {
 *         // 冻结库存
 *         inventoryMapper.freeze(context.get("skuId"), context.get("qty"));
 *         return new OrderResult(context.getXid());
 *     }
 *
 *     @Override
 *     public void confirmAction(TccContext context) {
 *         // 扣减冻结的库存
 *         inventoryMapper.deductFrozen(context.get("skuId"), context.get("qty"));
 *     }
 *
 *     @Override
 *     public void cancelAction(TccContext context) {
 *         // 释放冻结的库存
 *         inventoryMapper.unfreeze(context.get("skuId"), context.get("qty"));
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.5.0
 */
public interface TccAction<T> {

    /**
     * Try 阶段 - 预留资源
     *
     * @param context TCC 上下文
     * @return 业务返回值
     * @throws Exception 业务异常
     */
    T tryAction(TccContext context) throws Exception;

    /**
     * Confirm 阶段 - 确认提交
     *
     * @param context TCC 上下文
     * @throws Exception 业务异常
     */
    void confirmAction(TccContext context) throws Exception;

    /**
     * Cancel 阶段 - 取消预留
     *
     * @param context TCC 上下文
     * @throws Exception 业务异常
     */
    void cancelAction(TccContext context) throws Exception;
}
