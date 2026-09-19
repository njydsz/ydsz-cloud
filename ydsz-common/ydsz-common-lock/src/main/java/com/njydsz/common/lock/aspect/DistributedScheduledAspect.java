package com.njydsz.common.lock.aspect;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.exception.DistributedLockException;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.lock.util.LockExpressionUtils;

/**
 * 分布式定时任务 AOP 切面
 *
 * <p>拦截标注了 {@link DistributedScheduled} 的方法，在方法执行前尝试获取分布式锁， 获取成功则执行任务，获取失败（其他节点正在执行）则跳过本次执行。
 *
 * <p><b>SpEL 支持：</b>{@link DistributedScheduled#lockKey()} 支持 SpEL 表达式 （{@code #{...}}
 * 包裹），可引用方法参数动态生成锁 key。 {@link DistributedScheduled#shardIndex()} 同样支持 SpEL 表达式。
 *
 * <b>分片支持：</b>当 {@code shardTotal > 1} 时，锁 key 自动附加 {@code ":shard:" + shardIndex} 后缀， 实现多节点并行处理不同数据分片。
 *
 * <p><b>降级策略：</b>当 {@code LockStrategy} Bean 不存在时（构造器传入 null）， 直接执行任务不做加锁，保证单节点/测试环境功能可用。
 *
 * <p>与 {@link YdszDistributedLockAspect} 的区别：
 *
 * <ul>
 *   <li>本切面针对 {@code @Scheduled} 定时任务，获取锁失败时<b>跳过</b>执行（不抛异常）
 *   <li>{@link YdszDistributedLockAspect} 针对业务方法，获取锁失败时<b>抛异常</b>或重试
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Aspect
public class DistributedScheduledAspect {

  /** 锁 key 前缀 */
  private static final String LOCK_PREFIX = "ydsz:schedule:";

  /** 分片锁 key 后缀模板 */
  private static final String SHARD_SUFFIX = ":shard:";

  /** 分布式锁提供者（null 时降级为不加锁） */
  private final DistributedLocker distributedLocker;

  /**
   * 构造器
   *
   * @param lockStrategy 锁策略（可选，null 时降级）
   */
  public DistributedScheduledAspect(LockStrategy lockStrategy) {
    if (lockStrategy == null) {
      this.distributedLocker = null;
      log.info("[ydsz-lock] [scheduled] LockStrategy 不可用，定时任务将以单节点模式运行（不加锁）");
    } else {
      this.distributedLocker = lockStrategy.getLock(LockType.REENTRANT);
    }
  }

  /**
   * 环绕通知：拦截 @DistributedScheduled 注解的方法
   *
   * <p>获取不到锁时直接返回 null，不抛异常，不执行目标方法。
   *
   * @param joinPoint AOP 连接点
   * @param annotation 注解（由参数绑定自动注入）
   * @return 目标方法返回值；未获取锁时返回 null
   */
  @Around("@annotation(annotation)")
  public Object around(ProceedingJoinPoint joinPoint, DistributedScheduled annotation) {
    if (distributedLocker == null) {
      return proceed(joinPoint, annotation.onError());
    }

    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    String lockKey = resolveLockKey(annotation.lockKey(), method, joinPoint.getArgs());
    int shardIndex = resolveShardIndex(annotation, method, joinPoint.getArgs());
    String fullLockKey = buildFullLockKey(lockKey, annotation.shardTotal(), shardIndex);
    long leaseTime = annotation.leaseTime();
    TimeUnit timeUnit = annotation.timeUnit();

    String lockValue = distributedLocker.tryLock(fullLockKey, leaseTime, timeUnit);
    if (lockValue == null) {
      log.debug("[ydsz-lock] [scheduled] 未获取锁，跳过本次执行: key={}", fullLockKey);
      return null;
    }

    try {
      return proceed(joinPoint, annotation.onError());
    } finally {
      try {
        distributedLocker.unlock(fullLockKey, lockValue);
      } catch (Exception e) {
        log.debug(
            "[ydsz-lock] [scheduled] 解锁异常（可能已超时自动释放）: key={} err={}", fullLockKey, e.getMessage());
      }
    }
  }

  /**
   * 构建完整锁 key（含前缀和分片后缀）。
   *
   * <p>分片总数 {@code shardTotal} 大于 1 时追加 {@code ":shard:" + shardIndex} 后缀， 使不同节点通过不同锁并行执行。
   *
   * @param lockKey 原始锁 key
   * @param shardTotal 分片总数
   * @param shardIndex 当前分片序号
   * @return 完整锁 key
   */
  private String buildFullLockKey(String lockKey, int shardTotal, int shardIndex) {
    String base = LOCK_PREFIX + lockKey;
    if (shardTotal > 1) {
      return base + SHARD_SUFFIX + shardIndex;
    }
    return base;
  }

  /**
   * 执行目标方法并根据错误策略处理异常。
   *
   * <p>切面不声明 {@code throws Throwable}（遵循编码规范）， 运行时异常与 Error 按 {@link
   * DistributedScheduled.OnErrorPolicy} 策略处理。
   *
   * @param joinPoint 连接点
   * @param onError 错误处理策略
   * @return 目标方法返回值
   */
  private Object proceed(ProceedingJoinPoint joinPoint, DistributedScheduled.OnErrorPolicy onError) {
    try {
      return joinPoint.proceed();
    } catch (RuntimeException | Error e) {
      return handleError(onError, e, joinPoint);
    } catch (Throwable t) {
      return handleError(onError, new DistributedLockException("定时任务执行异常", t), joinPoint);
    }
  }

  /**
   * 根据错误策略处理异常。
   *
   * @param onError 错误处理策略
   * @param e 原始异常
   * @param joinPoint 连接点信息
   * @return 策略为 IGNORE 时返回 null
   */
  private Object handleError(
      DistributedScheduled.OnErrorPolicy onError, Throwable e, ProceedingJoinPoint joinPoint) {
    if (onError == DistributedScheduled.OnErrorPolicy.THROW) {
      if (e instanceof RuntimeException re) {
        throw re;
      }
      if (e instanceof Error err) {
        throw err;
      }
      throw new DistributedLockException("定时任务执行异常", e);
    }
    // LOG_ONLY: 仅记录日志
    log.error(
        "[ydsz-lock] [scheduled] 定时任务执行异常（已按策略记录日志，不抛出） method={} cause={}",
        joinPoint.getSignature().toShortString(),
        e.getMessage(),
        e);
    return null;
  }

  /**
   * 解析分片序号（支持 SpEL 表达式引用环境变量）。
   *
   * @param annotation 注解实例
   * @param method 目标方法
   * @param args 方法参数
   * @return 解析后的分片序号（默认 0）
   */
  private int resolveShardIndex(DistributedScheduled annotation, Method method, Object[] args) {
    int shardTotal = annotation.shardTotal();
    if (shardTotal <= 1) {
      return 0;
    }
    String expr = Integer.toString(annotation.shardIndex());
    // 如果 shardIndex 是常量数字而非 SpEL，直接返回
    if (!expr.contains("#")) {
      return annotation.shardIndex();
    }
    try {
      String resolved = LockExpressionUtils.resolve(expr, method, args);
      return Integer.parseInt(resolved);
    } catch (Exception e) {
      log.warn(
          "[ydsz-lock] [scheduled] shardIndex SpEL 解析失败，使用默认值 0 expr={} cause={}",
          expr,
          e.getMessage());
      return 0;
    }
  }

  /**
   * 解析锁 key，支持 SpEL 表达式（委托 {@link LockExpressionUtils}）
   *
   * <p>支持模板模式（{@code "message:#{#param}"}）与整串 SpEL 模式 （{@code "'message:' +
   * #param}"}），无占位符时直接返回原字符串。
   *
   * @param lockKey 注解上的 key 表达式
   * @param method 目标方法
   * @param args 方法参数
   * @return 解析后的锁 key
   */
  private String resolveLockKey(String lockKey, Method method, Object[] args) {
    return LockExpressionUtils.resolve(lockKey, method, args);
  }
}
