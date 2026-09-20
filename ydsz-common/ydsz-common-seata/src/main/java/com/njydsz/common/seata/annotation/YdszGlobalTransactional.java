package com.njydsz.common.seata.annotation;

import io.seata.spring.annotation.GlobalTransactional;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * YDSZ 封装的分布式事务注解（替代直接使用 Seata 原生 {@code @GlobalTransactional}）。
 *
 * <p>针对 Seata 原生 {@code @GlobalTransactional} 做了以下规范合规增强：
 * <ul>
 *   <li>rollbackFor 默认包含 {@link Exception.class}</li>
 *   <li>name 参数继承原生语义，建议格式："模块名-操作名"（如 "order-create-order"）</li>
 *   <li>超时时间通过 {@code ydzs.seata.tm.global-transaction-timeout} 配置（默认 30000ms，符合 YDIZ-TX-002）</li>
 * </ul>
 *
 * <p><b>规范引用：</b>
 * <ul>
 *   <li>YDIZ-TX-001 (P0)：@GlobalTransactional 禁止嵌套使用</li>
 *   <li>YDIZ-TX-002 (P0)：timeoutMillis ≤ 30000ms</li>
 *   <li>YDIZ-TX-003 (P1)：TCC 模式 try/confirm/cancel 必须幂等</li>
 *   <li>YDIZ-TX-004 (P1)：Feign 调用链必须透传 XID</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @YdszGlobalTransactional(name = "order-create-order")
 *     public void createOrder(OrderDTO dto) {
 *         orderRepository.save(order);
 *         inventoryClient.deduct(dto.getSkuId(), dto.getQuantity());
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@GlobalTransactional(
    name = "",
    rollbackFor = Exception.class)
public @interface YdszGlobalTransactional {

    /**
     * 全局事务名称（建议格式："模块名-操作名"）。
     *
     * <p>作用于 Seata Server 的日志展示和 TC 端事务分组标识。
     */
    @AliasFor(annotation = GlobalTransactional.class, attribute = "name")
    String name();

    /**
     * 触发回滚的异常类型（默认 Exception.class）。
     */
    @AliasFor(annotation = GlobalTransactional.class, attribute = "rollbackFor")
    Class<? extends Throwable>[] rollbackFor() default {Exception.class};
}
