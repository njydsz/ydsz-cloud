package com.njydsz.common.lock.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * 锁键 SpEL 表达式解析工具
 *
 * <p>统一各切面（分布式锁 / 幂等 / 分布式调度）的 SpEL 解析逻辑，支持两种写法：
 *
 * <ul>
 *   <li><b>模板模式</b>：{@code "order:#{#orderId}"}，逐段替换 {@code #{...}} 占位符， 保留模板中的常量前缀
 *   <li><b>整串 SpEL 模式</b>：{@code "'order:' + #orderId"}，将整个 key 作为 SpEL 表达式求值， 解析失败时回退为原字符串
 * </ul>
 *
 * <p><b>线程安全：</b>表达式解析器、参数名发现器与表达式缓存均为线程安全的无状态组件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LockExpressionUtils {

  /** SpEL 占位符正则：匹配 {@code #{...}} 内的表达式 */
  private static final Pattern SPEL_PLACEHOLDER_PATTERN = Pattern.compile("#\\{(.+?)}");

  /** SpEL 表达式解析器（线程安全） */
  private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

  /** 参数名发现器（线程安全） */
  private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new DefaultParameterNameDiscoverer();

  /** 表达式缓存，避免重复解析相同表达式（初始容量 64） */
  private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(64);

  private LockExpressionUtils() {
    throw new AssertionError("工具类禁止实例化");
  }

  /**
   * 解析锁键表达式
   *
   * <p>按 {@code #{...}} 占位符或整串 SpEL 两种模式解析，参数通过方法参数名绑定。
   *
   * @param keyExpression 注解上的 key 表达式（可为空串或 null）
   * @param method 目标方法（用于获取参数名）
   * @param args 方法参数
   * @return 解析后的锁键；无法解析时返回原表达式
   */
  public static String resolve(String keyExpression, Method method, Object[] args) {
    if (keyExpression == null || keyExpression.isEmpty()) {
      return keyExpression;
    }
    if (keyExpression.contains("#{")) {
      return resolveTemplate(keyExpression, method, args);
    }
    if (keyExpression.contains("#")) {
      return resolveFullExpression(keyExpression, method, args);
    }
    return keyExpression;
  }

  /**
   * 模板模式解析：逐段替换 {@code #{...}} 占位符
   *
   * @param template 包含占位符的模板字符串
   * @param method 目标方法
   * @param args 方法参数
   * @return 替换后的完整锁键
   */
  private static String resolveTemplate(String template, Method method, Object[] args) {
    Matcher matcher = SPEL_PLACEHOLDER_PATTERN.matcher(template);
    StringBuffer result = new StringBuffer(template.length());
    while (matcher.find()) {
      Object value = evaluate(matcher.group(1), method, args);
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * 整串 SpEL 模式解析：将整个 key 作为表达式求值，失败时回退为原字符串
   *
   * @param expression 完整 SpEL 表达式
   * @param method 目标方法
   * @param args 方法参数
   * @return 求值结果或原表达式
   */
  private static String resolveFullExpression(String expression, Method method, Object[] args) {
    try {
      Object value = evaluate(expression, method, args);
      return value != null ? String.valueOf(value) : expression;
    } catch (Exception e) {
      return expression;
    }
  }

  /**
   * 求值单个 SpEL 表达式（带缓存）
   *
   * @param expression SpEL 表达式
   * @param method 目标方法（用于绑定参数名）
   * @param args 方法参数
   * @return 求值结果，可能为 null
   */
  private static Object evaluate(String expression, Method method, Object[] args) {
    Expression parsed =
        EXPRESSION_CACHE.computeIfAbsent(expression, EXPRESSION_PARSER::parseExpression);
    SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
    String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
    if (parameterNames != null && args != null) {
      for (int i = 0; i < parameterNames.length && i < args.length; i++) {
        context.setVariable(parameterNames[i], args[i]);
      }
    }
    return parsed.getValue(context);
  }
}
