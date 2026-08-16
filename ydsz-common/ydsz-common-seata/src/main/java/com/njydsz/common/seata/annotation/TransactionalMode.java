package com.njydsz.common.seata.annotation;

import com.njydsz.common.seata.api.TransactionType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事务模式声明注解
 *
 * <p>用于在方法或类级别声明分布式事务类型，替代在代码中硬编码 {@link TransactionType}。
 *
 * <p><b>P1-6 新增</b>：解决不同业务场景需要不同事务模式时的动态切换问题。 通过 AOP 拦截 {@code @TransactionalMode}
 * 注解的方法，自动路由到对应的事务类型。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * &#64;Service
 * public class OrderService {
 *
 *     &#64;TransactionalMode(TransactionType.TCC)
 *     public void createOrder(OrderDTO dto) throws Exception {
 *         // 方法内无需关心事务类型，由注解声明
 *         orderMapper.insert(dto);
 *         inventoryMapper.deduct(dto.getSkuId(), dto.getQty());
 *     }
 *
 *     &#64;TransactionalMode(TransactionType.LOCAL)
 *     public OrderDTO queryOrder(Long id) {
 *         return orderMapper.selectById(id);
 *     }
 * }
 * }</pre>
 *
 * <p>也支持类级别注解（作为该类所有方法的默认事务类型）：
 *
 * <pre>{@code
 * &#64;Service
 * &#64;TransactionalMode(TransactionType.LOCAL)
 * public class QueryService {
 *     // 所有方法默认使用 LOCAL 事务
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TransactionalMode {

  /**
   * 声明的事务类型
   *
   * <ul>
   *   <li>{@link TransactionType#LOCAL} - 本地事务（默认）
   *   <li>{@link TransactionType#TCC} - TCC 分布式事务
   *   <li>{@link TransactionType#SEATA_AT} - Seata AT 自动补偿事务
   *   <li>{@link TransactionType#SAGA} - SAGA 长事务
   * </ul>
   */
  TransactionType value() default TransactionType.LOCAL;

  /**
   * 事务名称（可选，默认使用方法全名）
   *
   * <p>用于日志、监控和审计的事务标识，便于追踪和排查问题。
   */
  String name() default "";
}
