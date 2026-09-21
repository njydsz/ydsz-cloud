package com.njydsz.common.util.spring;

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
 * SpEL 键解析工具
 *
 * <p>统一幂等、分布式锁、限流等场景的 SpEL 表达式解析逻辑，支持两种写法：
 *
 * <ul>
 *   <li><b>模板模式</b>：{@code "order:#{#orderId}"}，逐段替换 {@code #{...}} 占位符， 保留模板中的常量前缀
 *   <li><b>整串 SpEL 模式</b>：{@code "'order:' + #orderId"}，将整个 key 作为 SpEL 表达式求值， 解析失败时回退为原字符串
 * </ul>
 *
 * <p><b>线程安全：</b>表达式解析器、参数名发现器与表达式缓存均为线程安全的无状态组件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SpELKeyUtils {

  /** 模板占位符正则：匹配 #{...} 形式 */
  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([^}]+)}");

  /** SpEL 表达式解析器（线程安全） */
  private static final ExpressionParser PARSER = new SpelExpressionParser();

  /** 参数名发现器（线程安全） */
  private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new DefaultParameterNameDiscoverer();

  /** 表达式缓存（避免重复解析） */
  private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

  private SpELKeyUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 解析包含 SpEL 占位符的键表达式
   *
   * <p>如果表达式包含 {@code #{...}} 占位符则使用模板模式逐个替换； 否则尝试将整个表达式作为 SpEL 求值，失败时回退为原字符串。
   *
   * @param expression 键表达式
   * @param method 目标方法（用于获取参数名）
   * @param args 方法参数值
   * @return 解析后的键
   */
  public static String resolve(String expression, Method method, Object[] args) {
    if (expression == null || expression.isEmpty()) {
      return expression;
    }

    // 模板模式：包含 #{...} 占位符
    if (expression.contains("#{")) {
      return resolveTemplate(expression, method, args);
    }

    // 整串 SpEL 模式：将整个表达式作为 SpEL 求值
    try {
      return resolveSpEL(expression, method, args);
    } catch (Exception e) {
      // 解析失败时回退为原字符串
      return expression;
    }
  }

  /**
   * 模板模式解析：逐段替换 #{...} 占位符
   *
   * @param template 模板字符串
   * @param method 目标方法
   * @param args 方法参数值
   * @return 替换后的字符串
   */
  private static String resolveTemplate(String template, Method method, Object[] args) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find()) {
      result.append(template, lastEnd, matcher.start());
      String spelExpr = matcher.group(1);
      String resolved = resolveSpEL(spelExpr, method, args);
      result.append(resolved != null ? resolved : matcher.group());
      lastEnd = matcher.end();
    }
    result.append(template.substring(lastEnd));
    return result.toString();
  }

  /**
   * 将表达式作为 SpEL 求值
   *
   * @param expression SpEL 表达式
   * @param method 目标方法（用于获取参数名）
   * @param args 方法参数值
   * @return 求值结果字符串，失败返回 null
   */
  private static String resolveSpEL(String expression, Method method, Object[] args) {
    Expression expr = EXPRESSION_CACHE.computeIfAbsent(expression, PARSER::parseExpression);

    // 创建不读取外部上下文的简单安全上下文
    SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

    // 注入方法参数为 SpEL 变量
    String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
    if (paramNames != null) {
      for (int i = 0; i < paramNames.length && i < args.length; i++) {
        if (paramNames[i] != null) {
          context.setVariable(paramNames[i], args[i]);
        }
      }
    }

    Object value = expr.getValue(context);
    return value != null ? value.toString() : null;
  }
}
