package com.njydsz.literule.server.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleResult;

/**
 * 拦截器链（P0-3 拦截器链模式 - 核心编排器）
 *
 * <p>管理 {@link EvaluationInterceptor} 的有序集合，提供链式调用能力。 拦截器按 {@link EvaluationInterceptor#order()} 升序排列，依次执行。
 *
 * <h3>短路语义</h3>
 *
 * <p>如果某个拦截器设置了短路结果（{@link EvaluationContext#shortCircuit}），链的执行立即停止，返回短路结果。
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * InterceptorChain chain = new InterceptorChain();
 * chain.addInterceptor(new CacheInterceptor(cache));
 * chain.addInterceptor(new MetricsInterceptor(metrics));
 * chain.addInterceptor(new CoreEvaluationInterceptor(engineCore));
 *
 * List&lt;RuleResult&gt; results = chain.execute(context);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class InterceptorChain {

  /** 拦截器列表（按 order 排序） */
  private final List<EvaluationInterceptor> interceptors = new ArrayList<>();

  /**
   * 添加拦截器
   *
   * @param interceptor 拦截器
   * @return this（链式调用）
   */
  public InterceptorChain addInterceptor(EvaluationInterceptor interceptor) {
    if (interceptor != null) {
      interceptors.add(interceptor);
      interceptors.sort(Comparator.comparingInt(EvaluationInterceptor::order));
    }
    return this;
  }

  /**
   * 执行拦截器链
   *
   * @param context 评估上下文
   * @return 评估结果
   */
  public List<RuleResult> execute(EvaluationContext context) {
    return executeChain(context, 0);
  }

  /**
   * 递归执行链中第 index 个拦截器
   *
   * @param context 上下文
   * @param index 当前拦截器索引
   * @return 评估结果
   */
  private List<RuleResult> executeChain(EvaluationContext context, int index) {
    if (index >= interceptors.size()) {
      // 链结束，返回短路结果（如有）或空列表
      return context.getResult();
    }
    EvaluationInterceptor current = interceptors.get(index);
    // 设置 proceedSupplier：调用时执行下一个拦截器
    context.setProceedSupplier(() -> executeChain(context, index + 1));
    try {
      List<RuleResult> result = current.intercept(context);
      // 如果拦截器设置了短路，使用短路结果
      if (context.isShortCircuited()) {
        return context.getShortCircuitResult();
      }
      return result;
    } catch (Exception e) {
      log.warn("[LiteRule] 拦截器 {} 执行异常: {}", current.name(), e.getMessage());
      throw new RuntimeException("拦截器链执行失败: " + current.name(), e);
    }
  }

  /**
   * 获取拦截器数量
   *
   * @return 数量
   */
  public int size() {
    return interceptors.size();
  }
}
