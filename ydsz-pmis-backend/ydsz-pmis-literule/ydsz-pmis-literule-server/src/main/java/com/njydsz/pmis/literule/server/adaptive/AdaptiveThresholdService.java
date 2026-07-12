package com.njydsz.pmis.literule.server.adaptive;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.server.config.RuleAdminService;
import com.njydsz.pmis.literule.server.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.server.spi.TraceDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * 自适应阈值分析服务（P3-4 自适应智能风控）
 *
 * <p>对标字节巨量引擎"规则 2.0"的自适应阈值能力，基于历史触发数据自动调整规则阈值：
 * <ol>
 *   <li>从 {@link TraceDataProvider} 获取规则最近 N 天的执行轨迹</li>
 *   <li>从 {@link RuleExecutionTrace#getFactsSnapshot()} 中提取条件表达式变量的实际值</li>
 *   <li>计算数据分布统计（均值、中位数、分位数、标准差）</li>
 *   <li>根据策略计算建议阈值：
 *     <ul>
 *       <li>PERCENTILE：取 P95 作为新阈值</li>
 *       <li>FALSE_RATE：触发率过高（&gt;50%）时提高阈值到 P75</li>
 *       <li>MISS_RATE：触发率过低（&lt;5%）时降低阈值到 P90</li>
 *       <li>BALANCED：使用 F1-score 最优阈值</li>
 *     </ul>
 *   </li>
 *   <li>计算置信度（样本量越大、分布越集中，置信度越高）</li>
 *   <li>LLM 可用时生成自然语言调整原因，否则降级为模板生成</li>
 * </ol>
 *
 * <p>所有方法均做了空值与异常隔离，TraceDataProvider 不可用时返回空列表。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public class AdaptiveThresholdService {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveThresholdService.class);

    /** 高触发率阈值（超过此值视为误报过多） */
    private static final double HIGH_TRIGGER_RATE = 0.5;

    /** 低触发率阈值（低于此值视为漏报过多） */
    private static final double LOW_TRIGGER_RATE = 0.05;

    /** 最小样本量（低于此值不生成建议） */
    private static final int MIN_SAMPLE_SIZE = 10;

    /** 高置信度样本量阈值 */
    private static final int HIGH_CONFIDENCE_SAMPLE_SIZE = 200;

    /** 自动应用置信度阈值（2.0.0） */
    private static final double AUTO_APPLY_CONFIDENCE_THRESHOLD = 0.75;

    /** LLM 调整原因系统提示词 */
    private static final String LLM_REASON_SYSTEM_PROMPT = "你是规则引擎风控专家。"
            + "请基于给定的规则阈值分析数据，用 1~2 句中文解释为什么要调整阈值，"
            + "语气专业简洁，不要带任何前后缀。";

    /** 规则配置提供者 */
    private final RuleConfigProvider configProvider;

    /** 轨迹数据提供者（SPI，由消费方提供） */
    private final TraceDataProvider traceDataProvider;

    /** 规则管理服务（用于应用阈值调整） */
    private final RuleAdminService ruleAdminService;

    /** LLM 客户端（可选，用于生成调整原因） */
    private final LLMClient llmClient;

    /** 待处理建议缓存（ruleCode → 建议列表），仅内存缓存，重启后需重新分析 */
    private final Map<String, List<ThresholdAnalysis>> pendingSuggestions = new ConcurrentHashMap<>();

    /** 已应用阈值的效果追踪记录（2.0.0）：ruleCode → 效果报告 */
    private final Map<String, ThresholdEffectReport> effectReports = new ConcurrentHashMap<>();

    /** 是否启用自动应用（2.0.0） */
    private volatile boolean autoApplyEnabled = false;

    /** 自动应用置信度阈值（2.0.0，可配置覆盖默认值） */
    private volatile double autoApplyConfidenceThreshold = AUTO_APPLY_CONFIDENCE_THRESHOLD;

    /**
     * 构造自适应阈值分析服务
     *
     * @param configProvider    规则配置提供者
     * @param traceDataProvider 轨迹数据提供者
     * @param ruleAdminService  规则管理服务（可为 null，仅 applyThreshold 不可用）
     * @param llmClient         LLM 客户端（可为 null，降级为模板生成原因）
     */
    public AdaptiveThresholdService(RuleConfigProvider configProvider,
                                     TraceDataProvider traceDataProvider,
                                     RuleAdminService ruleAdminService,
                                     LLMClient llmClient) {
        this.configProvider = configProvider;
        this.traceDataProvider = traceDataProvider;
        this.ruleAdminService = ruleAdminService;
        this.llmClient = llmClient;
    }

    /**
     * 分析指定规则的阈值
     *
     * @param ruleCode 规则编码
     * @param days     分析最近 N 天的数据
     * @return 阈值分析结果列表（一条规则可能含多个阈值比较项）；无数据时返回空列表
     */
    public List<ThresholdAnalysis> analyzeRule(String ruleCode, int days) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return List.of();
        }
        if (traceDataProvider == null || !traceDataProvider.isAvailable()) {
            log.debug("[AdaptiveThreshold] TraceDataProvider 不可用，跳过分析: ruleCode={}", ruleCode);
            return List.of();
        }

        RuleDefinition rule = configProvider.findByCode(ruleCode);
        if (rule == null) {
            log.debug("[AdaptiveThreshold] 规则不存在: ruleCode={}", ruleCode);
            return List.of();
        }

        List<ThresholdExtractor.ThresholdInfo> thresholds =
                ThresholdExtractor.extract(rule.getConditionExpression());
        if (thresholds.isEmpty()) {
            log.debug("[AdaptiveThreshold] 表达式无可识别的阈值比较: ruleCode={}, expr={}",
                    ruleCode, rule.getConditionExpression());
            return List.of();
        }

        List<RuleExecutionTrace> traces;
        try {
            traces = traceDataProvider.getTracesByRule(ruleCode, days);
        } catch (Exception e) {
            log.warn("[AdaptiveThreshold] 获取轨迹数据失败: ruleCode={}, err={}", ruleCode, e.getMessage());
            return List.of();
        }
        if (traces == null || traces.isEmpty()) {
            log.debug("[AdaptiveThreshold] 无轨迹数据: ruleCode={}", ruleCode);
            return List.of();
        }

        List<ThresholdAnalysis> analyses = new ArrayList<>();
        for (ThresholdExtractor.ThresholdInfo ti : thresholds) {
            ThresholdAnalysis analysis = analyzeOneThreshold(ruleCode, rule, ti, traces);
            if (analysis != null) {
                analyses.add(analysis);
            }
        }

        // 缓存建议
        if (!analyses.isEmpty()) {
            pendingSuggestions.put(ruleCode, new CopyOnWriteArrayList<>(analyses));
        }

        return analyses;
    }

    /**
     * 分析所有规则的阈值
     *
     * @param days 分析最近 N 天的数据
     * @return 全部规则的分析结果列表
     */
    public List<ThresholdAnalysis> analyzeAllRules(int days) {
        List<RuleDefinition> rules;
        try {
            rules = configProvider.loadAllRules();
        } catch (Exception e) {
            log.warn("[AdaptiveThreshold] 加载全部规则失败: {}", e.getMessage());
            return List.of();
        }
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        List<ThresholdAnalysis> all = new ArrayList<>();
        for (RuleDefinition rule : rules) {
            try {
                List<ThresholdAnalysis> one = analyzeRule(rule.getCode(), days);
                if (one != null && !one.isEmpty()) {
                    all.addAll(one);
                }
            } catch (Exception e) {
                log.warn("[AdaptiveThreshold] 分析规则失败: ruleCode={}, err={}",
                        rule.getCode(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 应用阈值调整
     *
     * <p>将建议阈值写入规则的条件表达式，通过 {@link RuleAdminService#save} 持久化。
     * 应用后从待处理建议列表中移除。
     *
     * @param ruleCode 规则编码
     * @param analysis 阈值分析结果
     * @param operator 操作人
     * @return 更新后的规则定义；应用失败时返回 null
     */
    public boolean applyThreshold(String ruleCode, ThresholdAnalysis analysis, String operator) {
        if (ruleCode == null || ruleCode.isBlank() || analysis == null) {
            return false;
        }
        if (ruleAdminService == null) {
            log.warn("[AdaptiveThreshold] RuleAdminService 未注入，无法应用阈值调整");
            return false;
        }

        RuleDefinition rule = configProvider.findByCode(ruleCode);
        if (rule == null) {
            log.warn("[AdaptiveThreshold] 规则不存在，无法应用阈值: ruleCode={}", ruleCode);
            return false;
        }

        String oldExpr = rule.getConditionExpression();
        String newExpr = replaceThresholdInExpression(oldExpr, analysis.getVariable(),
                analysis.getOperator(), analysis.getCurrentThreshold(),
                analysis.getSuggestedThreshold());
        if (newExpr == null || newExpr.equals(oldExpr)) {
            log.warn("[AdaptiveThreshold] 表达式中未找到匹配的阈值，无法替换: ruleCode={}, expr={}",
                    ruleCode, oldExpr);
            return false;
        }

        rule.setConditionExpression(newExpr);
        String changeDesc = String.format("[自适应阈值调整] %s %s %.4f → %.4f (策略=%s, 置信度=%.2f)",
                analysis.getVariable(), analysis.getOperator(),
                analysis.getCurrentThreshold(), analysis.getSuggestedThreshold(),
                analysis.getStrategy(), analysis.getConfidence());
        try {
            ruleAdminService.save(rule, operator, changeDesc);
            analysis.setApplied(true);
            // 从待处理列表中移除
            List<ThresholdAnalysis> pending = pendingSuggestions.get(ruleCode);
            if (pending != null) {
                pending.removeIf(a -> a.getVariable().equals(analysis.getVariable())
                        && a.getOperator().equals(analysis.getOperator()));
            }
            log.info("[AdaptiveThreshold] 阈值已应用: ruleCode={}, variable={}, {} {} → {}",
                    ruleCode, analysis.getVariable(), analysis.getOperator(),
                    analysis.getCurrentThreshold(), analysis.getSuggestedThreshold());
            return true;
        } catch (Exception e) {
            log.warn("[AdaptiveThreshold] 应用阈值调整失败: ruleCode={}, err={}", ruleCode, e.getMessage());
            return false;
        }
    }

    /**
     * 获取待处理的建议列表
     *
     * @param ruleCode 规则编码
     * @return 待处理建议列表；无缓存时返回空列表
     */
    public List<ThresholdAnalysis> getPendingSuggestions(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return List.of();
        }
        List<ThresholdAnalysis> list = pendingSuggestions.get(ruleCode);
        if (list == null) {
            return List.of();
        }
        // 过滤已应用的
        return list.stream().filter(a -> !a.isApplied()).toList();
    }

    // ==================== 自适应阈值闭环 (2.0.0) ====================

    /**
     * 设置是否启用自动应用
     *
     * @param autoApplyEnabled 是否启用
     * @since 2.0.0
     */
    public void setAutoApplyEnabled(boolean autoApplyEnabled) {
        this.autoApplyEnabled = autoApplyEnabled;
        log.info("[AdaptiveThreshold] 自动应用已{}", autoApplyEnabled ? "启用" : "禁用");
    }

    /**
     * 设置自动应用置信度阈值
     *
     * @param threshold 置信度阈值（0~1）
     * @since 2.0.0
     */
    public void setAutoApplyConfidenceThreshold(double threshold) {
        this.autoApplyConfidenceThreshold = Math.max(0, Math.min(1, threshold));
    }

    /**
     * 定时分析任务（2.0.0 自适应阈值闭环入口）
     *
     * <p>此方法设计为被 Spring @Scheduled 或 XXL-Job 定时调度调用。
     * 执行流程：
     * <ol>
     *   <li>分析全部规则最近 N 天的阈值</li>
     *   <li>若启用自动应用，对高置信度建议自动应用</li>
     *   <li>对已应用的阈值进行效果追踪</li>
     * </ol>
     *
     * @param analysisDays 分析天数
     * @param operator     操作人标识
     * @return 分析结果摘要
     * @since 2.0.0
     */
    public ScheduledAnalysisResult scheduledAnalyze(int analysisDays, String operator) {
        log.info("[AdaptiveThreshold] 定时分析任务启动: days={}, autoApply={}, operator={}",
                analysisDays, autoApplyEnabled, operator);

        // 1. 分析全部规则
        List<ThresholdAnalysis> allAnalyses = analyzeAllRules(analysisDays);

        int totalRules = (int) allAnalyses.stream().map(ThresholdAnalysis::getRuleCode).distinct().count();
        int totalSuggestions = allAnalyses.size();
        int autoApplied = 0;
        int autoApplyFailed = 0;

        // 2. 自动应用高置信度建议
        if (autoApplyEnabled) {
            for (ThresholdAnalysis analysis : allAnalyses) {
                if (!analysis.isApplied()
                        && analysis.getConfidence() >= autoApplyConfidenceThreshold) {
                    try {
                        boolean success = applyThreshold(analysis.getRuleCode(), analysis, operator);
                        if (success) {
                            autoApplied++;
                            // 记录效果追踪基线
                            recordEffectBaseline(analysis);
                        } else {
                            autoApplyFailed++;
                        }
                    } catch (Exception e) {
                        autoApplyFailed++;
                        log.warn("[AdaptiveThreshold] 自动应用失败: ruleCode={}, variable={}, err={}",
                                analysis.getRuleCode(), analysis.getVariable(), e.getMessage());
                    }
                }
            }
        }

        // 3. 效果追踪（检查已应用的阈值）
        int effectChecked = trackEffects(analysisDays, operator);

        ScheduledAnalysisResult result = new ScheduledAnalysisResult(
                totalRules, totalSuggestions, autoApplied, autoApplyFailed, effectChecked);
        log.info("[AdaptiveThreshold] 定时分析任务完成: {}", result);
        return result;
    }

    /**
     * 记录阈值应用效果基线
     */
    private void recordEffectBaseline(ThresholdAnalysis analysis) {
        ThresholdEffectReport report = ThresholdEffectReport.builder()
                .ruleCode(analysis.getRuleCode())
                .variable(analysis.getVariable())
                .oldThreshold(analysis.getCurrentThreshold())
                .newThreshold(analysis.getSuggestedThreshold())
                .appliedAt(LocalDateTime.now().toString())
                .baselineTriggerRate(analysis.getDistribution() != null
                        ? analysis.getDistribution().getTriggerRate() : 0)
                .baselineSampleSize(analysis.getDistribution() != null
                        ? analysis.getDistribution().getTotalCount() : 0)
                .build();
        effectReports.put(analysis.getRuleCode() + ":" + analysis.getVariable(), report);
    }

    /**
     * 追踪已应用阈值的效果
     *
     * <p>对每个已应用的阈值，重新分析最近 N 天的数据，
     * 比较应用前后的触发率变化，生成效果报告。
     *
     * @param days 观察天数
     * @param operator 操作人
     * @return 已追踪效果的数量
     */
    private int trackEffects(int days, String operator) {
        if (effectReports.isEmpty()) {
            return 0;
        }
        int tracked = 0;
        for (Map.Entry<String, ThresholdEffectReport> entry : effectReports.entrySet()) {
            ThresholdEffectReport report = entry.getValue();
            try {
                // 重新分析当前效果
                List<ThresholdAnalysis> currentAnalyses = analyzeRule(report.getRuleCode(), days);
                for (ThresholdAnalysis current : currentAnalyses) {
                    if (current.getVariable().equals(report.getVariable())) {
                        report.setCurrentTriggerRate(current.getDistribution() != null
                                ? current.getDistribution().getTriggerRate() : 0);
                        report.setCurrentSampleSize(current.getDistribution() != null
                                ? current.getDistribution().getTotalCount() : 0);
                        report.setEffectEvaluatedAt(LocalDateTime.now().toString());
                        // 计算效果：触发率变化
                        double rateDelta = report.getCurrentTriggerRate() - report.getBaselineTriggerRate();
                        report.setTriggerRateDelta(rateDelta);
                        if (Math.abs(rateDelta) < 0.02) {
                            report.setEffectLevel("NEUTRAL");
                        } else if (rateDelta < 0 && report.getBaselineTriggerRate() > HIGH_TRIGGER_RATE) {
                            report.setEffectLevel("POSITIVE");
                        } else if (rateDelta > 0 && report.getBaselineTriggerRate() < LOW_TRIGGER_RATE) {
                            report.setEffectLevel("POSITIVE");
                        } else {
                            report.setEffectLevel("NEEDS_REVIEW");
                        }
                        tracked++;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[AdaptiveThreshold] 效果追踪失败: ruleCode={}, err={}",
                        report.getRuleCode(), e.getMessage());
            }
        }
        return tracked;
    }

    /**
     * 获取阈值应用效果报告
     *
     * @param ruleCode 规则编码
     * @return 效果报告列表；无记录时返回空列表
     * @since 2.0.0
     */
    public List<ThresholdEffectReport> getEffectReports(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return List.of();
        }
        return effectReports.entrySet().stream()
                .filter(e -> e.getKey().startsWith(ruleCode + ":"))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 获取全部效果报告
     *
     * @return 全部效果报告列表
     * @since 2.0.0
     */
    public List<ThresholdEffectReport> getAllEffectReports() {
        return List.copyOf(effectReports.values());
    }

    // ==================== 内部分析逻辑 ====================

    /**
     * 分析单个阈值比较项
     */
    private ThresholdAnalysis analyzeOneThreshold(String ruleCode, RuleDefinition rule,
                                                   ThresholdExtractor.ThresholdInfo ti,
                                                   List<RuleExecutionTrace> traces) {
        // 1. 提取变量值
        List<Double> values = extractVariableValues(traces, ti.getVariable());
        if (values.size() < MIN_SAMPLE_SIZE) {
            log.debug("[AdaptiveThreshold] 样本量不足（{} < {}）: ruleCode={}, variable={}",
                    values.size(), MIN_SAMPLE_SIZE, ruleCode, ti.getVariable());
            return null;
        }

        // 2. 计算分布统计
        DistributionStats stats = calculateDistribution(values, ti, traces);

        // 3. 确定策略
        ThresholdStrategy strategy = determineStrategy(stats);

        // 4. 计算建议阈值
        double suggested = calculateSuggestedThreshold(strategy, ti, stats, values);

        // 5. 计算置信度
        double confidence = calculateConfidence(values.size(), stats);

        // 6. 生成调整原因
        String reason = generateReason(rule, ti, stats, suggested, strategy);

        return ThresholdAnalysis.builder()
                .ruleCode(ruleCode)
                .variable(ti.getVariable())
                .operator(ti.getOperator())
                .currentThreshold(ti.getThreshold())
                .suggestedThreshold(suggested)
                .confidence(confidence)
                .reason(reason)
                .strategy(strategy)
                .distribution(stats)
                .suggestedAt(LocalDateTime.now().toString())
                .build();
    }

    /**
     * 从轨迹列表中提取指定变量的数值
     *
     * @param traces   轨迹列表
     * @param variable 变量名
     * @return 数值列表（升序排序）
     */
    private List<Double> extractVariableValues(List<RuleExecutionTrace> traces, String variable) {
        List<Double> values = new ArrayList<>();
        for (RuleExecutionTrace trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot();
            if (facts == null) {
                continue;
            }
            Object val = facts.get(variable);
            if (val == null) {
                continue;
            }
            Double d = toDouble(val);
            if (d != null) {
                values.add(d);
            }
        }
        Collections.sort(values);
        return values;
    }

    /**
     * 计算数据分布统计
     */
    private DistributionStats calculateDistribution(List<Double> values,
                                                     ThresholdExtractor.ThresholdInfo ti,
                                                     List<RuleExecutionTrace> traces) {
        int total = values.size();
        // 计算当前阈值下的触发数
        int triggered = 0;
        for (double v : values) {
            if (satisfies(v, ti.getOperator(), ti.getThreshold())) {
                triggered++;
            }
        }
        int notTriggered = total - triggered;
        double triggerRate = total > 0 ? (double) triggered / total : 0.0;

        // 基础统计量
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double v : values) {
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double mean = sum / total;

        // 标准差
        double variance = 0;
        for (double v : values) {
            variance += (v - mean) * (v - mean);
        }
        double stddev = total > 1 ? Math.sqrt(variance / total) : 0;

        // 分位数
        double median = percentile(values, 50);
        double p90 = percentile(values, 90);
        double p95 = percentile(values, 95);
        double p99 = percentile(values, 99);

        return DistributionStats.builder()
                .totalCount(total)
                .triggeredCount(triggered)
                .notTriggeredCount(notTriggered)
                .triggerRate(triggerRate)
                .mean(mean)
                .median(median)
                .p90(p90)
                .p95(p95)
                .p99(p99)
                .min(min == Double.MAX_VALUE ? 0 : min)
                .max(max == Double.MIN_VALUE ? 0 : max)
                .stddev(stddev)
                .build();
    }

    /**
     * 根据数据特征确定调整策略
     */
    private ThresholdStrategy determineStrategy(DistributionStats stats) {
        double rate = stats.getTriggerRate();
        if (rate > HIGH_TRIGGER_RATE) {
            return ThresholdStrategy.FALSE_RATE;
        }
        if (rate < LOW_TRIGGER_RATE) {
            return ThresholdStrategy.MISS_RATE;
        }
        // 触发率在 5%~50% 之间，使用平衡策略
        return ThresholdStrategy.BALANCED;
    }

    /**
     * 根据策略计算建议阈值
     *
     * <p>对于 {@code >}/{@code >=} 运算符：阈值越高，触发率越低
     * <p>对于 {@code <}/{@code <=} 运算符：阈值越低，触发率越低
     */
    private double calculateSuggestedThreshold(ThresholdStrategy strategy,
                                                ThresholdExtractor.ThresholdInfo ti,
                                                DistributionStats stats,
                                                List<Double> values) {
        String op = ti.getOperator();
        boolean upperBound = op.equals("<") || op.equals("<=");

        return switch (strategy) {
            case PERCENTILE -> upperBound ? percentile(values, 5) : stats.getP95();
            case FALSE_RATE -> {
                // 触发率过高，提高阈值以降低触发率到 ~25%
                // 对于 > 运算符：取 P75（75% 数据低于此值 → 25% 触发）
                // 对于 < 运算符：取 P25（25% 数据低于此值 → 25% 触发）
                yield upperBound ? percentile(values, 25) : percentile(values, 75);
            }
            case MISS_RATE -> {
                // 触发率过低，降低阈值以提高触发率到 ~10%
                // 对于 > 运算符：取 P90（90% 数据低于此值 → 10% 触发）
                // 对于 < 运算符：取 P10（10% 数据低于此值 → 10% 触发）
                yield upperBound ? percentile(values, 10) : stats.getP90();
            }
            case BALANCED -> calculateBalancedThreshold(ti, values, stats);
            case LLM_SUGGESTED -> stats.getP95();
        };
    }

    /**
     * 计算 F1-score 最优阈值（BALANCED 策略）
     *
     * <p>遍历所有可能的阈值（去重后的样本值），计算每个阈值下的 F1-score，
     * 选择 F1-score 最高的阈值。F1 = 2 * precision * recall / (precision + recall)。
     *
     * <p>这里使用当前阈值下的触发情况作为"真实标签"（triggered=true 视为正样本），
     * 新阈值下的触发情况作为"预测标签"，寻找最优分割点。
     */
    private double calculateBalancedThreshold(ThresholdExtractor.ThresholdInfo ti,
                                               List<Double> values,
                                               DistributionStats stats) {
        String op = ti.getOperator();
        double currentThreshold = ti.getThreshold();

        // 去重后的候选阈值
        List<Double> candidates = new ArrayList<>(new LinkedHashSet<>(values));
        Collections.sort(candidates);
        if (candidates.isEmpty()) {
            return stats.getP95();
        }

        double bestThreshold = candidates.get(candidates.size() - 1);
        double bestF1 = -1;

        for (double candidate : candidates) {
            // 计算在该候选阈值下的 TP/FP/FN
            int tp = 0, fp = 0, fn = 0;
            for (double v : values) {
                boolean actualPositive = satisfies(v, op, currentThreshold);
                boolean predictedPositive = satisfies(v, op, candidate);
                if (actualPositive && predictedPositive) tp++;
                else if (!actualPositive && predictedPositive) fp++;
                else if (actualPositive && !predictedPositive) fn++;
            }
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
            double f1 = (precision + recall) > 0
                    ? 2 * precision * recall / (precision + recall) : 0;

            if (f1 > bestF1) {
                bestF1 = f1;
                bestThreshold = candidate;
            }
        }

        return bestThreshold;
    }

    /**
     * 计算置信度
     *
     * <p>置信度由样本量和分布集中度共同决定：
     * <ul>
     *   <li>样本量权重 60%：样本量 &ge; 200 时满分，&lt; 10 时为 0</li>
     *   <li>分布集中度权重 40%：变异系数（stddev/mean）越小越高</li>
     * </ul>
     */
    private double calculateConfidence(int sampleSize, DistributionStats stats) {
        // 样本量得分
        double sampleScore;
        if (sampleSize >= HIGH_CONFIDENCE_SAMPLE_SIZE) {
            sampleScore = 1.0;
        } else if (sampleSize < MIN_SAMPLE_SIZE) {
            sampleScore = 0.0;
        } else {
            sampleScore = (double) (sampleSize - MIN_SAMPLE_SIZE)
                    / (HIGH_CONFIDENCE_SAMPLE_SIZE - MIN_SAMPLE_SIZE);
        }

        // 分布集中度得分（基于变异系数）
        double concentrationScore;
        double mean = Math.abs(stats.getMean());
        if (mean < 1e-9) {
            // 均值接近 0 时无法计算变异系数，使用标准差绝对值
            concentrationScore = stats.getStddev() < 1.0 ? 1.0
                    : Math.max(0, 1.0 - stats.getStddev() / 100.0);
        } else {
            double cv = stats.getStddev() / mean;
            // 变异系数 < 0.1 视为高度集中，> 1.0 视为高度分散
            concentrationScore = cv < 0.1 ? 1.0
                    : cv > 1.0 ? 0.0 : 1.0 - (cv - 0.1) / 0.9;
        }

        double confidence = sampleScore * 0.6 + concentrationScore * 0.4;
        return Math.max(0, Math.min(1, confidence));
    }

    /**
     * 生成调整原因
     *
     * <p>LLM 可用时调用 LLM 生成自然语言原因；不可用时降级为模板生成。
     */
    private String generateReason(RuleDefinition rule, ThresholdExtractor.ThresholdInfo ti,
                                   DistributionStats stats, double suggested,
                                   ThresholdStrategy strategy) {
        // 模板原因（始终可用）
        String templateReason = buildTemplateReason(rule, ti, stats, suggested, strategy);

        if (llmClient == null) {
            return templateReason;
        }

        try {
            String userPrompt = buildLlmPrompt(rule, ti, stats, suggested, strategy);
            String llmReason = llmClient.chat(LLM_REASON_SYSTEM_PROMPT, userPrompt, null);
            if (llmReason != null && !llmReason.trim().isEmpty()) {
                return llmReason.trim();
            }
        } catch (LLMException e) {
            log.debug("[AdaptiveThreshold] LLM 生成原因失败，降级为模板: {}", e.getMessage());
        }
        return templateReason;
    }

    /**
     * 构建模板调整原因
     */
    private String buildTemplateReason(RuleDefinition rule, ThresholdExtractor.ThresholdInfo ti,
                                        DistributionStats stats, double suggested,
                                        ThresholdStrategy strategy) {
        String direction = suggested > ti.getThreshold() ? "提高" : "降低";
        return String.format(
                "规则[%s]变量%s当前阈值为%.4f，最近%d次执行中触发率为%.1f%%（触发%d次/未触发%d次）。"
                        + "基于%s策略，建议%s阈值到%.4f（均值=%.4f，中位数=%.4f，P95=%.4f）。",
                rule.getCode(), ti.getVariable(), ti.getThreshold(),
                stats.getTotalCount(), stats.getTriggerRate() * 100,
                stats.getTriggeredCount(), stats.getNotTriggeredCount(),
                strategy.name(), direction, suggested,
                stats.getMean(), stats.getMedian(), stats.getP95());
    }

    /**
     * 构建 LLM 提示词
     */
    private String buildLlmPrompt(RuleDefinition rule, ThresholdExtractor.ThresholdInfo ti,
                                    DistributionStats stats, double suggested,
                                    ThresholdStrategy strategy) {
        StringBuilder sb = new StringBuilder();
        sb.append("规则编码: ").append(rule.getCode()).append("\n");
        sb.append("规则名: ").append(rule.getName()).append("\n");
        sb.append("条件表达式: ").append(rule.getConditionExpression()).append("\n");
        sb.append("变量: ").append(ti.getVariable()).append("\n");
        sb.append("运算符: ").append(ti.getOperator()).append("\n");
        sb.append("当前阈值: ").append(ti.getThreshold()).append("\n");
        sb.append("建议阈值: ").append(suggested).append("\n");
        sb.append("调整策略: ").append(strategy.name()).append("\n");
        sb.append("样本量: ").append(stats.getTotalCount()).append("\n");
        sb.append("触发率: ").append(String.format("%.1f%%", stats.getTriggerRate() * 100)).append("\n");
        sb.append("均值: ").append(stats.getMean()).append("\n");
        sb.append("中位数: ").append(stats.getMedian()).append("\n");
        sb.append("P90: ").append(stats.getP90()).append("\n");
        sb.append("P95: ").append(stats.getP95()).append("\n");
        sb.append("P99: ").append(stats.getP99()).append("\n");
        sb.append("标准差: ").append(stats.getStddev()).append("\n");
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * 计算分位数
     *
     * @param sortedValues 升序排序的值列表
     * @param percentile   分位数（0~100）
     * @return 分位数值
     */
    private double percentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double rank = percentile / 100.0 * (sortedValues.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double fraction = rank - lower;
        return sortedValues.get(lower) * (1 - fraction) + sortedValues.get(upper) * fraction;
    }

    /**
     * 判断值是否满足比较条件
     */
    private boolean satisfies(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;
            default -> false;
        };
    }

    /**
     * 将 Object 转为 Double
     */
    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 替换表达式中的阈值
     *
     * @param expression      原表达式
     * @param variable        变量名
     * @param operator        运算符
     * @param currentThreshold 当前阈值
     * @param newThreshold     新阈值
     * @return 替换后的表达式；未找到匹配时返回 null
     */
    private String replaceThresholdInExpression(String expression, String variable,
                                                 String operator, double currentThreshold,
                                                 double newThreshold) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        // 转义正则特殊字符
        String opEscaped = operator.replaceAll("([<>=!])", "\\\\$1");
        // 格式化当前阈值（避免科学计数法）
        String currentStr = formatNumber(currentThreshold);
        String newStr = formatNumber(newThreshold);

        // 匹配 variable OP currentThreshold
        String pattern = variable + "\\s*" + opEscaped + "\\s*" + Pattern.quote(currentStr);
        String replacement = variable + " " + operator + " " + newStr;
        String result = expression.replaceAll(pattern, replacement);
        if (!result.equals(expression)) {
            return result;
        }

        // 尝试变量在右的形式：currentThreshold OP-flipped variable
        String flippedOp = flipOperator(operator);
        String flippedEscaped = flippedOp.replaceAll("([<>=!])", "\\\\$1");
        String pattern2 = Pattern.quote(currentStr) + "\\s*" + flippedEscaped + "\\s*" + variable;
        String replacement2 = newStr + " " + flippedOp + " " + variable;
        return expression.replaceAll(pattern2, replacement2);
    }

    /**
     * 翻转运算符
     */
    private String flipOperator(String op) {
        return switch (op) {
            case ">" -> "<";
            case "<" -> ">";
            case ">=" -> "<=";
            case "<=" -> ">=";
            default -> op;
        };
    }

    /**
     * 格式化数字（避免科学计数法，去除多余小数位）
     */
    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
