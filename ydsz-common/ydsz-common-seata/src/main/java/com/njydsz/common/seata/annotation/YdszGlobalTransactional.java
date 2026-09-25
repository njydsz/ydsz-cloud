package com.njydsz.common.seata.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式全局事务注解。
 *
 * <p>标识一个方法需要在 Seata 全局事务上下文中执行。语义对标 Seata 原生注解
 * {@code @org.seata.spring.annotation.GlobalTransaction}，但封装于 common
 * 包内，业务代码<b>不得直接 import Seata 原生 API</b>
 * （YDIZ-COMMON-003 / 编码规范 §25.1 / 22.4）。事务的开启 / 提交 / 回滚由
 * {@link com.njydsz.common.seata.aspect.YdszGlobalTransactionalAspect} 驱动，
 * 运行时通过反射桥接 Seata 全局事务 API，避免编译期硬依赖。
 *
 * <p><b>使用前提</b>
 * <ul>
 *   <li>业务模块需按需引入 Seata 客户端依赖（如 seata-spring-boot-starter），
 *       由根 pom 统一管控版本；</li>
 *   <li>application.yml 中配置 {@code ydsz.seata.enabled=true}，TC 客户端参数
 *       仍使用 {@code seata.*} 原生前缀。</li>
 * </ul>
 *
 * <p><b>嵌套禁止</b>
 * 同一调用链中禁止内层方法再次标注本注解（YDIZ-TX-001），
 * 内层如需事务请使用本地事务 {@code @Transactional} 或 Feign 跨服务边界新事务。
 *
 * <p>示例：
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     @YdszGlobalTransactional(name = "order-create-order")
 *     public void createOrder(OrderDTO dto) {
 *         orderRepository.save(order);
 *         inventoryClient.deduct(dto.getSkuId(), dto.getQuantity());
 *     }
 * }
 * }</pre>
 *
 * <h2>事务模式与超时责任链</h2>
 * <p>TC 端执行的事务模式由配置决定（通常 AT）。Seata AutoConfig 全局配置文件通常位于应用 classpath。
 *
 * @author ydsz-team
 * @since ACC-1
 * @see com.njydsz.common.seata.aspect.YdszGlobalTransactionalAspect
 * @see com.njydsz.common.seata.config.SeataProperties
 * @deprecated 自 26.09.25 起废弃。当前零业务模块引用，分布式事务场景尚未启用。
 *             计划 26.12 版本随 ydsz-common-seata 归档至 attic/。
 */
@Inherited
@Documented
@Deprecated
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface YdszGlobalTransactional {

  /**
   * 事务名称（事务日志标识，建议在 TC 控制台用于追踪）。
   */
  String name() default "";

  /**
   * 全局事务超时（毫秒）。
   */
  int timeoutMills() default 60_000;

  /**
   * 触发回滚的异常类型（为空表示所有异常都回滚）。
   */
  Class<? extends Throwable>[] rollbackFor() default {};

  /**
   * 不触发回滚的异常类型。
   */
  Class<? extends Throwable>[] noRollbackFor() default {};
}
