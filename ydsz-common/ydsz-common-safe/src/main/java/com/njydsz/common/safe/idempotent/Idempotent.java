package com.njydsz.common.safe.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性注解。
 *
 * <p>标记在 Controller 方法上，确保同一业务键只执行一次写操作。 基于 Redis SETNX 或本地 ConcurrentHashMap 实现分布式/本地幂等校验。
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>根据 SpEL 表达式解析幂等键（默认使用方法签名）
 *   <li>尝试写入幂等键，设置过期时间
 *   <li>写入成功 → 执行业务方法
 *   <li>写入失败（键已存在）→ 返回 429 Too Many Requests
 * </ol>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @PostMapping("/orders")
 * @Idempotent(key = "#request.orderNo", expire = 300, timeUnit = TimeUnit.SECONDS)
 * public YdszResponse<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
 *     // 同一 orderNo 在 300 秒内只执行一次
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

  /**
   * SpEL 表达式，用于从方法参数中提取幂等键。
   *
   * <p>支持：
   *
   * <ul>
   *   <li>{@code "#paramName"} - 直接引用参数值
   *   <li>{@code "#paramName.field"} - 引用参数的字段
   *   <li>{@code "''"} (空字符串) - 使用方法签名作为键
   * </ul>
   *
   * @return SpEL 表达式
   */
  String key() default "";

  /**
   * 幂等键过期时间。
   *
   * <p>在此时间内，相同幂等键的重复请求会被拒绝。
   *
   * @return 过期时间值
   */
  long expire() default 60;

  /**
   * 过期时间单位。
   *
   * @return 时间单位
   */
  TimeUnit timeUnit() default TimeUnit.SECONDS;

  /**
   * 重复请求时的提示消息。
   *
   * @return 提示消息
   */
  String message() default "请求已提交，请勿重复操作";
}
