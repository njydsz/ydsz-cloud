package com.njydsz.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;

/**
 * CEP 引擎（精简版）
 *
 * <p>支持滚动窗口计数模式：当窗口内匹配的事件数达到阈值时触发。不依赖 Flink，自行实现轻量级窗口机制。
 *
 * <h3>核心数据结构</h3>
 *
 * <ul>
 *   <li>每个 (patternId, partitionKey) 维护一个事件队列（线程安全）
 *   <li>事件入队时做时间窗口裁剪（移除窗口外的旧事件）
 *   <li>命中模式后调用 Listener 回调，由业务侧决定触发规则动作
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * CEPEngine engine = new CEPEngine(expressionEvaluator);
 * engine.registerPattern(pattern);
 * engine.addListener(hit -> fireRule(hit));
 * engine.feed(event);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class CEPEngine implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模式注册表 */
  private final Map<String, CEPPattern> patterns = new ConcurrentHashMap<>();

  /** 监听器列表 */
  private final List<Consumer<CEPHit>> listeners = new CopyOnWriteArrayList<>();

  /** 事件队列：patternId → partitionKey → Deque<CEPEvent> */
  private final Map<String, Map<String, ConcurrentLinkedDeque<CEPEvent>>> eventQueues =
      new ConcurrentHashMap<>();

  /** 表达式求值器（用于 filter 条件，通过构造器注入） */
  private final ExpressionEngine expressionEvaluator;

  /** 单分区事件队列上限，超过时丢弃最旧事件 */
  private static final int MAX_EVENTS_PER_PARTITION = 10_000;

  /** 已注册模式数 */
  private final AtomicLong totalHits = new AtomicLong();

  /** 默认表达式求值器（无参构造时使用，向后兼容） */
  private static final ExpressionEngine DEFAULT_EVALUATOR = createDefaultEvaluator();

  /** 默认构造（向后兼容，内部创建默认求值器） */
  public CEPEngine() {
    this(DEFAULT_EVALUATOR);
  }

  /**
   * 构造 CEP 引擎
   *
   * <p>推荐使用此构造器，使 CEP 的表达式求值器与引擎主求值器配置一致（沙箱开关、自定义函数注册等），避免独立 new 实例导致的配置不一致。
   *
   * @param expressionEvaluator 表达式求值器
   * @since 1.0.0
   */
  public CEPEngine(ExpressionEngine expressionEvaluator) {
    this.expressionEvaluator =
        expressionEvaluator != null ? expressionEvaluator : DEFAULT_EVALUATOR;
  }

  private static ExpressionEngine createDefaultEvaluator() {
    try {
      Class<?> clazz =
          Class.forName("com.njydsz.literule.server.engine.liteexpr.LiteExprEngine");
      return (ExpressionEngine) clazz.getConstructor(boolean.class).newInstance(true);
    } catch (Exception e) {
      throw new IllegalStateException("无法创建默认 LiteExprEngine", e);
    }
  }

  /** 注册模式
   * @param pattern 参数说明
   */
  public void registerPattern(CEPPattern pattern) {
    if (pattern == null || pattern.getId() == null) {
      throw new IllegalArgumentException("pattern 和 pattern.id 不能为空");
    }
    patterns.put(pattern.getId(), pattern);
    eventQueues.computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>());
    log.info("[CEP] 注册模式: id={}, ruleCode={}", pattern.getId(), pattern.getRuleCode());
  }

  /** 注销模式
   * @param patternId 参数说明
   */
  public void unregisterPattern(String patternId) {
    if (patternId == null) {
      return;
    }
    patterns.remove(patternId);
    eventQueues.remove(patternId);
    log.info("[CEP] 注销模式: id={}", patternId);
  }

  /** 添加命中监听器
   * @param listener 参数说明
   */
  public void addListener(Consumer<CEPHit> listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /** 移除监听器
   * @param listener 参数说明
   */
  public void removeListener(Consumer<CEPHit> listener) {
    listeners.remove(listener);
  }

  /** 投递事件
   * @param event 参数说明
   */
  public void feed(CEPEvent event) {
    if (event == null) {
      return;
    }
    for (CEPPattern pattern : patterns.values()) {
      try {
        feedToPattern(pattern, event);
      } catch (Exception e) {
        log.warn("[CEP] 模式 {} 处理事件异常: {}", pattern.getId(), e.getMessage());
      }
    }
  }

  /** 投递事件到指定模式 */
  private void feedToPattern(CEPPattern pattern, CEPEvent event) {
    // 类型过滤
    if (!matchesType(pattern, event)) {
      return;
    }
    // 表达式过滤
    if (pattern.getFilter() != null && !pattern.getFilter().isBlank()) {
      if (!evaluateFilter(pattern.getFilter(), event)) {
        return;
      }
    }

    // 维护事件队列
    String partitionKey = event.getPartitionKey();
    ConcurrentLinkedDeque<CEPEvent> queue =
        eventQueues
            .computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(partitionKey, k -> new ConcurrentLinkedDeque<>());

    handleTumblingWindow(pattern, event, queue);
  }

  private boolean matchesType(CEPPattern pattern, CEPEvent event) {
    if (pattern.getEventType() != null) {
      return pattern.getEventType().equals(event.getType());
    }
    if (pattern.getEventTypes() != null && !pattern.getEventTypes().isEmpty()) {
      return pattern.getEventTypes().contains(event.getType());
    }
    return true;
  }

  /** 滚动窗口：固定大小不重叠，到期后清空 */
  private void handleTumblingWindow(
      CEPPattern pattern, CEPEvent event, ConcurrentLinkedDeque<CEPEvent> queue) {
    Instant now = event.getTimestamp();
    Instant windowStart = now.minus(pattern.getWindow());
    queue.addLast(event);
    enforceQueueLimit(queue);
    // 裁剪窗口外
    while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(windowStart)) {
      queue.pollFirst();
    }
    int count = queue.size();
    if (count >= pattern.getThreshold()) {
      emitHit(pattern, new ArrayList<>(queue), count, event);
      // 滚动窗口命中后清空，开启下一个窗口
      queue.clear();
    }
  }

  /** 触发命中 */
  private void emitHit(CEPPattern pattern, List<CEPEvent> events, double metric, CEPEvent trigger) {
    CEPHit hit =
        CEPHit.builder()
            .patternId(pattern.getId())
            .ruleCode(pattern.getRuleCode())
            .matchedEvents(events)
            .hitAt(Instant.now())
            .metric(metric)
            .context(new HashMap<>())
            .build();
    if (trigger != null) {
      hit.getContext().put("partitionKey", trigger.getPartitionKey());
      hit.getContext().put("triggerType", trigger.getType());
    }
    totalHits.incrementAndGet();
    for (Consumer<CEPHit> l : listeners) {
      try {
        l.accept(hit);
      } catch (Exception e) {
        log.warn("[CEP] listener 异常: {}", e.getMessage());
      }
    }
    log.info(
        "[CEP] 命中模式: id={}, ruleCode={}, metric={}, events={}",
        pattern.getId(),
        pattern.getRuleCode(),
        metric,
        events.size());
  }

  /** 评估过滤器 */
  private boolean evaluateFilter(String filter, CEPEvent event) {
    try {
      // 包装事件到 context：$event
      Map<String, Object> ctx = new HashMap<>();
      ctx.put("event", event);
      ctx.put("type", event.getType());
      ctx.put("partitionKey", event.getPartitionKey());
      if (event.getAttributes() != null) {
        ctx.putAll(event.getAttributes());
      }
      RuleContextVO ruleContext = RuleContextVO.of(ctx);
      return expressionEvaluator.evalBoolean(filter, ruleContext);
    } catch (Exception e) {
      log.debug("[CEP] 过滤器评估失败: filter={}, error={}", filter, e.getMessage());
      return false;
    }
  }

  /** 获取已注册模式数量
   * @return 返回值说明
   */
  public int patternCount() {
    return patterns.size();
  }

  /** 获取所有命中次数（自启动以来）
   * @return 返回值说明
   */
  public long totalHits() {
    return totalHits.get();
  }

  /** 清理指定分区的状态
   * @param patternId 参数说明
   * @param partitionKey 参数说明
   */
  public void clearPartition(String patternId, String partitionKey) {
    if (patternId == null || partitionKey == null) {
      return;
    }
    Map<String, ConcurrentLinkedDeque<CEPEvent>> qMap = eventQueues.get(patternId);
    if (qMap != null) {
      qMap.remove(partitionKey);
    }
  }

  /** 清理所有状态 */
  public void clearAll() {
    eventQueues.clear();
  }

  /**
   * 定期清理过期事件队列
   *
   * <p>遍历所有模式的事件队列，移除窗口外的过期事件，防止长时间运行时队列无限增长。建议由 @Scheduled 定时调用（如每 60 秒）。
   *
   * @since 1.0.0
   */
  public void cleanupExpiredEvents() {
    Instant now = Instant.now();
    for (Map.Entry<String, CEPPattern> entry : patterns.entrySet()) {
      CEPPattern pattern = entry.getValue();
      if (pattern.getWindow() == null) {
        continue;
      }
      Map<String, ConcurrentLinkedDeque<CEPEvent>> qMap = eventQueues.get(entry.getKey());
      if (qMap == null) {
        continue;
      }
      Instant windowStart = now.minus(pattern.getWindow());
      for (ConcurrentLinkedDeque<CEPEvent> queue : qMap.values()) {
        while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(windowStart)) {
          queue.pollFirst();
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("[CEP] 过期事件清理完成");
    }
  }

  /**
   * 优雅关闭：清理所有队列和状态
   *
   * @since 1.0.0
   */
  @PreDestroy
  public void destroy() {
    clearAll();
    log.info("[CEP] 引擎已关闭，所有队列和状态已清理");
  }

  /**
   * 强制队列上限保护
   *
   * <p>当队列大小超过 MAX_EVENTS_PER_PARTITION 时，丢弃最旧的事件并记录告警。防止高吞吐场景下队列无限增长导致 OOM。
   *
   * @param queue 事件队列
   * @since 1.0.0
   */
  private void enforceQueueLimit(ConcurrentLinkedDeque<CEPEvent> queue) {
    while (queue.size() > MAX_EVENTS_PER_PARTITION) {
      CEPEvent dropped = queue.pollFirst();
      if (dropped == null) {
        break;
      }
    }
  }

  /** 列出已注册模式
   * @return 返回值说明
   */
  public List<CEPPattern> listPatterns() {
    return Collections.unmodifiableList(new ArrayList<>(patterns.values()));
  }
}
