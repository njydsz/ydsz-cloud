package com.njydsz.common.sentry.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 告警规则引擎。
 *
 * <p>核心职责：
 *
 * <ol>
 *   <li>评估指标值是否命中规则（{@link #evaluate}）</li>
 *   <li>基于滑动窗口抑制告警抖动（{@link #shouldFire}）</li>
 *   <li>维护单实例（通过内部静态 {@link Holder} 复用，避免重复创建）</li>
 * </ol>
 *
 * <p><b>设计要点</b>：
 *
 * <ul>
 *   <li>滑动窗口抑制：以规则配置的 {@code cooldownSeconds} 为窗口宽度，窗口内的重复告警被静默，
 *       防止指标在阈值附近抖动导致的告警风暴</li>
 *   <li>线程安全：使用 {@link ConcurrentHashMap} 存储最近触发记录，{@link Deque} 维护时间窗口</li>
 *   <li>轻量无依赖：纯 JDK 实现，零外部依赖，可在任何模块中通过 {@link #getInstance()} 使用</li>
 * </ul>
 *
 * <h3>集成方式</h3>
 *
 * <p>业务模块（{@code AgentMetrics} / {@code MessageMetrics} 等）在埋点回调中调用本引擎判断是否需要生成告警事件：
 *
 * <pre>{@code
 * AlertRule rule = AlertRule.builder()
 *     .name("agent_llm_failure_rate")
 *     .metric("agent_llm_calls_total")
 *     .operator(AlertRule.Operator.GTE)
 *     .threshold(0.05)
 *     .level(AlertLevel.CRITICAL)
 *     .cooldownSeconds(300)
 *     .build();
 *
 * AlertRule engine = AlertRuleEngine.getInstance();
 * AlertLevel level = engine.evaluate("agent_llm_calls_total", currentFailureRate, rule);
 * if (level != null && engine.shouldFire(level)) {
 *     // 构造 AlertEvent 并通过 AlertPublisher 发布
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@Slf4j
public class AlertRuleEngine {

  /** 单例持有者（延迟初始化 + 线程安全）。 */
  private static final class Holder {
    private static final AlertRuleEngine INSTANCE = new AlertRuleEngine();
  }

  /** 默认滑动窗口大小（冷却时间秒），当规则未指定时使用。 */
  private static final long DEFAULT_COOLDOWN_SECONDS = 60L;

  /** 触发记录最大容量（防内存溢出）。 */
  private static final int MAX_FIRE_HISTORY_SIZE = 10000;

  /** 最近告警触发历史（key = 告警名称, value = 触发时间戳队列）。 */
  private final Map<String, Deque<Instant>> fireHistory = new ConcurrentHashMap<>();

  /** 私有构造（单例模式）。 */
  private AlertRuleEngine() {
    // 防止外部实例化
  }

  /**
   * 获取全局唯一的 {@link AlertRuleEngine} 实例。
   *
   * <p>基于 Bill Pugh 静态内部类实现延迟加载 + 线程安全，无需 {@code synchronized}。
   *
   * @return 全局唯一引擎实例，不会为 {@code null}
   */
  public static AlertRuleEngine getInstance() {
    return Holder.INSTANCE;
  }

  /**
   * 评估指标值是否命中告警规则。
   *
   * <p>检查 metricName 是否与规则匹配，若匹配则比较 value 与 threshold（按 operator）， 命中返回规则指定的 {@link AlertLevel}，
   * 未命中返回 {@code null}。
   *
   * @param metricName 指标名称（须与 {@link AlertRule#getMetric()} 匹配）
   * @param value 当前指标采样值
   * @param rule 告警规则
   * @return 命中返回对应 {@link AlertLevel}，未命中或 metric 不匹配返回 {@code null}
   */
  public AlertLevel evaluate(String metricName, double value, AlertRule rule) {
    if (rule == null) {
      return null;
    }
    if (! rule.getMetric().equals(metricName)) {
      return null;
    }
    if (rule.evaluate(value)) {
      log.debug("[AlertRuleEngine] 规则命中: metric={}, value={}, threshold={}, level={}",
          metricName, value, rule.getThreshold(), rule.getLevel());
      return rule.getLevel();
    }
    return null;
  }

  /**
   * 评估指标值是否命中多条规则中的任意一条。
   *
   * <p>优先返回最高级别的告警。
   *
   * @param metricName 指标名称
   * @param value 当前指标值
   * @param rules 规则列表
   * @return 命中返回最高级 {@link AlertLevel}，未命中返回 {@code null}
   */
  public AlertLevel evaluateAny(String metricName, double value, List<AlertRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return null;
    }
    AlertLevel highest = null;
    for (AlertRule rule : rules) {
      AlertLevel level = evaluate(metricName, value, rule);
      if (level != null && (highest == null || level.ordinal() > highest.ordinal())) {
        highest = level;
      }
    }
    return highest;
  }

  /**
   * 判断告警是否应当触发（滑动窗口防抖抑制）。
   *
   * <p>基于规则的 {@code name} 作为去重 key，在冷却时间窗口内已触发过的告警返回 {@code false}（抑制）；
   * 超出窗口则记录新的触发时间并返回 {@code true}。
   *
   * <p><b>线程安全</b>：使用 {@link ConcurrentHashMap} 的 compute 方法保证原子性。
   *
   * @param alert 告警级别（应为 evaluate 返回的非 null 值）
   * @return 应当触发返回 {@code true}，被抑制返回 {@code false}
   */
  public boolean shouldFire(AlertLevel alert) {
    if (alert == null) {
      return false;
    }
    return shouldFire("level_" + alert.name(), DEFAULT_COOLDOWN_SECONDS);
  }

  /**
   * 判断指定名称的告警是否应当触发（自定义冷却时间）。
   *
   * <p>适用于需要按规则级别设置不同抑制周期的场景（如 CRITICAL 5 分钟、WARN 15 分钟）。
   *
   * @param alertName 告警名称（用作去重 key）
   * @param cooldownSeconds 冷却时间（秒）
   * @return 应当触发返回 {@code true}，冷却期内返回 {@code false}
   */
  public boolean shouldFire(String alertName, long cooldownSeconds) {
    if (alertName == null || cooldownSeconds <= 0) {
      return true;
    }
    Instant now = Instant.now();
    Instant windowStart = now.minusSeconds(cooldownSeconds);

    Deque<Instant> history = fireHistory.computeIfAbsent(alertName, k -> new LinkedList<>());

    synchronized (history) {
      // 清理过期记录
      while (!history.isEmpty() && history.peekFirst().isBefore(windowStart)) {
        history.pollFirst();
      }

      // 窗口内有记录 → 抑制
      if (!history.isEmpty()) {
        log.debug("[AlertRuleEngine] 告警被抑制: name={}, cooldown={}s, lastFire={}",
            alertName, cooldownSeconds, history.peekLast());
        return false;
      }

      // 触发并记录
      ensureCapacity(history);
      history.addLast(now);
      return true;
    }
  }

  /**
   * 综合评估 + 抑制判断的一站式方法。
   *
   * <p>业务模块在埋点回调中直接调用此方法，命中规则且未被抑制时返回 true，
   * 调用方据此构造 {@link com.njydsz.common.sentry.domain.AlertEvent} 并通过 {@link com.njydsz.common.sentry.spi.AlertPublisher} 发布。
   *
   * @param metricName 指标名称
   * @param value 当前指标值
   * @param rules 规则列表
   * @return 应当触发告警返回 true
   */
  public boolean checkAndFire(String metricName, double value, List<AlertRule> rules) {
    AlertLevel level = evaluateAny(metricName, value, rules);
    if (level == null) {
      return false;
    }
    // 按规则级别确定冷却时间
    long cooldown = rules.stream()
        .filter(r -> r.getLevel() == level)
        .mapToLong(AlertRule::getCooldownSeconds)
        .max()
        .orElse(DEFAULT_COOLDOWN_SECONDS);

    return shouldFire(level.name() + "_" + metricName, cooldown);
  }

  /**
   * 获取当前各告警名称的触发计数（用于可观测性输出）。
   *
   * @return 告警名称 → 触发次数映射
   */
  public Map<String, Integer> getFireCounts() {
    Map<String, Integer> counts = new ConcurrentHashMap<>();
    fireHistory.forEach((key, deque) -> counts.put(key, deque.size()));
    return counts;
  }

  /**
   * 清空指定告警名称的触发历史（运维手动解除抑制）。
   *
   * @param alertName 告警名称
   */
  public void clearHistory(String alertName) {
    fireHistory.remove(alertName);
  }

  /** 清理所有窗口，释放内存。 */
  public void clearAll() {
    fireHistory.clear();
  }

  // ==================== 私有方法 ====================

  /**
   * 确保触发历史队列不超过最大容量。
   *
   * <p>超出时从队首移除最老记录。
   *
   * @param history 触发历史队列
   */
  private void ensureCapacity(Deque<Instant> history) {
    while (history.size() >= MAX_FIRE_HISTORY_SIZE) {
      history.pollFirst();
    }
  }
}
