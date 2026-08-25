package com.njydsz.workflow.server.engine.expr;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 表达式引擎注册表（对标 warm-flow ListenerStrategy 热插拔机制）。
 *
 * <p>自动发现 Spring 容器中所有 {@link ExpressionEvaluator} 实现，按 {@link ExpressionEvalStrategy} 注册，
 * 运行时通过配置动态切换活动引擎。
 *
 * <p><b>热插拔机制：</b>
 *
 * <ul>
 *   <li>启动时自动发现所有 {@link ExpressionEvaluator} Bean</li>
 *   <li>通过 {@link ExpressionEvalStrategy#getCode()} 匹配策略</li>
 *   <li>运行时通过 {@link #setActiveStrategy(ExpressionEvalStrategy)} 动态切换</li>
 *   <li>未配置或未匹配时回退到 {@link ExpressionEvalStrategy#AVIATOR}</li>
 * </ul>
 *
 * <p><b>扩展方式：</b>业务方实现 {@link ExpressionEvaluator} 接口，在 Bean 名称或
 * 自定义注解中标注策略标识，注册表自动发现并注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExpressionEvalStrategy 表达式引擎策略枚举
 * @see ExpressionEvaluator 表达式求值器接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionEvaluatorRegistry {

  /** 所有表达式求值器实现 */
  private final List<ExpressionEvaluator> evaluators;

  /** 可选的活动策略（由配置注入） */
  private final ObjectProvider<FlowExpressionStrategyProvider> strategyProvider;

  /** 策略注册表 */
  private final Map<ExpressionEvalStrategy, ExpressionEvaluator> registry =
      new EnumMap<>(ExpressionEvalStrategy.class);

  /** 当前活动策略 */
  private ExpressionEvalStrategy activeStrategy = ExpressionEvalStrategy.AVIATOR;

  /** 当前活动的求值器 */
  private ExpressionEvaluator activeEvaluator;

  /**
   * 初始化注册表。
   *
   * <p>遍历所有 {@link ExpressionEvaluator} Bean，按策略类型注册。
   * 策略匹配规则：Bean 类名包含策略名称（不区分大小写）。
   */
  @PostConstruct
  public void init() {
    for (ExpressionEvaluator evaluator : evaluators) {
      ExpressionEvalStrategy strategy = detectStrategy(evaluator);
      if (strategy != null) {
        ExpressionEvaluator prev = registry.put(strategy, evaluator);
        if (prev != null) {
          log.warn("[Flow] 表达式引擎策略重复注册: strategy={} new={} old={}",
              strategy, evaluator.getClass().getSimpleName(), prev.getClass().getSimpleName());
        } else {
          log.info("[Flow] 表达式引擎已注册: strategy={} impl={}",
              strategy, evaluator.getClass().getSimpleName());
        }
      }
    }

    // 应用配置的策略
    FlowExpressionStrategyProvider provider = strategyProvider.getIfAvailable();
    if (provider != null) {
      ExpressionEvalStrategy configured = provider.getStrategy();
      if (configured != null && registry.containsKey(configured)) {
        activeStrategy = configured;
      }
    }

    // 设置活动求值器
    activeEvaluator = registry.get(activeStrategy);
    if (activeEvaluator == null) {
      log.warn("[Flow] 活动策略 {} 未注册，回退到 AVIATOR", activeStrategy);
      activeStrategy = ExpressionEvalStrategy.AVIATOR;
      activeEvaluator = registry.get(ExpressionEvalStrategy.AVIATOR);
    }

    log.info("[Flow] 当前活动表达式引擎: strategy={} impl={}",
        activeStrategy, activeEvaluator != null ? activeEvaluator.getClass().getSimpleName() : "none");
  }

  /**
   * 动态切换活动策略（运行时热插拔）。
   *
   * @param strategy 目标策略
   * @throws IllegalArgumentException 当策略未注册时抛出
   */
  public void setActiveStrategy(ExpressionEvalStrategy strategy) {
    ExpressionEvaluator evaluator = registry.get(strategy);
    if (evaluator == null) {
      throw new IllegalArgumentException("表达式引擎策略未注册: " + strategy);
    }
    this.activeStrategy = strategy;
    this.activeEvaluator = evaluator;
    log.info("[Flow] 表达式引擎已切换: strategy={} impl={}", strategy, evaluator.getClass().getSimpleName());
  }

  /**
   * 获取当前活动的表达式求值器。
   *
   * @return 活动求值器
   */
  public ExpressionEvaluator getActiveEvaluator() {
    return activeEvaluator;
  }

  /**
   * 获取当前活动策略。
   *
   * @return 活动策略枚举
   */
  public ExpressionEvalStrategy getActiveStrategy() {
    return activeStrategy;
  }

  /**
   * 获取指定策略的求值器。
   *
   * @param strategy 策略枚举
   * @return 对应求值器，未注册返回 {@code null}
   */
  public ExpressionEvaluator getEvaluator(ExpressionEvalStrategy strategy) {
    return registry.get(strategy);
  }

  /**
   * 检测求值器对应的策略类型。
   *
   * <p>通过类名匹配：包含 "aviator" → AVIATOR，包含 "spel" → SPEL。
   *
   * @param evaluator 表达式求值器
   * @return 检测到的策略，未匹配返回 {@code null}
   */
  private ExpressionEvalStrategy detectStrategy(ExpressionEvaluator evaluator) {
    String className = evaluator.getClass().getSimpleName().toLowerCase();
    if (className.contains("spel")) {
      return ExpressionEvalStrategy.SPEL;
    }
    if (className.contains("aviator")) {
      return ExpressionEvalStrategy.AVIATOR;
    }
    // 默认归类为 AVIATOR
    return ExpressionEvalStrategy.AVIATOR;
  }

  /**
   * 表达式策略配置提供者接口。
   *
   * <p>业务方可实现此接口并通过 {@code @Bean} 注册，提供当前配置的表达式策略。
   */
  public interface FlowExpressionStrategyProvider {
    /**
     * 获取当前配置的表达式策略。
     *
     * @return 策略枚举，未配置返回 {@code null}
     */
    ExpressionEvalStrategy getStrategy();
  }
}
