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
 * <p><b>分片使用示例：</b>
 *
 * <pre>{@code
 * @Scheduled(fixedDelay = 60_000L)
 * @DistributedScheduled(lockKey = "order:archive", leaseTime = 600, shardTotal = 3, shardIndex = "#{T(java.lang.Integer).parseInt(System.getenv('NODE_INDEX'))}")
 * public void archiveOrders() {
 *     // 仅处理 orderId % shardTotal == shardIndex 的订单
 *     // shardTotal=3, shardIndex=0 的节点处理 id mod 3 == 0 的订单
 * }
 * }</pre>
 *
 * <p><b>降级策略：</b>当 {@code LockStrategy} Bean 不存在（单节点/测试环境未装配 ydsz-common-lock）时， 直接执行任务不做加锁，保证功能可用。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /**
   * 任务错失触发策略（misfire policy），用于调度器因锁竞争或调度阻塞错过触发窗口后的补偿策略。
   *
   * <ul>
   *   <li>{@link MisfirePolicy#IGNORE}（默认）：忽略本次错失触发，等待下次调度</li>
   *   <li>{@link MisfirePolicy#FIRE_ONCE}：立即执行一次补偿（尽最大努力），输出 WARN 日志</li>
   * </ul>
   *
   * @return 错失触发策略
   */
  MisfirePolicy misfirePolicy() default MisfirePolicy.IGNORE;

  /**
   * 分片总数（用于将大数据集分片到多个节点并行处理）。
   *
   * <p>取值范围：1（不分片，默认）到 N（总节点数）。 设为 1 表示单节点全量处理，行为与不配置分片一致。
   *
   * <p>分片后，锁 key 自动变为 {@code lockKey + ":shard:" + shardIndex}。
   *
   * @return 分片总数
   */
  int shardTotal() default 1;

  /**
   * 当前节点的分片序号（从 0 开始，须小于 {@link #shardTotal()}）。
   *
   * <p>同一任务在不同节点使用不同的 {@code shardIndex} 值。 支持 SpEL 表达式引用环境变量或系统属性。
   *
   * <p><b>示例：</b>{@code "#{T(java.lang.Integer).parseInt(System.getenv('MY_SHARD_INDEX'))}"}
   *
   * @return 当前节点的分片序号
   */
  int shardIndex() default 0;

  /**
   * 定时任务执行失败时的处理策略。
   *
   * <ul>
   *   <li>{@link OnErrorPolicy#LOG_ONLY}（默认）：仅记录 ERROR 日志，不向上抛出异常</li>
   *   <li>{@link OnErrorPolicy#THROW}：将异常继续抛出给调度框架处理</li>
   * </ul>
   *
   * @return 错误处理策略
   */
  OnErrorPolicy onError() default OnErrorPolicy.LOG_ONLY;

  /**
   * 任务错失触发策略枚举。
   */
  enum MisfirePolicy {
    /** 忽略本次错失触发，等待下次调度 */
    IGNORE,
    /** 尽最大努力执行一次补偿 */
    FIRE_ONCE
  }

  /**
   * 执行失败处理策略枚举。
   */
  enum OnErrorPolicy {
    /** 仅记录日志，不向上抛出异常 */
    LOG_ONLY,
    /** 将异常继续抛出给调度框架 */
    THROW
  }
}
