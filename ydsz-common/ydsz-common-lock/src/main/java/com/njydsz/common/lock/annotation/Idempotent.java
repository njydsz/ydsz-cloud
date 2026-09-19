package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解（兼容旧 com.njydsz.common.lock.annotation.Idempotent）。
 *
 * <p>标注在 Controller 方法上，防止重复提交。 基于 Redis 分布式锁实现，在 TTL 时间内同一 key 的请求只处理一次。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Idempotent(key = "order:create", ttlSeconds = 5, message = "请勿重复提交")
 * @PostMapping("/orders")
 * public Result<Order> createOrder(@RequestBody OrderDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

  /**
   * 幂等键，支持 SpEL 表达式。
   *
   * <p>为空时自动根据 类名 + 方法名 + 参数摘要 生成。
   *
   * @return 幂等键
   */
  String key() default "";

  /**
   * 幂等锁过期时间（秒），超时后自动释放。
   *
   * <p>默认 5 秒，覆盖大部分重复点击场景。
   *
   * @return TTL 秒数
   */
  int ttlSeconds() default 5;

  /**
   * 重复提交时的提示信息。
   *
   * @return 提示信息
   */
  String message() default "请勿重复提交";

  /**
   * 幂等校验生效条件（SpEL 表达式）。
   *
   * <p>返回 {@code true} 时执行幂等校验，返回 {@code false} 时跳过幂等检查直接执行业务方法。
   *
   * <p>SpEL 变量：方法参数名（如 {@code #req} 引用入参）。
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * // 仅对 POST 请求做幂等校验，GET 查询放行
   * @Idempotent(key = "#req.requestId", condition = "#req.method == 'POST'")
   * public Result handle(Request req) { ... }
   * }</pre>
   *
   * <p>默认空串（恒生效，不做条件判断）。
   *
   * @return SpEL 条件表达式，空串表示无条件生效
   */
  String condition() default "";
}
