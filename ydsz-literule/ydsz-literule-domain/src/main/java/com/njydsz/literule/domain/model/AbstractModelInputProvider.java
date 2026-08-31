package com.njydsz.literule.domain.model;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.api.RuleContext;

/**
 * ModelInputProvider 抽象基类（P3-1 SPI Adapter）
 *
 * <p>提供通用的模板方法、异常隔离、生命周期钩子，简化业务方实现。
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * {@literal @Component}
 * public class RiskScoreModelProvider extends AbstractModelInputProvider {
 *
 *     {@literal @Override}
 *     public String getModelId() {
 *         return "risk-score-v1";
 *     }
 *
 *     {@literal @Override}
 *     protected Map<String, Object> doGetModelOutput(RuleContext context) {
 *         double score = modelService.predict(context.getFacts());
 *         return Map.of("score", score);
 *     }
 * }
 * </pre>
 *
 * <h3>异常处理</h3>
 *
 * <p>{@link #doGetModelOutput} 中抛出的异常会被自动捕获并记录 WARN 日志，返回空 Map， 不会中断其他 provider 的执行。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public abstract class AbstractModelInputProvider implements ModelInputProvider {

  /** 初始化标记 */
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  @Override
  public final Map<String, Object> getModelOutput(RuleContext context) {
    ensureInit();
    if (!isEnabled()) {
      return Map.of();
    }
    try {
      return doGetModelOutput(context);
    } catch (Exception e) {
      log.warn("[LiteRule-Model] Provider {} 调用异常: {}", getModelId(), e.getMessage());
      return Map.of();
    }
  }

  private void ensureInit() {
    if (initialized.compareAndSet(false, true)) {
      onInit();
    }
  }

  /**
   * 子类实现：获取模型输出
   *
   * <p>实现方可放心抛出异常，基类自动捕获并返回空 Map。
   *
   * @param context 规则上下文（含 facts）
   * @return 模型输出 Map；null 或空 Map 表示无输出
   */
  protected abstract Map<String, Object> doGetModelOutput(RuleContext context);

  /**
   * 初始化钩子（可选覆写）
   *
   * <p>在首次调用 {@link #getModelOutput} 前自动执行一次，用于初始化模型连接等。
   */
  protected void onInit() {
    // 默认空实现
  }

  /**
   * 是否启用（默认 true，子类可覆写）
   *
   * @return true=启用；false=禁用
   */
  @Override
  public boolean isEnabled() {
    return true;
  }

  /**
   * 执行优先级（默认 0，子类可覆写）
   *
   * @return 优先级；数字小的先执行
   */
  public int getOrder() {
    return 0;
  }
}
