package com.njydsz.literule.server.core.engine;

import java.util.List;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

/**
 * 评估拦截器接口（P0-3 拦截器链模式）
 *
 * <p>将 {@link com.njydsz.literule.server.core.DefaultRuleEngine} 的职责拆分为可组合的拦截器链，每个拦截器负责一个横切关注点（缓存、熔断、超时、指标、追踪等）。
 *
 * <h3>拦截器链执行顺序</h3>
 *
 * <ol>
 *   <li>{@code CacheInterceptor} — 缓存查询/写入
 *   <li>{@code FactInjectionInterceptor} — 事实/模型注入
 *   <li>{@code CircuitBreakerInterceptor} — 熔断检查
 *   <li>{@code TimeoutInterceptor} — 超时控制
 *   <li>{@code MetricsInterceptor} — 指标记录
 *   <li>{@code TraceInterceptor} — 轨迹记录
 * </ol>
 *
 * <p>拦截器通过 {@link EvaluationContext} 在链中传递上下文数据（如 enriched context、cache hit 结果）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface EvaluationInterceptor {

  /**
   * 执行拦截器逻辑
   *
   * <p>拦截器可以选择：
   *
   * <ul>
   *   <li>调用 {@link EvaluationContext#proceed()} 继续执行链中后续拦截器
   *   <li>直接返回 {@link EvaluationContext#getResult()} 短路后续逻辑（如缓存命中）
   * </ul>
   *
   * @param context 评估上下文（含规则上下文、传递数据）
   * @return 评估结果列表
   * @throws Exception 拦截器执行异常
   */
  List<RuleResult> intercept(EvaluationContext context) throws Exception;

  /**
   * 拦截器在链中的顺序（数值越小越先执行）
   *
   * @return 顺序值
   */
  default int order() {
    return 0;
  }

  /**
   * 拦截器名称（用于日志和监控）
   *
   * @return 名称
   */
  default String name() {
    return this.getClass().getSimpleName();
  }
}
