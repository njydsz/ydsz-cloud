package com.njydsz.workflow.server.engine.expr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.ExpressionTooLongException;
import com.googlecode.aviator.runtime.function.LambdaFunction;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.SysException;

/**
 * 工作流内置 Aviator 表达式求值器（安全加固版）。
 *
 * <p>引擎自包含的表达式求值实现，基于 Aviator 5.x。当 Aviator 在 classpath 中且无其他 {@link ExpressionEvaluator}
 * 实现时自动启用。
 *
 * <h3>安全加固措施（P0-A8）</h3>
 *
 * <ul>
 *   <li><b>禁用反射特性</b> — 通过自定义 {@link AviatorEvaluatorInstance} 禁用 {@link Feature#NewInstance} 与
 *       {@link Feature#Module}，防止恶意表达式通过反射调用 {@code Runtime.getRuntime().exec(...)}
 *   <li><b>表达式长度限制</b> — 单表达式上限 4KB（通过 {@link Options#MAX_LENGTH} 或自定义校验），防止超长表达式耗尽 CPU
 *   <li><b>缓存编译结果</b> — 使用 {@link ConcurrentHashMap} 缓存编译后的表达式，避免重复编译开销
 * </ul>
 *
 * <p>业务系统如需更强大的规则引擎能力（如规则链、决策表），可自行实现 {@link ExpressionEvaluator} 并注册为 Bean 覆盖本实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(AviatorEvaluatorInstance.class)
@ConditionalOnMissingBean(ExpressionEvaluator.class)
public class AviatorExpressionEvaluator implements ExpressionEvaluator {

  /** 表达式最大长度限制（字节），防止超长表达式攻击 */
  private static final int MAX_EXPRESSION_LENGTH = 4096;

  /**
   * 自定义 Aviator 求值器实例（禁用反射特性）。
   *
   * <p>使用独立实例而非全局静态实例，确保安全配置隔离。
   */
  private final AviatorEvaluatorInstance instance;

  /**
   * 表达式编译缓存（表达式文本 → 编译后的 LambdaFunction）。
   *
   * <p>缓存编译结果避免重复编译，同时利用 ConcurrentHashMap 的线程安全特性。
   */
  private final ConcurrentHashMap<String, LambdaFunction> compiledCache = new ConcurrentHashMap<>(128);

  /**
   * 构造安全加固的 Aviator 求值器实例。
   *
   * <p>禁用反射特性（NewInstance/Module），确保表达式无法访问任意 Java 类。
   */
  public AviatorExpressionEvaluator() {
    this.instance = AviatorEvaluatorInstance.newInstance();
    // 安全加固：禁用反射特性，防止表达式注入调用任意 Java 方法
    this.instance.disableFeature(Feature.NewInstance);
    this.instance.disableFeature(Feature.Module);
    // 设置表达式长度上限
    this.instance.setOption(Options.MAX_LENGTH, MAX_EXPRESSION_LENGTH);
    // 关闭追踪（生产环境不需要）
    this.instance.setOption(Options.TRACE_EVAL, false);
    log.info("[Flow][Aviator] 安全加固求值器已初始化（禁用 NewInstance/Module 反射特性，MAX_LENGTH={}）",
        MAX_EXPRESSION_LENGTH);
  }

  @Override
  public boolean evalBoolean(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return true;
    }
    try {
      Object result = execute(expression, variables);
      if (result instanceof Boolean b) {
        return b;
      }
      if (result instanceof Number n) {
        return n.doubleValue() != 0;
      }
      return result != null;
    } catch (Exception e) {
      log.warn("[Flow][Aviator] 布尔表达式求值失败: expr='{}' err={}", expression, e.getMessage());
      return false;
    }
  }

  @Override
  public Object eval(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      return execute(expression, variables);
    } catch (Exception e) {
      log.warn("[Flow][Aviator] 表达式求值失败: expr='{}' err={}", expression, e.getMessage());
      return null;
    }
  }

  /**
   * 编译并执行表达式（带缓存）。
   *
   * <p>先从缓存获取编译结果，未命中时编译并缓存。使用 {@link LambdaFunction#call(Map)} 执行。
   *
   * @param expression 表达式文本
   * @param variables 变量 Map
   * @return 执行结果
   * @throws SysException 当表达式超长或语法错误时抛出
   */
  private Object execute(String expression, Map<String, Object> variables) {
    try {
      LambdaFunction compiled = compiledCache.computeIfAbsent(expression, expr -> {
        try {
          // 先尝试编译为 LambdaFunction（性能优于 execute）
          AviatorFunction func = instance.compile(expr);
          if (func instanceof LambdaFunction lambda) {
            return lambda;
          }
          // 非 LambdaFunction 时包装为 LambdaFunction
          return new LambdaFunction() {
            @Override
            public AviatorFunction call(Map<String, Object> env) {
              return (AviatorFunction) instance.execute(expr, env);
            }

            @Override
            public String getName() {
              return expr;
            }
          };
        } catch (ExpressionTooLongException e) {
          throw new SysException("workflow.expr.too_long", "表达式超长（上限" + MAX_EXPRESSION_LENGTH + "字节）");
        } catch (ExpressionSyntaxErrorException e) {
          throw new SysException("workflow.expr.syntax_error", "表达式语法错误: " + e.getMessage());
        }
      });
      @SuppressWarnings("unchecked")
      Map<String, Object> env = variables != null ? variables : Map.of();
      return compiled.call(env);
    } catch (SysException e) {
      throw e;
    } catch (Exception e) {
      throw new SysException("workflow.expr.eval_failed", "表达式执行失败: " + e.getMessage());
    }
  }
}
