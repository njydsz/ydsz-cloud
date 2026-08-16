package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式定时任务注解
 *
 * <p>用于包装 {@code @Scheduled} 定时任务，确保多节点部署时同一任务同一时刻只有一个节点执行。 获取不到锁的节点直接跳过本次执行（非阻塞），不抛异常。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Scheduled(fixedDelay = 60_000L)
 * @DistributedScheduled(lockKey = "message:expiry-clean", leaseTime = 300)
 * public void cleanExpiredMessages() {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * <p><b>降级策略：</b>当 {@code LockStrategy} Bean 不存在（单节点/测试环境未装配 ydsz-common-lock）时， 直接执行任务不做加锁，保证功能可用。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedScheduled {

  /**
   * 锁的 key（不含前缀，自动添加 {@code ydsz:schedule:} 前缀）
   *
   * <p>支持 SpEL 表达式，如 {@code "message:#{#param}"}。 大多数定时任务无入参，直接使用静态字符串即可。
   *
   * @return 锁的 key
   */
  String lockKey();

  /**
   * 锁持有时间（秒），应略大于任务预计执行时间
   *
   * <p>默认 300 秒（5 分钟），覆盖大多数定时任务场景。 超过此时间锁自动释放，防止节点宕机导致死锁。
   *
   * @return 锁持有时间（秒）
   */
  long leaseTime() default 300;

  /**
   * 时间单位
   *
   * @return 时间单位枚举
   */
  TimeUnit timeUnit() default TimeUnit.SECONDS;
}
