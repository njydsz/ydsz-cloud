package com.njydsz.common.cache.annotation;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * 多级缓存 SpEL 缓存键构造器 — 为 {@link MultiLevelCacheable} 提供基于 SpEL 表达式的缓存键解析能力。
 *
 * <p>对标 {@link org.springframework.cache.interceptor.SimpleKeyGenerator} 的 SpEL 路径，但输出格式遵循
 * 多级缓存约定：{@code mlevel:{declaringClass.simpleName}.{methodName}:{spelResult}}。
 *
 * <p>支持两种上下文构造策略：
 *
 * <ul>
 *   <li>{@link #resolveKey(String, ProceedingJoinPoint)} — 基于 AspectJ {@link ProceedingJoinPoint} 绑定方法参数变量</li>
 *   <li>{@link #resolveKey(String, Method, Object[])} — 基于反射 {@link Method} + 参数数组（非 AOP 调用方使用）</li>
 * </ul>
 *
 * <p>空结果（SpEL 求值返回 null）统一替换为 {@code "null"} 字符串，避免 NPE；求值异常时降级为默认 key 生成规则。
 *
 * <p>注意：本缓存键构造器聚焦于 SpEL 解析，与租户感知的 {@link
 * com.njydsz.common.cache.support.CacheKeyBuilder} 是两类工具——后者负责租户前缀拼接，
 * 前者负责 SpEL 求值。二者可组合使用。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MultiLevelCacheable
 * @see com.njydsz.common.cache.aspect.MultiLevelCacheAspect
 */
public final class CacheKeyBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(CacheKeyBuilder.class);

  /** 多级缓存 SpEL 前缀 */
  private static final String PREFIX = "mlevel";

  /** SpEL 表达式解析器（线程安全，可复用） */
  private static final ExpressionParser PARSER = new SpelExpressionParser();

  /** 方法参数名发现器（线程安全，可复用） */
  private static final ParameterNameDiscoverer PARAM_DISCOVERER = new DefaultParameterNameDiscoverer();

  /** 缓存 SpEL 表达式解析结果，避免重复解析开销（Expression 实例线程安全） */
  private static final ConcurrentMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(64);

  private CacheKeyBuilder() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== 主入口：基于 SpEL 生成完整缓存键 ==============================

  /**
   * 解析 SpEL key 表达式，返回多级缓存完整键（L1 + L2 共用同一 key）。
   *
   * <p>格式：{@code mlevel:{declaringClass.simpleName}.{methodName}:{spelResult}}
   *
   * @param keyExpression SpEL key 表达式（可为空，为空时使用默认规则：方法名 + 参数拼接）
   * @param joinPoint 切点（提供目标对象、方法元数据、参数值）
   * @return 完整缓存键，不会为 {@code null}
   */
  public static String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    String className = method.getDeclaringClass().getSimpleName();
    String methodName = method.getName();

    String spelResult = resolveSpelValue(keyExpression, method, joinPoint.getArgs());

    return PREFIX + ":" + className + "." + methodName + ":" + spelResult;
  }

  /**
   * 解析 SpEL key 表达式（非 AOP 场景）。
   *
   * <p>格式：{@code mlevel:{declaringClass.simpleName}.{methodName}:{spelResult}}
   *
   * @param keyExpression SpEL key 表达式（可为空）
   * @param method 目标方法
   * @param args 方法参数值数组
   * @return 完整缓存键，不会为 {@code null}
   */
  public static String resolveKey(String keyExpression, Method method, Object[] args) {
    String className = method.getDeclaringClass().getSimpleName();
    String methodName = method.getName();

    String spelResult = resolveSpelValue(keyExpression, method, args);

    return PREFIX + ":" + className + "." + methodName + ":" + spelResult;
  }

  // ============================== SpEL 求值核心 ==============================

  /**
   * 求值 SpEL 表达式，返回其字符串表示。
   *
   * <p>表达式为空时使用默认规则（方法名 + 拼接参数）。求值异常时降级并返回默认结果。
   *
   * @param keyExpression SpEL 表达式
   * @param method 方法（用于参数名发现）
   * @param args 参数值
   * @return SpEL 结果的字符串表示，或默认值
   */
  static String resolveSpelValue(String keyExpression, Method method, Object[] args) {
    if (!StringUtils.hasText(keyExpression)) {
      return defaultSpelResult(method, args);
    }
    try {
      Expression expression = EXPRESSION_CACHE.computeIfAbsent(keyExpression, PARSER::parseExpression);
      StandardEvaluationContext context = createContext(method, args);
      Object value = expression.getValue(context);
      return value != null ? value.toString() : "null";
    } catch (Exception e) {
      LOG.warn(
          "SpEL key 求值失败, expression={}, method={}, 使用默认 key, error={}",
          keyExpression,
          method.getName(),
          e.getMessage());
      return defaultSpelResult(method, args);
    }
  }

  // ============================== StandardEvaluationContext 构造 ==============================

  /**
   * 基于方法元数据与参数值构造 {@link StandardEvaluationContext}。
   *
   * <p>绑定的变量：
   *
   * <ul>
   *   <li>方法参数索引变量：{@code #p0}, {@code #p1}, ... 与 {@code #a0}, {@code #a1}, ...</li>
   *   <li>方法参数名变量：通过 {@link ParameterNameDiscoverer} 解析后绑定为 {@code #paramName}</li>
   * </ul>
   *
   * <p>例：方法 {@code void update(Long userId, String code)} 调用后，上下文同时可见
   * {@code #userId}、{@code #p0}、{@code #a0} 指向同一值；{@code #p1}、{@code #a1}、{@code #code} 指向第二参数。
   *
   * @param method 目标方法（用于解析形参名）
   * @param args 实参数组
   * @return 已绑定方法参数的 {@link StandardEvaluationContext} 实例，不会为 {@code null}
   */
  public static StandardEvaluationContext createContext(Method method, Object[] args) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    if (args == null || args.length == 0) {
      return context;
    }

    String[] paramNames = PARAM_DISCOVERER.getParameterNames(method);
    for (int i = 0; i < args.length; i++) {
      // 绑定索引变量（#p0/#a0），对标 Spring Cache 约定
      context.setVariable("p" + i, args[i]);
      context.setVariable("a" + i, args[i]);
      // 绑定参数名变量（#paramName），需编译时保留参数名信息（-parameters 或 debug 符号）
      if (paramNames != null && paramNames[i] != null) {
        context.setVariable(paramNames[i], args[i]);
      }
    }
    return context;
  }

  // ============================== 默认 key 生成规则 ==============================

  /**
   * SpEL 表达式为空或求值失败时的默认结果：方法名 + "_" + 参数拼接。
   *
   * @param method 方法
   * @param args 参数值
   * @return 默认 SpEL 结果字符串
   */
  private static String defaultSpelResult(Method method, Object[] args) {
    StringBuilder sb = new StringBuilder(method.getName());
    if (args != null && args.length > 0) {
      for (Object arg : args) {
        sb.append("_").append(arg);
      }
    }
    return sb.toString();
  }
}
