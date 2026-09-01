package com.njydsz.literule.server.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;

/**
 * 规则注册表 — 管理规则的注册、注销和索引维护
 *
 * <p>封装规则的生命周期管理职责（从 {@link DefaultRuleEngine} 拆分）， 包括：
 *
 * <ul>
 *   <li>按优先级升序注册规则（增量保序插入，避免全量 sort）
 *   <li>热更新覆盖（同编码规则自动注销旧版本）
 *   <li>注销规则并同步清理索引、统计、熔断器状态
 *   <li>规则变更时精准失效评估结果缓存
 * </ul>
 *
 * <p>线程安全：底层使用 {@link CopyOnWriteArrayList}，注册/注销操作本身原子； 索引更新委托 {@link RuleIndexer}（内部使用锁）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleRegistry {

  /** 已注册规则列表（按优先级升序排列） */
  private final CopyOnWriteArrayList<Rule> rules = new CopyOnWriteArrayList<>();

  /** 规则索引器（大规则量场景索引优化） */
  private final RuleIndexer ruleIndexer = new RuleIndexer();

  /** 默认索引启用阈值（规则数达到该值才启用索引） */
  private static final int DEFAULT_INDEX_BYPASS_THRESHOLD = 200;

  /** 索引未启用时的规则量阈值 */
  private volatile int indexBypassThreshold = DEFAULT_INDEX_BYPASS_THRESHOLD;

  /** 统计记录器（用于清理注销规则的统计） */
  private volatile RuleStatistics statistics;

  /** 熔断器（可选，用于清理注销规则的熔断状态） */
  private volatile RuleCircuitBreaker circuitBreaker;

  /** 监控指标（可选，用于记录规则注册数） */
  private volatile RuleMetrics metrics;

  /** 评估结果缓存（可选，用于精准失效缓存） */
  private volatile EvaluationResultCache evaluationResultCache;

  /**
   * 注册规则到注册表
   *
   * <p>注册流程：
   *
   * <ol>
   *   <li>校验规则非空且 code 非空
   *   <li>移除同编码旧规则（支持热更新覆盖）
   *   <li>二分查找按 priority 升序插入
   *   <li>更新规则索引
   *   <li>规则数首次超过阈值时启用索引模式
   *   <li>精准失效该规则的评估结果缓存
   * </ol>
   *
   * @param rule 待注册规则；为 null 或 code 为 null 时静默跳过
   */
  public void register(Rule rule) {
    if (rule == null || rule.getCode() == null) {
      return;
    }
    // 先移除同编码旧规则（支持热更新覆盖）
    unregister(rule.getCode());
    // 增量保序插入：二分查找插入位置，避免全量 sort
    int insertIdx = binarySearchInsertIndex(rule.getPriority());
    rules.add(insertIdx, rule);
    // 增量更新索引
    ruleIndexer.addToIndex(rule);
    // 当规则数首次超过阈值时，重建索引启用索引模式
    if (!ruleIndexer.isIndexEnabled() && rules.size() >= indexBypassThreshold) {
      ruleIndexer.rebuildIndex(rules);
    }
    // 规则变更时精准失效该规则的评估结果缓存
    if (evaluationResultCache != null) {
      evaluationResultCache.invalidateRule(rule.getCode());
    }
    recordRegisteredRules();
    log.info(
        "[LiteRule] 规则已注册: code={}, name={}, priority={}, total={}",
        rule.getCode(),
        rule.getName(),
        rule.getPriority(),
        rules.size());
  }

  /**
   * 注销指定编码的规则
   *
   * <p>从规则列表和索引中移除指定编码的规则，并同步清理统计、熔断器状态。
   *
   * @param ruleCode 规则编码；为 null 时静默跳过
   */
  public void unregister(String ruleCode) {
    if (ruleCode == null) {
      return;
    }
    rules.removeIf(r -> ruleCode.equals(r.getCode()));
    ruleIndexer.removeFromIndex(ruleCode);
    // 清理该规则的统计数据，避免统计数据 Map 无限增长
    if (statistics != null) {
      statistics.removeRuleStats(ruleCode);
    }
    // 清理熔断器状态
    if (circuitBreaker != null) {
      circuitBreaker.reset(ruleCode);
    }
    // 规则变更时精准失效该规则的评估结果缓存
    if (evaluationResultCache != null) {
      evaluationResultCache.invalidateRule(ruleCode);
    }
    recordRegisteredRules();
  }

  /**
   * 获取已注册规则列表（按优先级升序排列）
   *
   * <p>返回的是底层列表的直接引用，遍历安全；但不应用于外部修改。
   *
   * @return 已注册规则列表
   */
  public List<Rule> getRules() {
    return rules;
  }

  /**
   * 获取当前注册规则数
   *
   * @return 注册规则数
   */
  public int size() {
    return rules.size();
  }

  /**
   * 获取规则索引器
   *
   * @return 规则索引器
   */
  public RuleIndexer getRuleIndexer() {
    return ruleIndexer;
  }

  /**
   * 设置索引绕过阈值
   *
   * @param indexBypassThreshold 索引绕过阈值；< 1 时视为 1
   */
  public void setIndexBypassThreshold(int indexBypassThreshold) {
    this.indexBypassThreshold = Math.max(1, indexBypassThreshold);
  }

  /**
   * 获取索引绕过阈值
   *
   * @return 索引绕过阈值
   */
  public int getIndexBypassThreshold() {
    return indexBypassThreshold;
  }

  /**
   * 设置统计记录器
   *
   * @param statistics 统计记录器
   */
  public void setStatistics(RuleStatistics statistics) {
    this.statistics = statistics;
  }

  /**
   * 设置熔断器
   *
   * @param circuitBreaker 熔断器
   */
  public void setCircuitBreaker(RuleCircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  /**
   * 设置监控指标
   *
   * @param metrics 监控指标
   */
  public void setMetrics(RuleMetrics metrics) {
    this.metrics = metrics;
  }

  /**
   * 设置评估结果缓存
   *
   * @param evaluationResultCache 评估结果缓存
   */
  public void setEvaluationResultCache(EvaluationResultCache evaluationResultCache) {
    this.evaluationResultCache = evaluationResultCache;
  }

  /** 记录当前注册规则数到监控指标 */
  private void recordRegisteredRules() {
    if (metrics != null) {
      metrics.recordRegisteredRules(rules.size());
    }
  }

  /**
   * 二分查找按 priority 的插入位置（priority 升序）
   *
   * <p>由于 rules 已按 priority 升序排列，使用二分查找可将"找位置"从 O(n) 降到 O(log n)。
   *
   * @param priority 待插入规则的优先级
   * @return 插入位置索引
   */
  private int binarySearchInsertIndex(int priority) {
    int low = 0;
    int high = rules.size();
    while (low < high) {
      int mid = (low + high) >>> 1;
      int midPriority = rules.get(mid).getPriority();
      if (midPriority < priority) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }
}
