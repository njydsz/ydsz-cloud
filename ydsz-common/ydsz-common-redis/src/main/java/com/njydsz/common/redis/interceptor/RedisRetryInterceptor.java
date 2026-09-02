package com.njydsz.common.redis.interceptor;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Redis 重试拦截器
 *
 * <p>基于 Spring AOP 的 MethodInterceptor 实现，为 Redis 操作提供自动重试能力。 针对可重试异常（连接失败、超时）进行指数退避重试，对不可重试异常立即抛出。
 *
 * <p><b>重试策略：</b>
 *
 * <ul>
 *   <li>默认重试 3 次
 *   <li>初始等待 100ms，指数退避，最大等待 2s
 *   <li>仅对 RedisConnectionFailureException、QueryTimeoutException 重试
 *   <li>对 SerializationException、NullPointerException 等立即抛出
 *   <li><b>默认仅对读操作重试；写操作具有副作用，如需对写操作重试，必须显式配置 retryOnWrite=true</b>
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <pre>{@code
 * // 在 RedisConfiguration 中配置
 * @Bean
 * public RedisRetryInterceptor redisRetryInterceptor() {
 *     return new RedisRetryInterceptor(3, 100, 2000);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisRetryInterceptor implements MethodInterceptor {

  private static final Set<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS =
      Set.of(RedisConnectionFailureException.class, QueryTimeoutException.class);

  private static final Set<Class<? extends Throwable>> NON_RETRYABLE_EXCEPTIONS =
      Set.of(
          SerializationException.class,
          NullPointerException.class,
          IllegalArgumentException.class,
          IllegalStateException.class);

  /**
   * RedisTemplate 写操作方法名前缀集合（非幂等或具有副作用的操作）
   *
   * <p>这些操作默认不进行重试，除非通过 retryOnWrite=true 显式启用。 包括但不限于：
   *
   * <ul>
   *   <li>delete/del - 删除键
   *   <li>expire/pexpire/expireAt - 设置过期时间
   *   <li>persist - 移除过期时间
   *   <li>rename/move - 重命名/移动键
   *   <li>set - 设置值
   *   <li>increment/decrement - 增减操作
   *   <li>append - 追加内容
   *   <li>bound* - 绑定操作
   *   <li>multi/exec - 事务操作
   *   <li>opsFor* - 获取操作对象（其后续调用可能是写操作）
   *   <li>execute - 执行原生命令（可能是写操作）
   * </ul>
   *
   * <p><b>注意：</b>"execute" 方法本身不区分读写，这里使用精确匹配， 仅当方法名恰好为 "execute" 时才判定为写操作，避免误拦截 executeQuery 等读方法。
   */
  private static final Set<String> WRITE_METHOD_PREFIXES =
      Set.of(
          "delete",
          "del",
          "expire",
          "pexpire",
          "expireAt",
          "persist",
          "rename",
          "move",
          "set",
          "setIfAbsent",
          "setIfPresent",
          "setNX",
          "setEX",
          "increment",
          "decrement",
          "incr",
          "decr",
          "append",
          "bound",
          "multi",
          "exec",
          "unwatch",
          "opsFor",
          "add",
          "remove",
          "put",
          "clear",
          "left",
          "right",
          "push",
          "pop",
          "createGroup",
          "destroyGroup",
          "claim",
          "ack",
          "trim",
          "migrate");

  /**
   * 精确匹配的写操作方法名集合
   *
   * <p>这些方法名不能以 prefix 匹配，需要精确相等才判定为写操作。
   */
  private static final Set<String> WRITE_METHOD_EXACT = Set.of("execute");

  /**
   * 精确匹配的读操作方法名白名单。
   *
   * <p>这些方法名可能以写前缀开头（如 set 相关的 get），但实际是读操作，优先放行。
   */
  private static final Set<String> READ_METHOD_NAMES =
      Set.of(
          "get",
          "hasKey",
          "count",
          "size",
          "keys",
          "randomMember",
          "scan",
          "getExpire",
          "getBit",
          "range",
          "members",
          "intersect",
          "union",
          "difference",
          "score",
          "rank",
          "reverseRank",
          "position",
          "distance",
          "pending",
          "read",
          "readGroup");

  private final int maxRetries;
  private final long initialBackoffMs;
  private final long maxBackoffMs;
  private final boolean retryOnWrite;

  /** 创建重试拦截器（使用默认配置：重试 3 次，初始 100ms，最大 2s，仅对读操作重试） */
  public RedisRetryInterceptor() {
    this(3, 100, 2000, false);
  }

  /**
   * 创建重试拦截器（默认仅对读操作重试）
   *
   * @param maxRetries 最大重试次数
   * @param initialBackoffMs 初始退避时间（毫秒）
   * @param maxBackoffMs 最大退避时间（毫秒）
   */
  public RedisRetryInterceptor(int maxRetries, long initialBackoffMs, long maxBackoffMs) {
    this(maxRetries, initialBackoffMs, maxBackoffMs, false);
  }

  /**
   * 创建重试拦截器
   *
   * @param maxRetries 最大重试次数
   * @param initialBackoffMs 初始退避时间（毫秒）
   * @param maxBackoffMs 最大退避时间（毫秒）
   * @param retryOnWrite 是否对写操作也进行重试（默认 false，仅对读操作重试）
   */
  public RedisRetryInterceptor(
      int maxRetries, long initialBackoffMs, long maxBackoffMs, boolean retryOnWrite) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("最大重试次数不能小于 0");
    }
    if (initialBackoffMs <= 0) {
      throw new IllegalArgumentException("初始退避时间必须大于 0");
    }
    if (maxBackoffMs < initialBackoffMs) {
      throw new IllegalArgumentException("最大退避时间不能小于初始退避时间");
    }
    this.maxRetries = maxRetries;
    this.initialBackoffMs = initialBackoffMs;
    this.maxBackoffMs = maxBackoffMs;
    this.retryOnWrite = retryOnWrite;
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    String methodName = invocation.getMethod().getName();
    boolean isWriteOperation = isWriteMethod(methodName);

    // 写操作且未开启写重试：直接执行，不重试
    if (isWriteOperation && !retryOnWrite) {
      log.debug("【Redis】跳过写操作重试 | method={}", methodName);
      return invocation.proceed();
    }

    Throwable lastException = null;
    long backoffMs = initialBackoffMs;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        if (attempt > 0) {
          log.debug("【Redis】重试执行 | method={} | attempt={}/{}", methodName, attempt, maxRetries);
          sleep(backoffMs);
          backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
        }
        return invocation.proceed();
      } catch (Throwable e) {
        lastException = e;
        if (!isRetryable(e)) {
          log.debug(
              "【Redis】遇到不可重试异常，直接抛出 | method={} | error={}",
              methodName,
              e.getClass().getSimpleName());
          throw e;
        }
        log.debug(
            "【Redis】遇到可重试异常 | method={} | error={}", methodName, e.getClass().getSimpleName());
      }
    }

    Throwable exceptionToThrow =
        lastException != null ? lastException : new RuntimeException("Unknown error");
    log.error(
        "【Redis】重试次数耗尽，最后一次异常 | method={} | error={}",
        methodName,
        exceptionToThrow.getClass().getSimpleName());
    throw exceptionToThrow;
  }

  /**
   * 判断方法是否为写操作
   *
   * @param methodName 方法名
   * @return true-写操作，false-读操作
   */
  private boolean isWriteMethod(String methodName) {
    if (methodName == null || methodName.isEmpty()) {
      return false;
    }
    // 精确匹配常见读操作（这些方法名包含在 WRITE_METHOD_PREFIXES 中，但实际是读操作）
    if (READ_METHOD_NAMES.contains(methodName)) {
      return false;
    }
    // 前缀/后缀匹配写操作，或精确匹配写操作方法名
    return isWriteByPrefix(methodName) || WRITE_METHOD_EXACT.contains(methodName);
  }

  /**
   * 通过方法名前缀/后缀匹配写操作。
   *
   * @param methodName 方法名
   * @return true-匹配到写操作特征
   */
  private boolean isWriteByPrefix(String methodName) {
    for (String prefix : WRITE_METHOD_PREFIXES) {
      if (methodName.startsWith(prefix) || methodName.endsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断异常是否可重试
   *
   * @param e 异常
   * @return true-可重试，false-不可重试
   */
  private boolean isRetryable(Throwable e) {
    if (e == null) {
      return false;
    }

    for (Class<? extends Throwable> nonRetryable : NON_RETRYABLE_EXCEPTIONS) {
      if (nonRetryable.isInstance(e)) {
        return false;
      }
    }

    for (Class<? extends Throwable> retryable : RETRYABLE_EXCEPTIONS) {
      if (retryable.isInstance(e)) {
        return true;
      }
    }

    Throwable cause = e.getCause();
    if (cause != null && cause != e) {
      return isRetryable(cause);
    }

    return false;
  }

  /**
   * 线程睡眠
   *
   * @param ms 毫秒数
   */
  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("【Redis】重试等待被中断");
    }
  }
}
