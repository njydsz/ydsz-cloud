package com.njydsz.common.seata.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SAGA 事务步骤注解 — 标记方法为 SAGA 分布式事务的一个步骤。
 *
 * <p>SAGA 模式适用于长事务场景（如跨多个微服务的业务流程），每个步骤具备：
 * <ul>
 *   <li>正向操作（被标注的方法）</li>
 *   <li>补偿操作（{@link #compensateMethod()} 指定的方法）</li>
 * </ul>
 *
 * <p><b>SAGA 执行流程：</b>
 * <ol>
 *   <li>SagaManager 按 {@link #order()} 升序依次执行各步骤</li>
 *   <li>步骤 i 成功 → 继续执行步骤 i+1</li>
 *   <li>步骤 i 失败 → 按逆序执行已成功步骤的补偿方法（rollback）</li>
 * </ol>
 *
 * <p><b>幂等要求：</b>补偿方法必须幂等，支持多次调用结果一致。
 *
 * <p><b>设计状态：</b>能力储备 — 注解定义为预留，{@code SagaManager} 执行器待 TODO。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Service
 * @SagaTransactional(name = "order-saga")
 * public class OrderSagaService {
 *
 *     @SagaStep(value = "create-order", order = 1, compensateMethod = "cancelOrder")
 *     public void createOrder(OrderDTO dto) { ... }
 *
 *     public void cancelOrder(OrderDTO dto) { ... }
 *
 *     @SagaStep(value = "deduct-inventory", order = 2, compensateMethod = "restoreInventory")
 *     public void deductInventory(OrderDTO dto) { ... }
 *
 *     public void restoreInventory(OrderDTO dto) { ... }
 * }
 * }</pre>
 *
 * <p>规范引用：YDIZ-TX-003 (P1) TCC/SAGA 模式必须幂等
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaStep {

    /** 步骤名称（用于日志标识和 SAGA 状态机追踪） */
    String value();

    /** 步骤执行顺序（升序，从 1 开始） */
    int order();

    /**
     * 补偿方法名（当前 Bean 中的方法名）。
     *
     * <p>SAGA 回滚时，SagaManager 通过反射调用此方法进行补偿。
     */
    String compensateMethod();

    /**
     * 步骤超时时间（毫秒，默认 30000ms）。
     *
     * <p>超时后触发补偿流程。
     */
    int timeoutMillis() default 30000;

    /**
     * 是否允许空补偿（默认 false）。
     *
     * <p>空补偿：补偿方法被调用但正向操作实际未执行。允许空补偿时，
     * 补偿方法需检测正向操作是否执行，若未执行则直接返回成功。
     */
    boolean allowEmptyCompensation() default false;
}
