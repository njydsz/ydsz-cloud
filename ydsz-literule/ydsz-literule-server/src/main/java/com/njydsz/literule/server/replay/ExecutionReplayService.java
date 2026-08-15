package com.njydsz.literule.server.replay;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleExecutionTrace;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.RuleSeverity;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.api.expression.ExpressionEvaluator;
import com.njydsz.literule.server.impl.ExpressionRule;
import com.njydsz.literule.server.spi.RuleVersionRepository;
import com.njydsz.literule.server.spi.TraceRecorder;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行回放服务（P3-4）
 *
 * <p>基于历史执行轨迹（{@link RuleExecutionTrace}）中保存的事实快照（factsSnapshot），
 * 用当前规则集或指定版本重新评估，对比历史结果与当前结果，生成结构化差异报告。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #replayByTraceId(String)} - 按 traceId 回放单次执行（全部规则）</li>
 *   <li>{@link #batchReplay(List)} - 批量回放多条 trace，生成汇总差异报告</li>
 *   <li>{@link #replayWithVersion(String, String, int)} - 指定规则版本回放（对比历史与目标版本）</li>
 *   <li>{@link #replayWithExpression(String, String, String, String, RuleSeverity, Map)} - 用自定义表达式回放</li>
 * </ul>
 *
 * <h3>差异类型</h3>
 * <ul>
 *   <li>{@code ADDED} - 历史未触发，当前触发（新增触发）</li>
 *   <li>{@code REMOVED} - 历史触发，当前未触发（减少触发）</li>
 *   <li>{@code SEVERITY_CHANGED} - 触发状态不变，但严重度变化</li>
 *   <li>{@code UNCHANGED} - 触发状态和严重度均不变</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ExecutionReplayService service = new ExecutionReplayService(ruleAdminService, traceRecorder, versionRepository, evaluator);
 *
 * // 单条回放
 * ReplayResult result = service.replayByTraceId("trace-abc-123");
 *
 * // 批量回放
 * List<RuleExecutionTrace> traces = traceRecorder.getByRuleCode("RISK_001", 100);
 * BatchReplayResult batchResult = service.batchReplay(traces);
 *
 * // 指定版本回放
 * ReplayResult versionResult = service.replayWithVersion("trace-abc-123", "RISK_001", 3);
 * }</pre>
 *
 * @author ydsz-team
 *
 * @since 1.0.0
 */
@Slf4j
public class ExecutionReplayService {

    private final RuleAdminService ruleAdminService;
    private final TraceRecorder traceRecorder;
    private final RuleVersionRepository versionRepository;
    private final ExpressionEvaluator evaluator;

    /**
     * 构造执行回放服务
     *
     * @param ruleAdminService  规则管理服务（必需，用于 dry-run 和表达式评估）
     * @param traceRecorder     轨迹记录器（必需，用于加载历史 trace）
     * @param versionRepository 版本仓库（可选，为 null 时不支持版本回放）
     * @param evaluator         表达式求值器（必需，用于版本回放时构建临时规则）
     */
    public ExecutionReplayService(RuleAdminService ruleAdminService,
                                    TraceRecorder traceRecorder,
                                    RuleVersionRepository versionRepository,
                                    ExpressionEvaluator evaluator) {
        this.ruleAdminService = Objects.requireNonNull(ruleAdminService, "ruleAdminService");
        this.traceRecorder = traceRecorder;
        this.versionRepository = versionRepository;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /**
     * 按 traceId 回放单次执行
     *
     * <p>从历史 trace 记录中读取 factsSnapshot，用当前规则集重新评估全部规则，
     * 对比历史结果与当前结果，展示规则变更后的差异。
     *
     * @param traceId 追踪 ID
     * @return 回放结果（含历史快照 + 当前评估 + 差异分析）
     */
    public ReplayResult replayByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return ReplayResult.error(traceId, "traceId 不能为空");
        }
        if (traceRecorder == null) {
            return ReplayResult.error(traceId, "TraceRecorder 未配置，无法加载历史轨迹");
        }

        List<RuleExecutionTrace> traces = traceRecorder.getByTraceId(traceId);
        if (traces == null || traces.isEmpty()) {
            return ReplayResult.error(traceId, "未找到 traceId=" + traceId + " 的执行记录");
        }

        // 取第一条 trace 的 factsSnapshot 作为回放输入
        Map<String, Object> facts = traces.get(0).getFactsSnapshot();
        if (facts == null || facts.isEmpty()) {
            return ReplayResult.error(traceId, "traceId=" + traceId + " 的事实快照为空，无法回放");
        }

        // 用当前规则集重新评估
        List<RuleResult> currentResults = ruleAdminService.dryRun(null, facts);

        // 构建历史触发规则编码集合
        Set<String> historicalTriggered = traces.stream()
                .filter(RuleExecutionTrace::isTriggered)
                .map(RuleExecutionTrace::getRuleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 构建当前触发规则编码集合
        Set<String> currentTriggered = currentResults.stream()
                .filter(RuleResult::isTriggered)
                .map(RuleResult::getRuleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 差异分析
        ReplayDiff diff = computeDiff(historicalTriggered, currentTriggered);

        return ReplayResult.builder()
                .traceId(traceId)
                .factsSnapshot(facts)
                .historicalTraces(traces)
                .currentResults(currentResults)
                .diff(diff)
                .replayedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 批量回放
     *
     * <p>对每条 trace 用当前规则集重新评估，对比历史结果与当前结果，
     * 生成汇总差异报告。
     *
     * @param traces 待回放的历史轨迹列表
     * @return 批量回放差异报告
     */
    public BatchReplayResult batchReplay(List<RuleExecutionTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return BatchReplayResult.empty();
        }

        List<ReplayDiffEntry> diffs = new ArrayList<>();
        int consistentCount = 0;
        int diffCount = 0;
        int skippedCount = 0;

        for (RuleExecutionTrace trace : traces) {
            Map<String, Object> facts = trace.getFactsSnapshot();
            if (facts == null || facts.isEmpty()) {
                skippedCount++;
                continue;
            }

            // 用当前规则集对单条规则重新评估
            List<RuleResult> currentResults = ruleAdminService.dryRun(trace.getRuleCode(), facts);
            RuleResult currentResult = currentResults.stream()
                    .filter(r -> trace.getRuleCode() != null && trace.getRuleCode().equals(r.getRuleCode()))
                    .findFirst()
                    .orElse(null);

            boolean historicalTriggered = trace.isTriggered();
            boolean currentTriggered = currentResult != null && currentResult.isTriggered();
            String historicalSeverity = trace.getSeverity();
            String currentSeverity = currentResult != null && currentResult.getSeverity() != null
                    ? currentResult.getSeverity().name() : null;

            boolean severityConsistent = severityEquals(historicalSeverity, currentSeverity);

            if (historicalTriggered == currentTriggered && severityConsistent) {
                consistentCount++;
            } else {
                diffCount++;
                DiffType diffType = classifyDiff(historicalTriggered, currentTriggered, severityConsistent);
                diffs.add(ReplayDiffEntry.builder()
                        .traceId(trace.getTraceId())
                        .ruleCode(trace.getRuleCode())
                        .ruleName(trace.getRuleName())
                        .historicalTriggered(historicalTriggered)
                        .currentTriggered(currentTriggered)
                        .historicalSeverity(historicalSeverity)
                        .currentSeverity(currentSeverity)
                        .diffType(diffType)
                        .replayedAt(trace.getCreatedAt())
                        .build());
            }
        }

        return BatchReplayResult.builder()
                .totalReplayed(traces.size())
                .consistentCount(consistentCount)
                .diffCount(diffCount)
                .skippedCount(skippedCount)
                .diffs(diffs)
                .summary(String.format("共回放 %d 条，一致 %d 条，差异 %d 条，跳过 %d 条",
                        traces.size(), consistentCount, diffCount, skippedCount))
                .replayedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 指定规则版本回放
     *
     * <p>从历史 trace 中加载事实快照，用指定版本的规则定义重新评估，
     * 对比历史结果与目标版本结果。用于验证版本回滚后的行为是否符合预期。
     *
     * @param traceId  追踪 ID
     * @param ruleCode 规则编码
     * @param version  目标版本号
     * @return 回放结果
     */
    public ReplayResult replayWithVersion(String traceId, String ruleCode, int version) {
        if (traceId == null || traceId.isBlank()) {
            return ReplayResult.error(traceId, "traceId 不能为空");
        }
        if (versionRepository == null) {
            return ReplayResult.error(traceId, "版本仓库未配置，不支持版本回放");
        }

        // 加载历史 trace
        List<RuleExecutionTrace> traces = traceRecorder != null
                ? traceRecorder.getByTraceId(traceId) : Collections.emptyList();
        if (traces.isEmpty()) {
            return ReplayResult.error(traceId, "未找到 traceId=" + traceId + " 的执行记录");
        }

        // 查找目标规则的 trace
        RuleExecutionTrace targetTrace = traces.stream()
                .filter(t -> ruleCode != null && ruleCode.equals(t.getRuleCode()))
                .findFirst()
                .orElse(null);
        if (targetTrace == null) {
            return ReplayResult.error(traceId, "traceId=" + traceId + " 中未找到规则 " + ruleCode + " 的执行记录");
        }

        Map<String, Object> facts = targetTrace.getFactsSnapshot();
        if (facts == null || facts.isEmpty()) {
            return ReplayResult.error(traceId, "事实快照为空，无法回放");
        }

        // 加载指定版本的规则定义
        RuleDefinition versionDef = versionRepository.rollback(ruleCode, version, "REPLAY");
        if (versionDef == null) {
            return ReplayResult.error(traceId, "未找到规则 " + ruleCode + " 的版本 " + version);
        }

        // 用目标版本重新评估
        ExpressionRule versionRule = new ExpressionRule(versionDef, evaluator);
        RuleContext context =
                RuleContext.of(facts, "REPLAY", "MANUAL");
        RuleResult versionResult = versionRule.evaluate(context);

        // 同时用当前规则评估
        List<RuleResult> currentResults = ruleAdminService.dryRun(ruleCode, facts);

        // 构建差异
        Set<String> historicalTriggered = new LinkedHashSet<>();
        if (targetTrace.isTriggered()) {
            historicalTriggered.add(ruleCode);
        }
        Set<String> versionTriggered = new LinkedHashSet<>();
        if (versionResult.isTriggered()) {
            versionTriggered.add(ruleCode);
        }
        Set<String> currentTriggered = currentResults.stream()
                .filter(RuleResult::isTriggered)
                .map(RuleResult::getRuleCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ReplayDiff diffVsHistory = computeDiff(historicalTriggered, versionTriggered);
        ReplayDiff diffVsCurrent = computeDiff(versionTriggered, currentTriggered);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("versionDef", versionDef);
        extra.put("versionResult", versionResult);
        extra.put("diffVsCurrent", diffVsCurrent);

        return ReplayResult.builder()
                .traceId(traceId)
                .factsSnapshot(facts)
                .historicalTraces(traces)
                .currentResults(currentResults)
                .diff(diffVsHistory)
                .extra(extra)
                .replayedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 用自定义表达式回放
     *
     * <p>使用新的条件/严重度表达式对历史事实快照重新评估，
     * 用于预览规则变更后的影响。
     *
     * @param traceId             追踪 ID
     * @param ruleCode            规则编码
     * @param conditionExpression 新条件表达式
     * @param severityExpression  新严重度表达式（可为 null）
     * @param defaultSeverity     默认严重度
     * @param facts               事实数据（为 null 时从 trace 加载）
     * @return 评估结果
     */
    public RuleResult replayWithExpression(String traceId, String ruleCode,
                                             String conditionExpression,
                                             String severityExpression,
                                             RuleSeverity defaultSeverity,
                                             Map<String, Object> facts) {
        Map<String, Object> replayFacts = facts;
        if (replayFacts == null || replayFacts.isEmpty()) {
            if (traceRecorder == null) {
                return RuleResult.notTriggered(ruleCode);
            }
            List<RuleExecutionTrace> traces = traceRecorder.getByTraceId(traceId);
            if (!traces.isEmpty()) {
                replayFacts = traces.get(0).getFactsSnapshot();
            }
        }
        return ruleAdminService.evaluateWithExpression(
                ruleCode, conditionExpression, severityExpression, defaultSeverity, replayFacts);
    }

    // ==================== 内部方法 ====================

    private ReplayDiff computeDiff(Set<String> historicalTriggered, Set<String> currentTriggered) {
        Set<String> added = new LinkedHashSet<>(currentTriggered);
        added.removeAll(historicalTriggered);

        Set<String> removed = new LinkedHashSet<>(historicalTriggered);
        removed.removeAll(currentTriggered);

        Set<String> unchanged = new LinkedHashSet<>(currentTriggered);
        unchanged.retainAll(historicalTriggered);

        return ReplayDiff.builder()
                .added(added)
                .removed(removed)
                .unchanged(unchanged)
                .summary(String.format("新增触发 %d 条，移除触发 %d 条，保持不变 %d 条",
                        added.size(), removed.size(), unchanged.size()))
                .build();
    }

    private boolean severityEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equalsIgnoreCase(s2);
    }

    private DiffType classifyDiff(boolean historicalTriggered, boolean currentTriggered,
                                   boolean severityConsistent) {
        if (!historicalTriggered && currentTriggered) {
            return DiffType.ADDED;
        }
        if (historicalTriggered && !currentTriggered) {
            return DiffType.REMOVED;
        }
        if (!severityConsistent) {
            return DiffType.SEVERITY_CHANGED;
        }
        return DiffType.UNCHANGED;
    }

    // ==================== 结果对象 ====================

    /**
     * 差异类型
     */
    public enum DiffType {
        ADDED, REMOVED, SEVERITY_CHANGED, UNCHANGED
    }

    /**
     * 单次回放结果
     */
    @Data
    @Builder
    public static class ReplayResult {
        private String traceId;
        private Map<String, Object> factsSnapshot;
        private List<RuleExecutionTrace> historicalTraces;
        private List<RuleResult> currentResults;
        private ReplayDiff diff;
        private Map<String, Object> extra;
        private LocalDateTime replayedAt;
        private String errorMessage;

        public boolean isSuccess() {
            return errorMessage == null;
        }

        /**
         * 构造回放失败结果（携带 errorMessage，{@link #isSuccess()} 返回 false）。
         *
         * @param traceId 追踪 ID（可能为 null，调用方用于回显）
         * @param error   失败原因描述
         * @return 失败的 {@link ReplayResult}
         */
        public static ReplayResult error(String traceId, String error) {
            return ReplayResult.builder()
                    .traceId(traceId)
                    .errorMessage(error)
                    .replayedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 批量回放结果
     */
    @Data
    @Builder
    public static class BatchReplayResult {
        private int totalReplayed;
        private int consistentCount;
        private int diffCount;
        private int skippedCount;
        private List<ReplayDiffEntry> diffs;
        private String summary;
        private LocalDateTime replayedAt;

        /**
         * 构造空批量回放结果（输入为空时返回，所有计数归零、diffs 为空）。
         *
         * @return 空的 {@link BatchReplayResult}
         */
        public static BatchReplayResult empty() {
            return BatchReplayResult.builder()
                    .totalReplayed(0)
                    .consistentCount(0)
                    .diffCount(0)
                    .skippedCount(0)
                    .diffs(Collections.emptyList())
                    .summary("无回放数据")
                    .replayedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 回放差异条目
     */
    @Data
    @Builder
    public static class ReplayDiffEntry {
        private String traceId;
        private String ruleCode;
        private String ruleName;
        private boolean historicalTriggered;
        private boolean currentTriggered;
        private String historicalSeverity;
        private String currentSeverity;
        private DiffType diffType;
        private LocalDateTime replayedAt;
    }

    /**
     * 回放差异汇总
     */
    @Data
    @Builder
    public static class ReplayDiff {
        private Set<String> added;
        private Set<String> removed;
        private Set<String> unchanged;
        private String summary;
    }
}
