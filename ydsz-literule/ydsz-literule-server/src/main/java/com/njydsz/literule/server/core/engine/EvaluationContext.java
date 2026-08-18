package com.njydsz.literule.server.core.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.Setter;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

/**
 * 评估拦截器链上下文（P0-3 拦截器链模式）
 *
 * <p>在拦截器链中传递的上下文对象，包含：
 *
 * <ul>
 *   <li>原始规则上下文（{@link #ruleContext}）
 *   <li>短路结果（{@link #shortCircuitResult}）— 非 null 时拦截器链直接返回此结果
 *   <li>传递属性（{@link #attributes}）— 拦截器间共享数据的 Map
 *   <li>Proceed Supplier（{@link #proceedSupplier}）— 调用以继续执行链
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Getter
@Setter
public class EvaluationContext {

  /** 原始规则上下文 */
  private final RuleContext ruleContext;

  /** 短路结果：非 null 时拦截器链直接返回此结果，不再执行后续拦截器 */
  private List<RuleResult> shortCircuitResult;

  /** 拦截器间共享属性 */
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();

  /** 调用以继续执行链中后续拦截器 */
  private Supplier<List<RuleResult>> proceedSupplier;

  public EvaluationContext(RuleContext ruleContext) {
    this.ruleContext = ruleContext;
  }

  /**
   * 继续执行链中后续拦截器
   *
   * @return 后续拦截器的执行结果
   */
  public List<RuleResult> proceed() {
    if (proceedSupplier != null) {
      return proceedSupplier.get();
    }
    return List.of();
  }

  /**
   * 设置短路结果（后续拦截器不会执行）
   *
   * @param result 要短路返回的结果
   */
  public void shortCircuit(List<RuleResult> result) {
    this.shortCircuitResult = result;
  }

  /**
   * 是否已短路
   *
   * @return true=已短路
   */
  public boolean isShortCircuited() {
    return shortCircuitResult != null;
  }

  /**
   * 获取最终结果（短路结果或空列表）
   *
   * @return 结果
   */
  public List<RuleResult> getResult() {
    return shortCircuitResult != null ? shortCircuitResult : List.of();
  }

  /**
   * 设置属性
   *
   * @param key 键
   * @param value 值   */
  public void setAttribute(String key, Object value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }

  /**
   * 获取属性
   *
   * @param key 键
   * @param <T> 值类型
   * @return 值；不存在返回 null
   */
  @SuppressWarnings("unchecked")
  public <T> T getAttribute(String key) {
    return (T) attributes.get(key);
  }
}
