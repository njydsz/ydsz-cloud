package com.njydsz.pmis.literule.server.core;

import com.njydsz.pmis.literule.api.EffectivenessReport;
import com.njydsz.pmis.literule.api.RuleEffectivenessMetrics;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 规则效果评估服务（P2-2）
 *
 * <p>对标国内主流风控规则引擎（如蚂蚁 SOFAStack、字节巨量引擎）的效果评估能力，
 * 提供基于人工反馈标注的规则 Precision/Recall/F1 指标计算。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>反馈记录</b>：支持对规则评估结果进行 TP/FP/FN/TN 标注</li>
 *   <li><b>指标计算</b>：实时计算 Precision、Recall、F1-Score、Accuracy 等指标</li>
 *   <li><b>时间窗口</b>：支持滑动时间窗口，自动淘汰过期反馈数据</li>
 *   <li><b>聚合报告</b>：生成全局 + 单规则维度的效果评估报告</li>
 *   <li><b>批量导入</b>：支持批量录入反馈标注数据</li>
 *   <li><b>低效规则识别</b>：自动标记 F1 < 0.60 的规则为"效果较差"</li>
 * </ul>
 *
 * <h3>使用流程</h3>
 * <pre>
 * // 1. 创建服务（默认 7 天滑动窗口）
 * RuleEffectivenessService service = new RuleEffectivenessService();
 *
 * // 2. 记录反馈（规则 R001 触发了，且人工确认确实应该触发 → TP）
 * service.recordFeedback("R001", FeedbackType.TRUE_POSITIVE);
 *
 * // 3. 记录反馈（规则 R002 触发了，但人工确认不应触发 → FP，误报）
 * service.recordFeedback("R002", FeedbackType.FALSE_POSITIVE);
 *
 * // 4. 查看单规则指标
 * RuleEffectivenessMetrics metrics = service.getMetrics("R001");
 * System.out.println("Precision=" + metrics.getPrecision());
 * System.out.println("Recall=" + metrics.getRecall());
 * System.out.println("F1=" + metrics.getF1Score());
 *
 * // 5. 生成全局报告
 * EffectivenessReport report = service.generateReport();
 * System.out.println(report.getSummary());
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ConcurrentHashMap} + {@link AtomicLong} 保证并发安全，
 * 适用于多线程环境下的反馈记录和指标查询。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
public class RuleEffectivenessService {

    /** 默认滑动窗口大小（7 天） */
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    /** 最小有效样本数（低于此数标记为 INSUFFICIENT_DATA） */
    private static final int MIN_SAMPLES = 30;

    /** 低效规则 F1 阈值 */
    private static final double POOR_F1_THRESHOLD = 0.60;

    /** 优秀规则 F1 阈值 */
    private static final double TOP_F1_THRESHOLD = 0.75;

    /** 反馈记录条目（单条反馈标注） */
    private static class FeedbackEntry {
        final String ruleCode;
        final FeedbackType type;
        final long timestamp;

        FeedbackEntry(String ruleCode, FeedbackType type, long timestamp) {
            this.ruleCode = ruleCode;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    /** 按规则编码分组的反馈计数器 */
    private final ConcurrentHashMap<String, Counters> perRuleCounters = new ConcurrentHashMap<>();

    /** 全部反馈记录（用于时间窗口淘汰） */
    private final List<FeedbackEntry> allEntries = new ArrayList<>();

    /** 读写锁（保护 allEntries 的遍历与淘汰） */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** 滑动窗口大小 */
    private final Duration window;

    /** 上次淘汰时间戳 */
    private volatile long lastEvictionTime = System.currentTimeMillis();

    /** 淘汰间隔（毫秒，默认 5 分钟执行一次淘汰） */
    private static final long EVICTION_INTERVAL_MS = 5 * 60 * 1000L;

    /**
     * 单规则计数器（线程安全）
     */
    private static class Counters {
        final AtomicLong tp = new AtomicLong(0);
        final AtomicLong fp = new AtomicLong(0);
        final AtomicLong fn = new AtomicLong(0);
        final AtomicLong tn = new AtomicLong(0);

        long total() {
            return tp.get() + fp.get() + fn.get() + tn.get();
        }

        void increment(FeedbackType type) {
            switch (type) {
                case TRUE_POSITIVE -> tp.incrementAndGet();
                case FALSE_POSITIVE -> fp.incrementAndGet();
                case FALSE_NEGATIVE -> fn.incrementAndGet();
                case TRUE_NEGATIVE -> tn.incrementAndGet();
            }
        }

        void decrement(FeedbackType type) {
            switch (type) {
                case TRUE_POSITIVE -> tp.decrementAndGet();
                case FALSE_POSITIVE -> fp.decrementAndGet();
                case FALSE_NEGATIVE -> fn.decrementAndGet();
                case TRUE_NEGATIVE -> tn.decrementAndGet();
            }
        }

    }

    /**
     * 反馈类型
     */
    public enum FeedbackType {
        /** 真正例：规则触发且应该触发 */
        TRUE_POSITIVE,
        /** 假正例：规则触发但不应触发（误报） */
        FALSE_POSITIVE,
        /** 假负例：规则未触发但应该触发（漏报） */
        FALSE_NEGATIVE,
        /** 真负例：规则未触发且不应触发 */
        TRUE_NEGATIVE
    }

    /**
     * 使用默认 7 天滑动窗口创建服务
     */
    public RuleEffectivenessService() {
        this(DEFAULT_WINDOW);
    }

    /**
     * 指定滑动窗口创建服务
     *
     * @param window 滑动窗口大小（如 Duration.ofDays(30)）
     */
    public RuleEffectivenessService(Duration window) {
        this.window = window != null ? window : DEFAULT_WINDOW;
        log.info("[Effectiveness] 规则效果评估服务已初始化（window={}天）", this.window.toDays());
    }

    // ==================== 反馈记录 ====================

    /**
     * 记录单条反馈
     *
     * @param ruleCode 规则编码
     * @param type     反馈类型
     */
    public void recordFeedback(String ruleCode, FeedbackType type) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("反馈类型不能为空");
        }

        long now = System.currentTimeMillis();
        FeedbackEntry entry = new FeedbackEntry(ruleCode, type, now);

        lock.writeLock().lock();
        try {
            allEntries.add(entry);
        } finally {
            lock.writeLock().unlock();
        }

        perRuleCounters.computeIfAbsent(ruleCode, k -> new Counters()).increment(type);

        // 定期淘汰过期数据
        maybeEvict();

        log.debug("[Effectiveness] 记录反馈: rule={}, type={}", ruleCode, type);
    }

    /**
     * 批量记录反馈
     *
     * @param ruleCode 规则编码
     * @param type     反馈类型
     * @param count    数量
     */
    public void recordFeedbackBatch(String ruleCode, FeedbackType type, long count) {
        if (count <= 0) return;
        for (long i = 0; i < count; i++) {
            recordFeedback(ruleCode, type);
        }
    }

    /**
     * 批量记录多条反馈（不同规则）
     *
     * @param feedbacks 反馈列表（ruleCode → type）
     */
    public void recordFeedbacks(List<FeedbackRecord> feedbacks) {
        if (feedbacks == null || feedbacks.isEmpty()) return;
        for (FeedbackRecord fb : feedbacks) {
            recordFeedback(fb.ruleCode, fb.type);
        }
    }

    // ==================== 指标查询 ====================

    /**
     * 获取单规则效果指标
     *
     * @param ruleCode 规则编码
     * @return 效果指标；规则无反馈数据时返回空指标
     */
    public RuleEffectivenessMetrics getMetrics(String ruleCode) {
        Counters c = perRuleCounters.get(ruleCode);
        if (c == null) {
            return RuleEffectivenessMetrics.empty(ruleCode);
        }
        return buildMetrics(ruleCode, c);
    }

    /**
     * 获取全部规则的效果指标
     *
     * @return 规则编码 → 效果指标 的映射
     */
    public Map<String, RuleEffectivenessMetrics> getAllMetrics() {
        Map<String, RuleEffectivenessMetrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, Counters> entry : perRuleCounters.entrySet()) {
            result.put(entry.getKey(), buildMetrics(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * 获取全局汇总指标
     *
     * @return 全局效果指标
     */
    public RuleEffectivenessMetrics getGlobalMetrics() {
        Counters global = new Counters();
        for (Counters c : perRuleCounters.values()) {
            global.tp.addAndGet(c.tp.get());
            global.fp.addAndGet(c.fp.get());
            global.fn.addAndGet(c.fn.get());
            global.tn.addAndGet(c.tn.get());
        }
        return buildMetrics(null, global);
    }

    // ==================== 报告生成 ====================

    /**
     * 生成完整的效果评估报告
     *
     * @return 效果评估报告
     */
    public EffectivenessReport generateReport() {
        Map<String, RuleEffectivenessMetrics> perRule = getAllMetrics();
        RuleEffectivenessMetrics global = getGlobalMetrics();

        // 分类
        List<RuleEffectivenessMetrics> poorRules = new ArrayList<>();
        List<RuleEffectivenessMetrics> topRules = new ArrayList<>();
        List<String> lowDataRules = new ArrayList<>();

        for (RuleEffectivenessMetrics m : perRule.values()) {
            if (m.getTotalSamples() < MIN_SAMPLES) {
                lowDataRules.add(m.getRuleCode());
                continue;
            }
            double f1 = m.getF1Score();
            if (f1 < POOR_F1_THRESHOLD) {
                poorRules.add(m);
            } else if (f1 >= TOP_F1_THRESHOLD) {
                topRules.add(m);
            }
        }

        // 排序
        poorRules.sort(Comparator.comparingDouble(m -> m.getF1Score()));
        topRules.sort((a, b) -> Double.compare(b.getF1Score(), a.getF1Score()));

        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        return EffectivenessReport.builder()
                .generatedAt(LocalDateTime.now())
                .windowStart(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(windowStart), ZoneId.systemDefault()))
                .windowEnd(LocalDateTime.now())
                .globalMetrics(global)
                .perRuleMetrics(perRule)
                .poorRules(poorRules)
                .topRules(topRules)
                .lowDataRules(lowDataRules)
                .totalFeedbackSamples(global.getTotalSamples())
                .evaluatedRuleCount(perRule.size())
                .build();
    }

    // ==================== 管理操作 ====================

    /**
     * 清除指定规则的反馈数据
     *
     * @param ruleCode 规则编码
     */
    public void clearRule(String ruleCode) {
        perRuleCounters.remove(ruleCode);
        lock.writeLock().lock();
        try {
            allEntries.removeIf(e -> e.ruleCode.equals(ruleCode));
        } finally {
            lock.writeLock().unlock();
        }
        log.info("[Effectiveness] 清除规则 {} 的反馈数据", ruleCode);
    }

    /**
     * 清除全部反馈数据
     */
    public void clearAll() {
        perRuleCounters.clear();
        lock.writeLock().lock();
        try {
            allEntries.clear();
        } finally {
            lock.writeLock().unlock();
        }
        log.info("[Effectiveness] 清除全部反馈数据");
    }

    /**
     * 获取已收集反馈的规则数量
     *
     * @return 规则数量
     */
    public int ruleCount() {
        return perRuleCounters.size();
    }

    /**
     * 获取总反馈样本数
     *
     * @return 总样本数
     */
    public long totalFeedbackCount() {
        long total = 0;
        for (Counters c : perRuleCounters.values()) {
            total += c.total();
        }
        return total;
    }

    // ==================== 内部实现 ====================

    /**
     * 从计数器构建指标对象
     */
    private RuleEffectivenessMetrics buildMetrics(String ruleCode, Counters c) {
        return RuleEffectivenessMetrics.builder()
                .ruleCode(ruleCode)
                .truePositives(c.tp.get())
                .falsePositives(c.fp.get())
                .falseNegatives(c.fn.get())
                .trueNegatives(c.tn.get())
                .totalSamples(c.total())
                .build();
    }

    /**
     * 定期淘汰过期反馈数据
     *
     * <p>每隔 {@link #EVICTION_INTERVAL_MS} 执行一次，移除超出滑动窗口的反馈记录，
     * 并重新计算各规则计数器。
     */
    private void maybeEvict() {
        long now = System.currentTimeMillis();
        if (now - lastEvictionTime < EVICTION_INTERVAL_MS) {
            return;
        }
        lastEvictionTime = now;

        long cutoff = now - window.toMillis();
        List<FeedbackEntry> expired = new ArrayList<>();

        lock.writeLock().lock();
        try {
            // 收集过期条目
            var iter = allEntries.iterator();
            while (iter.hasNext()) {
                FeedbackEntry entry = iter.next();
                if (entry.timestamp < cutoff) {
                    expired.add(entry);
                    iter.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        if (expired.isEmpty()) {
            return;
        }

        // 回扣计数器
        for (FeedbackEntry entry : expired) {
            Counters c = perRuleCounters.get(entry.ruleCode);
            if (c != null) {
                c.decrement(entry.type);
            }
        }

        // 清理空计数器
        perRuleCounters.entrySet().removeIf(e -> e.getValue().total() <= 0);

        log.debug("[Effectiveness] 淘汰过期反馈 {} 条，剩余 {} 条",
                expired.size(), allEntries.size());
    }

    /**
     * 手动触发淘汰（主要用于测试）
     */
    public void evictNow() {
        lastEvictionTime = 0;
        maybeEvict();
    }

    /**
     * 反馈记录（批量录入用）
     */
    public static class FeedbackRecord {
        private final String ruleCode;
        private final FeedbackType type;

        public FeedbackRecord(String ruleCode, FeedbackType type) {
            this.ruleCode = ruleCode;
            this.type = type;
        }

        public String getRuleCode() {
            return ruleCode;
        }

        public FeedbackType getType() {
            return type;
        }
    }
}
