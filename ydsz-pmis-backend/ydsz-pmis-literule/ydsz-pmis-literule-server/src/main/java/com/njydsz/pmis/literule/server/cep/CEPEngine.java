package com.njydsz.pmis.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
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

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator;

import lombok.extern.slf4j.Slf4j;

/**
 * CEP 引擎（P2-13）
 *
 * <p>支持时间窗口、序列、聚合、缺失四种模式。不依赖 Flink，自行实现轻量级窗口机制。
 *
 * <h3>核心数据结构</h3>
 * <ul>
 *   <li>每个 (patternId, partitionKey) 维护一个事件队列（线程安全）</li>
 *   <li>事件入队时做时间窗口裁剪（移除窗口外的旧事件）</li>
 *   <li>命中模式后调用 Listener 回调，由业务侧决定触发规则动作</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 * CEPEngine engine = new CEPEngine();
 * engine.registerPattern(pattern);
 * engine.addListener(hit -> fireRule(hit));
 * engine.feed(event);
 * </pre>
 *
 * @since 1.5.0
 */
@Slf4j
public class CEPEngine implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模式注册表 */
    private final Map<String, CEPPattern> patterns = new ConcurrentHashMap<>();

    /** 监听器列表 */
    private final List<Consumer<CEPHit>> listeners = new CopyOnWriteArrayList<>();

    /** 事件队列：patternId → partitionKey → Deque<CEPEvent> */
    private final Map<String, Map<String, ConcurrentLinkedDeque<CEPEvent>>> eventQueues = new ConcurrentHashMap<>();

    /** 表达式求值器（用于 filter 条件） */
    private final ExpressionEvaluator expressionEvaluator = new LiteExprEvaluator(true);

    /** 序列状态：patternId → partitionKey → 序列已匹配步骤 */
    private final Map<String, Map<String, SequenceState>> sequenceStates = new ConcurrentHashMap<>();

    /** 会话窗口最后事件时间戳：patternId → partitionKey → lastEventInstant */
    private final Map<String, Map<String, Instant>> sessionLastEventAt = new ConcurrentHashMap<>();

    /** 已注册模式数 */
    private final AtomicLong totalHits = new AtomicLong();

    /**
     * 注册模式
     */
    public void registerPattern(CEPPattern pattern) {
        if (pattern == null || pattern.getId() == null) {
            throw new IllegalArgumentException("pattern 和 pattern.id 不能为空");
        }
        patterns.put(pattern.getId(), pattern);
        eventQueues.computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>());
        if (pattern.getType() == CEPPattern.PatternType.SEQUENCE) {
            sequenceStates.computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>());
        }
        log.info("[CEP] 注册模式: id={}, type={}, ruleCode={}", pattern.getId(), pattern.getType(), pattern.getRuleCode());
    }

    /**
     * 注销模式
     */
    public void unregisterPattern(String patternId) {
        if (patternId == null) return;
        patterns.remove(patternId);
        eventQueues.remove(patternId);
        sequenceStates.remove(patternId);
        log.info("[CEP] 注销模式: id={}", patternId);
    }

    /**
     * 添加命中监听器
     */
    public void addListener(Consumer<CEPHit> listener) {
        if (listener != null) listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(Consumer<CEPHit> listener) {
        listeners.remove(listener);
    }

    /**
     * 投放事件
     */
    public void feed(CEPEvent event) {
        if (event == null) return;
        for (CEPPattern pattern : patterns.values()) {
            try {
                feedToPattern(pattern, event);
            } catch (Exception e) {
                log.warn("[CEP] 模式 {} 处理事件异常: {}", pattern.getId(), e.getMessage());
            }
        }
    }

    /**
     * 投放事件到指定模式
     */
    private void feedToPattern(CEPPattern pattern, CEPEvent event) {
        // 1. ABSENCE 模式特殊处理：需要接收任何类型事件
        if (pattern.getType() == CEPPattern.PatternType.ABSENCE) {
            handleAbsence(pattern, event, eventQueues
                    .computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(event.getPartitionKey(), k -> new ConcurrentLinkedDeque<>()));
            return;
        }

        // 2. 类型过滤
        if (!matchesType(pattern, event)) return;
        // 3. 表达式过滤
        if (pattern.getFilter() != null && !pattern.getFilter().isBlank()) {
            if (!evaluateFilter(pattern.getFilter(), event)) return;
        }

        // 4. 维护事件队列
        String partitionKey = event.getPartitionKey();
        ConcurrentLinkedDeque<CEPEvent> queue = eventQueues
                .computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(partitionKey, k -> new ConcurrentLinkedDeque<>());

        switch (pattern.getType()) {
            case TIME_WINDOW -> handleTimeWindow(pattern, event, queue);
            case SEQUENCE -> handleSequence(pattern, event, partitionKey);
            case AGGREGATE -> handleAggregate(pattern, event, queue);
            default -> log.warn("[CEP] 未知模式类型: {}", pattern.getType());
        }
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

    /**
     * 获取模式窗口类型（默认 TUMBLING，兼容旧版）
     */
    private CEPPattern.WindowType resolveWindowType(CEPPattern pattern) {
        return pattern.getWindowType() != null ? pattern.getWindowType() : CEPPattern.WindowType.TUMBLING;
    }

    /**
     * 时间窗口模式（2.0.0 增强窗口类型支持）
     *
     * <p>根据 {@link CEPPattern.WindowType} 分派不同的窗口语义：
     * <ul>
     *   <li>TUMBLING - 滚动窗口：固定大小不重叠，到期后清空</li>
     *   <li>SLIDING - 滑动窗口：按 slide 步长推进，窗口可重叠</li>
     *   <li>SESSION - 会话窗口：超过 sessionGap 无事件则关闭当前窗口</li>
     *   <li>COUNT - 计数窗口：事件数达到 countWindow 时触发并清空</li>
     * </ul>
     */
    private void handleTimeWindow(CEPPattern pattern, CEPEvent event,
                                  ConcurrentLinkedDeque<CEPEvent> queue) {
        CEPPattern.WindowType wt = resolveWindowType(pattern);
        switch (wt) {
            case SLIDING -> handleSlidingWindow(pattern, event, queue);
            case SESSION -> handleSessionWindow(pattern, event, queue);
            case COUNT -> handleCountWindow(pattern, event, queue);
            default -> handleTumblingWindow(pattern, event, queue);
        }
    }

    /**
     * 滚动窗口（默认）：固定大小不重叠，到期后清空
     */
    private void handleTumblingWindow(CEPPattern pattern, CEPEvent event,
                                      ConcurrentLinkedDeque<CEPEvent> queue) {
        Instant now = event.getTimestamp();
        Instant windowStart = now.minus(pattern.getWindow());
        queue.addLast(event);
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

    /**
     * 滑动窗口：按 slide 步长推进，窗口可重叠
     *
     * <p>窗口大小 = {@code window}，滑动步长 = {@code slide}（默认为 window/2）。
     * 每次新事件到来时，检查是否已达到 slide 步长，若是则触发并滑动窗口。
     */
    private void handleSlidingWindow(CEPPattern pattern, CEPEvent event,
                                     ConcurrentLinkedDeque<CEPEvent> queue) {
        Instant now = event.getTimestamp();
        Instant windowStart = now.minus(pattern.getWindow());
        queue.addLast(event);
        // 裁剪窗口外
        while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(windowStart)) {
            queue.pollFirst();
        }
        int count = queue.size();
        // 滑动窗口：每次达到阈值就触发，但不清空队列（窗口可重叠）
        if (count >= pattern.getThreshold()) {
            emitHit(pattern, new ArrayList<>(queue), count, event);
            // 按 slide 步长移除最旧事件，实现滑动
            Duration slide = pattern.getSlide() != null ? pattern.getSlide() : pattern.getWindow().dividedBy(2);
            Instant slideBoundary = now.minus(slide);
            while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(slideBoundary)) {
                queue.pollFirst();
            }
        }
    }

    /**
     * 会话窗口：由事件间隔驱动，超过 sessionGap 则关闭当前窗口
     *
     * <p>当新事件到来时，检查与上一个事件的间隔是否超过 sessionGap：
     * <ul>
     *   <li>超过：先检查旧窗口是否达到阈值，达到则触发，然后清空开启新窗口</li>
     *   <li>未超过：追加到当前窗口</li>
     * </ul>
     */
    private void handleSessionWindow(CEPPattern pattern, CEPEvent event,
                                     ConcurrentLinkedDeque<CEPEvent> queue) {
        Instant now = event.getTimestamp();
        Duration gap = pattern.getSessionGap() != null ? pattern.getSessionGap() : pattern.getWindow();

        // 检查会话超时
        Map<String, Instant> lastEventMap = sessionLastEventAt
                .computeIfAbsent(pattern.getId(), k -> new ConcurrentHashMap<>());
        String partitionKey = event.getPartitionKey();
        Instant lastEventAt = lastEventMap.get(partitionKey);

        if (lastEventAt != null && Duration.between(lastEventAt, now).compareTo(gap) > 0) {
            // 会话超时，检查旧窗口是否达到阈值
            int oldCount = queue.size();
            if (oldCount >= pattern.getThreshold()) {
                emitHit(pattern, new ArrayList<>(queue), oldCount, event);
            }
            queue.clear();
        }

        queue.addLast(event);
        lastEventMap.put(partitionKey, now);

        // 实时检查阈值（会话窗口也可在事件到来时即时触发）
        int count = queue.size();
        if (count >= pattern.getThreshold() && pattern.getWindow() != null) {
            // 对于会话窗口，阈值触发后不清空（等待会话超时才清空）
            // 但避免重复触发：仅当 queue 大小恰好等于阈值时触发
            if (count == (int) pattern.getThreshold()) {
                emitHit(pattern, new ArrayList<>(queue), count, event);
            }
        }
    }

    /**
     * 计数窗口：按事件数量计数，达到 countWindow 时触发并清空
     *
     * <p>不使用时间窗口，纯按事件数量。{@code countWindow} 为触发阈值。
     */
    private void handleCountWindow(CEPPattern pattern, CEPEvent event,
                                   ConcurrentLinkedDeque<CEPEvent> queue) {
        queue.addLast(event);
        int count = queue.size();
        int threshold = pattern.getCountWindow() > 0 ? pattern.getCountWindow() : (int) pattern.getThreshold();
        if (count >= threshold) {
            emitHit(pattern, new ArrayList<>(queue), count, event);
            queue.clear();
        }
    }

    /**
     * 序列模式
     */
    private void handleSequence(CEPPattern pattern, CEPEvent event, String partitionKey) {
        if (pattern.getSequence() == null || pattern.getSequence().isEmpty()) return;
        Map<String, SequenceState> stateMap = sequenceStates.get(pattern.getId());
        SequenceState state = stateMap.computeIfAbsent(partitionKey, k -> new SequenceState());

        // 找到当前应该匹配的步骤
        CEPPattern.SequenceStep step = findStep(pattern, state.currentStep + 1);
        if (step == null) {
            // 已完成所有步骤，从头开始
            state.reset();
            step = findStep(pattern, 1);
        }
        if (step == null) return;

        // 检查间隔
        if (state.lastMatchAt != null) {
            if (step.getMinGap() != null) {
                Duration gap = Duration.between(state.lastMatchAt, event.getTimestamp());
                if (gap.compareTo(step.getMinGap()) < 0) {
                    // 间隔过短，重置
                    state.reset();
                    step = findStep(pattern, 1);
                    if (step == null) return;
                }
            }
            if (step.getMaxGap() != null) {
                Duration gap = Duration.between(state.lastMatchAt, event.getTimestamp());
                if (gap.compareTo(step.getMaxGap()) > 0) {
                    // 间隔超长，重置
                    state.reset();
                    step = findStep(pattern, 1);
                    if (step == null) return;
                }
            }
        }

        // 类型匹配
        if (!step.getEventType().equals(event.getType())) return;

        // 该步过滤
        if (step.getFilter() != null && !step.getFilter().isBlank()) {
            if (!evaluateFilter(step.getFilter(), event)) return;
        }

        // 匹配成功
        state.matchedEvents.add(event);
        state.currentStep = step.getOrder();
        state.lastMatchAt = event.getTimestamp();

        // 检查是否完成所有步骤
        if (state.currentStep >= pattern.getSequence().size()) {
            emitHit(pattern, new ArrayList<>(state.matchedEvents), 0, event);
            state.reset();
        }
    }

    /**
     * 聚合模式（2.0.0 增强窗口类型支持）
     *
     * <p>聚合模式同样支持 TUMBLING/SLIDING/SESSION/COUNT 四种窗口类型。
     * 当 windowType 为 null 时默认使用 TUMBLING。
     */
    private void handleAggregate(CEPPattern pattern, CEPEvent event,
                                 ConcurrentLinkedDeque<CEPEvent> queue) {
        CEPPattern.WindowType wt = resolveWindowType(pattern);
        switch (wt) {
            case COUNT -> {
                queue.addLast(event);
                int count = queue.size();
                int threshold = pattern.getCountWindow() > 0 ? pattern.getCountWindow() : (int) pattern.getThreshold();
                if (count >= threshold) {
                    double metric = aggregate(queue, pattern.getAggregateFunction(), pattern.getAggregateField());
                    emitHit(pattern, new ArrayList<>(queue), metric, event);
                    queue.clear();
                }
            }
            default -> {
                // TUMBLING / SLIDING / SESSION 均使用时间裁剪
                Instant now = event.getTimestamp();
                Instant windowStart = now.minus(pattern.getWindow());
                queue.addLast(event);
                while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(windowStart)) {
                    queue.pollFirst();
                }
                double metric = aggregate(queue, pattern.getAggregateFunction(), pattern.getAggregateField());
                if (metric >= pattern.getThreshold()) {
                    emitHit(pattern, new ArrayList<>(queue), metric, event);
                    if (wt == CEPPattern.WindowType.TUMBLING) {
                        queue.clear();
                    }
                }
            }
        }
    }

    /**
     * 缺失模式：投放的不是该类型事件时，检查"期待"的事件是否超时
     */
    private void handleAbsence(CEPPattern pattern, CEPEvent event,
                               ConcurrentLinkedDeque<CEPEvent> queue) {
        // 简化：投放的若不是期待的 eventType，则视为缺失
        if (pattern.getEventType() != null && pattern.getEventType().equals(event.getType())) {
            // 期待的事件已出现，清空队列
            queue.clear();
            return;
        }
        queue.addLast(event);
        // 清理窗口外
        Instant windowStart = event.getTimestamp().minus(pattern.getWindow());
        while (!queue.isEmpty() && queue.peekFirst().getTimestamp().isBefore(windowStart)) {
            queue.pollFirst();
        }
        // 若窗口内一直未出现期待事件，触发告警
        if (queue.size() >= pattern.getThreshold()) {
            emitHit(pattern, new ArrayList<>(queue), queue.size(), event);
        }
    }

    /**
     * 触发命中
     */
    private void emitHit(CEPPattern pattern, List<CEPEvent> events, double metric, CEPEvent trigger) {
        CEPHit hit = CEPHit.builder()
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
        log.info("[CEP] 命中模式: id={}, ruleCode={}, metric={}, events={}",
                pattern.getId(), pattern.getRuleCode(), metric, events.size());
    }

    /**
     * 评估过滤器
     */
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
            RuleContext ruleContext =
                    RuleContext.of(ctx);
            return expressionEvaluator.evalBoolean(filter, ruleContext);
        } catch (Exception e) {
            log.debug("[CEP] 过滤器评估失败: filter={}, error={}", filter, e.getMessage());
            return false;
        }
    }

    /**
     * 计算聚合
     */
    private double aggregate(ConcurrentLinkedDeque<CEPEvent> queue,
                             CEPPattern.AggregateFunction func, String field) {
        if (queue.isEmpty() || func == null) return 0;
        if (func == CEPPattern.AggregateFunction.COUNT) return queue.size();
        double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int count = 0;
        for (CEPEvent e : queue) {
            double v = field != null ? e.attrDouble(field) : 0;
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
            count++;
        }
        return switch (func) {
            case SUM -> sum;
            case AVG -> count > 0 ? sum / count : 0;
            case MIN -> min == Double.MAX_VALUE ? 0 : min;
            case MAX -> max == -Double.MAX_VALUE ? 0 : max;
            default -> 0;
        };
    }

    /**
     * 查找序列步骤
     */
    private CEPPattern.SequenceStep findStep(CEPPattern pattern, int order) {
        return pattern.getSequence().stream()
                .filter(s -> s.getOrder() == order)
                .findFirst()
                .orElse(null);
    }

    /**
     * 序列状态
     */
    private static class SequenceState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        int currentStep = 0;
        Instant lastMatchAt;
        final List<CEPEvent> matchedEvents = new ArrayList<>();

        void reset() {
            currentStep = 0;
            lastMatchAt = null;
            matchedEvents.clear();
        }
    }

    /**
     * 获取已注册模式数量
     */
    public int patternCount() {
        return patterns.size();
    }

    /**
     * 获取所有命中次数（自启动以来）
     */
    public long totalHits() {
        return totalHits.get();
    }

    /**
     * 清理指定分区的状态
     */
    public void clearPartition(String patternId, String partitionKey) {
        if (patternId == null || partitionKey == null) return;
        Map<String, ConcurrentLinkedDeque<CEPEvent>> qMap = eventQueues.get(patternId);
        if (qMap != null) qMap.remove(partitionKey);
        Map<String, SequenceState> sMap = sequenceStates.get(patternId);
        if (sMap != null) sMap.remove(partitionKey);
        Map<String, Instant> sessionMap = sessionLastEventAt.get(patternId);
        if (sessionMap != null) sessionMap.remove(partitionKey);
    }

    /**
     * 清理所有状态
     */
    public void clearAll() {
        eventQueues.clear();
        sequenceStates.clear();
        sessionLastEventAt.clear();
    }

    /**
     * 列出已注册模式
     */
    public List<CEPPattern> listPatterns() {
        return Collections.unmodifiableList(new ArrayList<>(patterns.values()));
    }
}
