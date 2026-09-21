package com.njydsz.common.safe.idempotent.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.safe.idempotent.annotation.IdempotentExempt;
import com.njydsz.common.safe.idempotent.exception.IdempotentException;
import com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy;
import com.njydsz.common.safe.metrics.SafeMetrics;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.common.util.spring.SpELKeyUtils;

/**
 * 接口幂等性 AOP 切面
 *
 * <p>拦截标注 {@link Idempotent} 的 Controller 方法，委托 {@link IdempotentStrategy} 实现"在 TTL
 * 窗口内同一幂等键只处理一次"的语义，防止用户重复提交或网络重试造成脏数据。
 *
 * <h3>执行流程</h3>
 *
 * <ol>
 *   <li>解析 {@link Idempotent#key()}，支持 SpEL（{@code #{...}} 包裹）； 为空时按"类名#方法名#参数摘要"自动生成
 *   <li>委托 {@link IdempotentStrategy#acquire} 获取幂等锁，成功返回 token
 *   <li>获取失败时抛出 {@link IdempotentException}（HTTP 409 Conflict）
 *   <li>目标方法执行完成后，按"业务异常自动释放锁"规则处理：
 *       <ul>
 *         <li>正常返回 / 无异常：保留幂等锁至 TTL 自然过期
 *         <li>抛出 {@code BusinessException}（含子类）：立即释放幂等锁， 让客户端修正参数后可重试提交
 *         <li>抛出其他异常（如 SysException / RuntimeException）：保留幂等锁， 防止重试风暴击穿下游
 *       </ul>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Aspect
public class IdempotentAspect {

  /** 秒转毫秒系数 */
  private static final long SECONDS_TO_MILLIS = 1000L;

  /** SHA-256 摘要截取字节数（16 字节 = 32 位十六进制，避免幂等键过长） */
  private static final int DIGEST_BYTES = 16;

  /** 幂等策略（委托实现） */
  private final IdempotentStrategy idempotentStrategy;

  /** 幂等键 Redis 前缀 */
  private final String keyPrefix;

  /** 可选命名空间（多服务共享 Redis 时隔离键） */
  private final String namespace;

  /** 安全指标收集器（可选，用于观测幂等命中率） */
  private final SafeMetrics safeMetrics;

  /**
   * 构造 IdempotentAspect
   *
   * @param idempotentStrategy 幂等策略
   * @param keyPrefix 幂等键 Redis 前缀
   * @param namespace 命名空间（可为 null）
   * @param safeMetrics 安全指标收集器（可为 null）
   */
  public IdempotentAspect(
      IdempotentStrategy idempotentStrategy,
      String keyPrefix,
      String namespace,
      SafeMetrics safeMetrics) {
    this.idempotentStrategy = idempotentStrategy;
    this.keyPrefix = keyPrefix != null && !keyPrefix.isEmpty() ? keyPrefix : "ydsz:idem:";
    this.namespace = namespace;
    this.safeMetrics = safeMetrics;
  }

  /**
   * 拦截 {@link Idempotent} 注解方法，执行幂等校验
   *
   * @param joinPoint AOP 连接点
   * @param idempotent 幂等注解
   * @return 目标方法返回值
   * @throws Throwable 目标方法抛出的异常
   */
  @Around("@annotation(idempotent)")
  public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

    // 方法级 @IdempotentExempt 检查：直接放行
    if (method.isAnnotationPresent(IdempotentExempt.class)) {
      log.debug(
          "[ydsz-safe] [idempotent] 方法标注 @IdempotentExempt，跳过幂等检查 method={}", method.getName());
      return proceed(joinPoint);
    }

    // condition 条件判断：SpEL 表达式返回 false 时跳过幂等检查
    if (!evaluateCondition(idempotent.condition(), method, joinPoint.getArgs())) {
      log.debug(
          "[ydsz-safe] [idempotent] 幂等条件不满足，跳过幂等检查 method={} condition={}",
          method.getName(),
          idempotent.condition());
      return proceed(joinPoint);
    }

    String userKey = resolveUserKey(idempotent.key(), method, joinPoint.getArgs());
    String redisKey = buildRedisKey(userKey);

    long acquireStart = System.currentTimeMillis();
    String token =
        idempotentStrategy.acquire(redisKey, idempotent.ttlSeconds() * SECONDS_TO_MILLIS);
    if (token == null) {
      // 幂等命中：同一 key 在 TTL 窗口内被重复提交，拒绝处理
      recordIdempotentHit(idempotent, redisKey);
      throw new IdempotentException(idempotent.message(), redisKey);
    }

    // acquire 成功
    if (safeMetrics != null) {
      safeMetrics.recordIdempotentAcquireSuccess(System.currentTimeMillis() - acquireStart);
    }
    long heldStart = System.currentTimeMillis();

    try {
      Object result = joinPoint.proceed();
      // 正常返回：保留幂等锁至 TTL 自然过期，防止重复提交
      log.debug("[ydsz-safe] [idempotent] 方法正常完成，保留幂等锁至 TTL 过期 key={}", redisKey);
      return result;
    } catch (BusinessException bizEx) {
      // 业务异常：自动释放幂等锁，允许客户端修正后重试
      idempotentStrategy.release(redisKey, token);
      if (safeMetrics != null) {
        safeMetrics.recordIdempotentRelease(System.currentTimeMillis() - heldStart);
      }
      log.debug("[ydsz-safe] [idempotent] 业务异常释放幂等锁 key={} cause={}", redisKey, bizEx.getMessage());
      throw bizEx;
    } catch (RuntimeException | Error e) {
      // 非 BusinessException：保留幂等锁防止重试风暴
      log.warn(
          "[ydsz-safe] [idempotent] 非 BusinessException 抛出，保留幂等锁 key={} cause={}",
          redisKey,
          e.getClass().getSimpleName());
      throw e;
    } catch (Throwable ex) {
      log.warn(
          "[ydsz-safe] [idempotent] 检查型异常，保留幂等锁 key={} cause={}",
          redisKey,
          ex.getClass().getSimpleName());
      throw wrapCheckedException(ex);
    }
  }

  /**
   * 执行目标方法并传播异常
   *
   * <p>切面不声明 {@code throws Throwable}，运行时异常与 Error 原样传播，检查型异常包装为RuntimeException。
   *
   * @param joinPoint 连接点
   * @return 目标方法返回值
   */
  private Object proceed(ProceedingJoinPoint joinPoint) {
    try {
      return joinPoint.proceed();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw wrapCheckedException(t);
    }
  }

  /**
   * 将检查型异常包装为业务异常
   *
   * @param cause 原始异常
   * @return 包装后的业务异常
   */
  private BusinessException wrapCheckedException(Throwable cause) {
    BusinessException wrapped = new BusinessException(CoreExceptionCode.FAIL, cause);
    wrapped.setMessage("接口执行异常: " + cause.getMessage());
    return wrapped;
  }

  // ============================== 私有 ==============================

  /**
   * 解析用户幂等键
   *
   * <p>支持两种形式：
   *
   * <ul>
   *   <li>空字符串：自动按"类名#方法名#参数摘要"生成
   *   <li>{@code #{...}} 包裹的 SpEL 表达式或纯 SpEL 表达式
   *   <li>纯字符串（如 {@code "order:create"}）：直接使用
   * </ul>
   *
   * @param keyExpression 注解上的 key 表达式
   * @param method 目标方法
   * @param args 方法参数
   * @return 解析后的幂等键
   */
  private String resolveUserKey(String keyExpression, Method method, Object[] args) {
    if (keyExpression == null || keyExpression.isEmpty()) {
      return generateAutoKey(method, args);
    }
    try {
      String resolved = SpELKeyUtils.resolve(keyExpression, method, args);
      if (resolved == null || resolved.isEmpty()) {
        log.warn("[ydsz-safe] [idempotent] SpEL 解析结果为空，降级为自动 key expr={}", keyExpression);
        return generateAutoKey(method, args);
      }
      return resolved;
    } catch (Exception e) {
      log.warn(
          "[ydsz-safe] [idempotent] SpEL 解析失败，降级为自动 key expr={} cause={}",
          keyExpression,
          e.getMessage());
      return generateAutoKey(method, args);
    }
  }

  /**
   * 自动生成幂等键（类名#方法名#参数摘要）
   *
   * @param method 目标方法
   * @param args 方法参数
   * @return 自动生成的幂等键
   */
  private String generateAutoKey(Method method, Object[] args) {
    String className = method.getDeclaringClass().getSimpleName();
    String methodName = method.getName();
    String argsDigest = digestArgs(method, args);
    return className + "#" + methodName + "#" + argsDigest;
  }

  /**
   * 计算参数摘要（SHA-256 前 {@value #DIGEST_BYTES} 字节十六进制，避免过长 key）
   *
   * <p>自动过滤标注 {@link IdempotentExempt} 的参数，排除分页参数/时间戳等。
   *
   * @param method 目标方法（用于获取参数级 @IdempotentExempt 注解）
   * @param args 方法参数
   * @return 参数摘要
   */
  private String digestArgs(Method method, Object[] args) {
    if (args == null || args.length == 0) {
      return "no-args";
    }
    try {
      return sha256Hex(buildDigestSource(method, args));
    } catch (Exception e) {
      return fallbackDigest(method, args);
    }
  }

  /**
   * 构建摘要源文本：拼接未豁免参数
   *
   * @param method 目标方法
   * @param args 方法参数
   * @return 摘要源文本
   */
  private String buildDigestSource(Method method, Object[] args) {
    Parameter[] parameters = method.getParameters();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < args.length; i++) {
      if (isExempt(parameters, i)) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append("|");
      }
      sb.append(args[i] == null ? "null" : args[i].toString());
    }
    // 如果全部参数都被豁免，使用 args 数量作为标识
    if (sb.length() == 0) {
      sb.append("all-exempt:").append(args.length);
    }
    return sb.toString();
  }

  /**
   * 判断参数是否标注 {@link IdempotentExempt}
   *
   * @param parameters 方法参数列表
   * @param index 参数下标
   * @return true-豁免
   */
  private boolean isExempt(Parameter[] parameters, int index) {
    return index < parameters.length
        && parameters[index] != null
        && parameters[index].isAnnotationPresent(IdempotentExempt.class);
  }

  /**
   * 计算 SHA-256 摘要前 {@value #DIGEST_BYTES} 字节的十六进制
   *
   * @param source 摘要源文本
   * @return 十六进制摘要
   */
  private String sha256Hex(String source) {
    byte[] digest = DigestUtils.sha256(source.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (int i = 0; i < DIGEST_BYTES && i < digest.length; i++) {
      hex.append(String.format("%02x", digest[i]));
    }
    return hex.toString();
  }

  /**
   * 摘要计算异常时的降级方案（基于参数 hashCode）
   *
   * @param method 目标方法
   * @param args 方法参数
   * @return 降级摘要
   */
  private String fallbackDigest(Method method, Object[] args) {
    Parameter[] parameters = method.getParameters();
    int activeCount = 0;
    for (int i = 0; i < args.length && i < parameters.length; i++) {
      if (!isExempt(parameters, i)) {
        activeCount++;
      }
    }
    return activeCount == 0
        ? "all-exempt"
        : String.valueOf(Arrays.hashCode(args)) + ":" + activeCount;
  }

  /**
   * 构建 Redis key（添加前缀和命名空间）
   *
   * @param userKey 用户幂等键
   * @return 完整 Redis key
   */
  private String buildRedisKey(String userKey) {
    if (namespace == null || namespace.isEmpty()) {
      return keyPrefix + userKey;
    }
    return keyPrefix + namespace + ":" + userKey;
  }

  /**
   * 评估幂等校验的 condition 条件（SpEL 表达式）。
   *
   * <p>空串或空白串视为无条件生效，返回 {@code true}。 表达式解析异常时保守返回 {@code true}（执行幂等校验），避免因 SpEL 配置错误导致幂等防线失效。
   *
   * @param condition SpEL 条件表达式
   * @param method 目标方法
   * @param args 方法参数
   * @return {@code true} 表示条件满足需要执行幂等校验，{@code false} 表示跳过
   */
  private boolean evaluateCondition(String condition, Method method, Object[] args) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    try {
      String resolved = SpELKeyUtils.resolve(condition, method, args);
      return Boolean.parseBoolean(resolved);
    } catch (Exception e) {
      log.warn(
          "[ydsz-safe] [idempotent] condition SpEL 解析失败，默认执行幂等校验 condition={} cause={}",
          condition,
          e.getMessage());
      return true;
    }
  }

  /**
   * 记录幂等命中（可选指标收集）
   *
   * @param idempotent 注解
   * @param redisKey Redis key
   */
  private void recordIdempotentHit(Idempotent idempotent, String redisKey) {
    log.info("[ydsz-safe] [idempotent] 幂等命中拒绝 key={} ttl={}s", redisKey, idempotent.ttlSeconds());
    if (safeMetrics != null) {
      safeMetrics.recordIdempotentHit();
    }
  }
}
