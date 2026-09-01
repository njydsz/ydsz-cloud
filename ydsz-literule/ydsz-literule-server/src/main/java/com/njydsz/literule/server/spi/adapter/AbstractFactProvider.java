package com.njydsz.literule.server.spi.adapter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.server.spi.FactProvider;

/**
 * FactProvider 抽象基类（P3-1 SPI Adapter）
 *
 * <p>提供通用的模板方法、异常隔离、生命周期钩子，简化业务方实现。
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * {@literal @Component}
 * public class ProjectBudgetFactProvider extends AbstractFactProvider {
 *
 *     {@literal @Override}
 *     public String getProviderId() {
 *         return "project-budget";
 *     }
 *
 *     {@literal @Override}
 *     protected Map<String, Object> doGetFacts(RuleContextVO context) {
 *         // 业务逻辑：查询 DB/Redis/API
 *         return Map.of("budgetUsedRatio", 0.85);
 *     }
 * }
 * </pre>
 *
 * <h3>异常处理</h3>
 *
 * <p>{@link #doGetFacts} 中抛出的异常会被自动捕获并记录 WARN 日志，返回空 Map， 不会中断其他 provider 的执行。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public abstract class AbstractFactProvider implements FactProvider {

  /** 初始化标记 */
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  @Override
  public final Map<String, Object> getFacts(RuleContextVO context) {
    ensureInit();
    // 前置检查：若禁用则直接返回空
    if (!isEnabled()) {
      return Map.of();
    }
    try {
      return doGetFacts(context);
    } catch (Exception e) {
      log.warn("[LiteRule-Fact] Provider {} 采集异常: {}", getProviderId(), e.getMessage());
      return Map.of();
    }
  }

  /** 确保初始化仅执行一次（线程安全） */
  private void ensureInit() {
    if (initialized.compareAndSet(false, true)) {
      onInit();
    }
  }

  /**
   * 子类实现：采集事实数据
   *
   * <p>实现方可放心抛出异常，基类会自动捕获并返回空 Map（不影响其他 provider）。
   *
   * @param context 规则上下文（含已有 facts）
   * @return 事实数据 Map；null 或空 Map 表示无数据
   */
  protected abstract Map<String, Object> doGetFacts(RuleContextVO context);

  /**
   * 初始化钩子（可选覆写）
   *
   * <p>在首次调用 {@link #getFacts} 前自动执行一次，用于初始化连接池、预热缓存等。
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
  @Override
  public int getOrder() {
    return 0;
  }
}
