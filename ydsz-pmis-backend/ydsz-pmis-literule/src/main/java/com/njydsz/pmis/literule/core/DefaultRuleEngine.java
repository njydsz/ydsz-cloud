package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.StatsRecorder;
import com.njydsz.pmis.literule.spi.TraceRecorder;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认规则引擎实现
 *
 * <p>核心能力：
 * <ul>
 *   <li>规则注册/注销（线程安全 CopyOnWriteArrayList）</li>
 *   <li>按优先级编排执行（priority 数值越小越先执行）</li>
 *   <li>单规则异常隔离（不影响其他规则）</li>
 *   <li>结果按严重度倒序排列（RED → YELLOW → INFO）</li>
 *   <li>执行统计（执行次数/触发次数/异常次数/耗时）</li>
 *   <li>Dry-run 仿真（返回全部结果含未触发，不记录统计）</li>
 *   <li>执行轨迹异步记录（1.4.0）</li>
 *   <li>单规则超时与熔断（1.4.0）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class DefaultRuleEngine implements RuleEngine, StatsRecorder {

    /** 已注册规则列表（按优先级排序） */
    private final CopyOnWriteArrayList<Rule> rules = new CopyOnWriteArrayList<>();

    /** 规则索引器（P0-1：大规则量场景索引优化） */
    private final RuleIndexer ruleIndexer = new RuleIndexer();

    /** 是否启用统计（对应 pmis.literule.statsEnabled 配置） */
    private volatile boolean statsEnabled = true;

    /** 轨迹记录器（可选，1.4.0 起支持） */
    private volatile TraceRecorder traceRecorder;

    /** 超时执行器（可选，1.4.0 起支持） */
    private volatile RuleTimeoutExecutor timeoutExecutor;

    /** 熔断器（可选，1.4.0 起支持） */
    private volatile RuleCircuitBreaker circuitBreaker;

    /** 监控指标（可选，1.4.0 起支持） */
    private volatile RuleMetrics metrics;

    /** 灰度路由器（可选，1.4.0 起支持） */
    private volatile RuleCanaryRouter canaryRouter;

    /** 是否启用灰度路由（与 canaryRouter 双重判断） */
    private volatile boolean canaryEnabled = true;

    /** 断点调试 Hook（可选，1.4.0 起支持 P2-3） */
    private volatile BreakpointHook breakpointHook;

    /** 统计计数器 */
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalTriggered = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalElapsedMs = new AtomicLong(0);

    /** 按规则编码的统计明细 */
    private final ConcurrentHashMap<String, RuleEngineStats.RuleStat> perRuleStats = new ConcurrentHashMap<>();

    @Override
    public void register(Rule rule) {
        if (rule == null || rule.getCode() == null) {
            return;
        }
        // 先移除同编码旧规则（支持热更新覆盖）
        unregister(rule.getCode());
        // 增量保序插入（P2-10）：二分查找插入位置，避免全量 sort
        int insertIdx = binarySearchInsertIndex(rule.getPriority());
        rules.add(insertIdx, rule);
        // 增量更新索引
        ruleIndexer.addToIndex(rule);
        // 当规则数首次超过阈值时，重建索引启用索引模式
        if (!ruleIndexer.isIndexEnabled() && rules.size() >= 200) {
            ruleIndexer.rebuildIndex(rules);
        }
        recordRegisteredRules();
        log.info("[LiteRule] 规则已注册: code={}, name={}, priority={}, total={}",
                rule.getCode(), rule.getName(), rule.getPriority(), rules.size());
    }

    /**
     * 二分查找按 priority 的插入位置（priority 升序）
     *
     * <p>由于 rules 已按 priority 升序排列，使用二分查找可将"找位置"从 O(n) 降到 O(log n)，
     * 总体插入复杂度由 O(n log n)（全量 sort）降为 O(n)（数组移动 + 二分查找）。
     * 规模化（>1000 规则）注册时性能提升显著。
     *
     * @param priority 待插入规则的优先级
     * @return 插入位置索引
     * @since 1.5.1
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

    @Override
    public void unregister(String ruleCode) {
        if (ruleCode == null) return;
        rules.removeIf(r -> ruleCode.equals(r.getCode()));
        ruleIndexer.removeFromIndex(ruleCode);
        recordRegisteredRules();
    }

    /**
     * 记录当前注册规则数到监控指标
     */
    private void recordRegisteredRules() {
        if (metrics != null) {
            metrics.recordRegisteredRules(rules.size());
        }
    }

    @Override
    public List<RuleResult> evaluate(RuleContext context) {
        List<RuleResult> triggered = new ArrayList<>();
        // 互斥组：记录本次评估中已命中的互斥组，同组后续规则跳过
        Set<String> triggeredGroups = new HashSet<>();
        String scenario = context.getScenario();
        String contextTenantId = context.getTenantId();
        int evaluatedCount = 0;

        // P0-1：使用索引查找候选规则（大规则量场景性能优化）
        List<Rule> candidateRules = ruleIndexer.isIndexEnabled()
                ? ruleIndexer.findCandidates(contextTenantId, scenario, triggeredGroups)
                : rules;

        // 遍历候选规则（索引模式下已按租户+场景+互斥组过滤）
        for (Rule rule : candidateRules) {
            // 索引未启用时仍需租户和场景过滤
            if (!ruleIndexer.isIndexEnabled()) {
                // 租户隔离（1.5.0）：仅评估与上下文租户匹配的规则
                if (!java.util.Objects.equals(rule.getTenantId(), contextTenantId)) {
                    continue;
                }
                // 场景过滤：非 DEFAULT 场景下，跳过 scope 不匹配的规则
                if (!shouldEvaluate(rule, scenario)) {
                    continue;
                }
            }

            // 互斥组短路：同组内已有规则命中，跳过评估
            // 索引模式可能已排除了互斥组，但运行时 triggeredGroups 是动态更新的，仍需检查
            String mutexGroup = rule.getMutexGroup();
            if (mutexGroup != null && !mutexGroup.isBlank() && triggeredGroups.contains(mutexGroup)) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule] 规则 {} 所属互斥组 {} 已命中，跳过评估", rule.getCode(), mutexGroup);
                }
                continue;
            }

            evaluatedCount++;

            // 熔断检查：已被熔断的规则跳过评估
            if (circuitBreaker != null && !circuitBreaker.allowEvaluate(rule.getCode())) {
                log.debug("[LiteRule] 规则 {} 已被熔断，跳过评估", rule.getCode());
                continue;
            }

            // 断点调试（P2-3）：仅在规则设置了断点时触发，避免对全部规则产生性能开销
            BreakpointHook bpHook = this.breakpointHook;
            boolean hasBreakpoint = bpHook != null && bpHook.hasBreakpoint(rule.getCode());
            Map<String, Object> bpFactsSnapshot = null;
            if (hasBreakpoint) {
                // 提取 final 局部变量，IDE 才能识别为非空
                final BreakpointHook hook = Objects.requireNonNull(bpHook, "breakpointHook");
                try {
                    bpFactsSnapshot = new LinkedHashMap<>(context.getFacts());
                    BreakpointHook.BreakpointContext beforeCtx = new BreakpointHook.BreakpointContext(
                            "BEFORE", context.getTraceId(), rule.getCode(), rule.getName(),
                            scenario, bpFactsSnapshot);
                    BreakpointHook.BreakpointAction action = hook.onBeforeEvaluate(beforeCtx);
                    if (log.isDebugEnabled()) {
                        log.debug("[LiteRule] 规则 {} 命中断点 onBeforeEvaluate action={}", rule.getCode(), action);
                    }
                    if (action == BreakpointHook.BreakpointAction.STEP_OVER) {
                        // 单步跳过：不评估当前规则，直接进入下一条
                        continue;
                    }
                    // SUSPEND 的实际阻塞由 hook 实现内部完成（如阻塞等待外部唤醒），引擎层不感知
                } catch (Exception be) {
                    log.debug("[LiteRule] 断点 onBeforeEvaluate 异常: {}", be.getMessage());
                }
            }

            long start = System.nanoTime();
            RuleResult result = null;
            Exception caughtException = null;
            boolean routedToCanary = false;

            // 灰度路由：仅对带 canaryRatio 的表达式规则生效
            RuleDefinition canaryDef = resolveCanaryDefinition(rule);
            if (canaryDef != null) {
                boolean goCanary = canaryRouter.shouldRouteToCanary(canaryDef, context);
                canaryRouter.recordBucket(rule.getCode(), goCanary);
                if (goCanary) {
                    routedToCanary = true;
                    Rule canaryRule = canaryRouter.buildCanaryRule(canaryDef);
                    try {
                        if (timeoutExecutor != null) {
                            result = timeoutExecutor.evaluateWithTimeout(canaryRule, context, 0);
                        } else {
                            result = canaryRule.evaluate(context);
                        }
                    } catch (Exception e) {
                        caughtException = e;
                    }
                    if (result != null) {
                        canaryRouter.markCanary(result);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("[LiteRule-Canary] 规则 {} 命中灰度桶，评估候选版本", rule.getCode());
                    }
                }
            }

            // 未路由到灰度桶：评估主版本
            if (!routedToCanary) {
                try {
                    if (timeoutExecutor != null) {
                        result = timeoutExecutor.evaluateWithTimeout(rule, context, 0);
                    } else {
                        result = rule.evaluate(context);
                    }
                } catch (Exception e) {
                    caughtException = e;
                }
            }

            long elapsed = (System.nanoTime() - start) / 1_000_000;
            boolean isTriggered = result != null && result.isTriggered();
            // 异常 + 超时返回的"未触发"也算异常（用于熔断统计）
            boolean isError = caughtException != null
                    || (result != null && result.getDescription() != null
                        && result.getDescription().startsWith("评估超时"));
            record(rule.getCode(), isTriggered, isError, elapsed);

            // 断点调试（P2-3）：评估后回调，供 hook 查看结果与上下文快照
            if (hasBreakpoint) {
                // 提取 final 局部变量，IDE 才能识别为非空
                final BreakpointHook hook = Objects.requireNonNull(bpHook, "breakpointHook");
                try {
                    BreakpointHook.BreakpointContext afterCtx = new BreakpointHook.BreakpointContext(
                            "AFTER", context.getTraceId(), rule.getCode(), rule.getName(),
                            scenario, bpFactsSnapshot);
                    afterCtx.setResult(result);
                    afterCtx.setElapsedMs(elapsed);
                    if (caughtException != null) {
                        afterCtx.setException(caughtException);
                    }
                    hook.onAfterEvaluate(afterCtx);
                } catch (Exception ae) {
                    log.debug("[LiteRule] 断点 onAfterEvaluate 异常: {}", ae.getMessage());
                }
            }

            // 熔断器记录结果
            if (circuitBreaker != null) {
                circuitBreaker.recordResult(rule.getCode(), !isError);
            }

            // 监控指标记录
            if (metrics != null) {
                try {
                    metrics.recordEvaluation(rule.getCode(), scenario, isTriggered,
                            result != null ? result.getSeverity() : null, isError, elapsed);
                } catch (Exception me) {
                    log.debug("[LiteRule] 指标记录失败: {}", me.getMessage());
                }
            }

            if (isError && caughtException != null) {
                log.warn("[LiteRule] 规则 {} 评估异常: {}", rule.getCode(), caughtException.getMessage());
            }
            // 异步记录 Trace（即使异常也记录，便于排查）
            if (traceRecorder != null && traceRecorder.isEnabled()) {
                try {
                    RuleExecutionTrace trace = buildTrace(context, rule, result, elapsed, caughtException);
                    traceRecorder.record(trace);
                } catch (Exception te) {
                    log.debug("[LiteRule] Trace 记录失败: {}", te.getMessage());
                }
            }
            if (isTriggered) {
                triggered.add(result);
                // 互斥组：记录已命中的组，同组后续规则跳过评估
                if (mutexGroup != null && !mutexGroup.isBlank()) {
                    triggeredGroups.add(mutexGroup);
                }
            }
        }
        // 按严重度倒序
        triggered.sort(Comparator.comparingInt((RuleResult r) -> severityWeight(r)).reversed());
        // 记录本次评估遍历的规则数（用于规则规模监控）
        if (metrics != null) {
            metrics.recordEvaluatedRules(evaluatedCount);
        }
        return triggered;
    }

    /**
     * 解析规则对应的灰度候选定义
     *
     * <p>仅当以下条件全部满足时返回非 null：
     * <ul>
     *   <li>canaryEnabled = true</li>
     *   <li>canaryRouter 已注入</li>
     *   <li>规则暴露了 RuleDefinition（即 {@code rule.getRuleDefinition()} 非空）</li>
     *   <li>canaryRatio > 0 且配置了候选表达式（条件或严重度）</li>
     * </ul>
     *
     * @param rule 规则
     * @return 灰度定义；不满足条件返回 null
     * @since 1.4.0
     */
    private RuleDefinition resolveCanaryDefinition(Rule rule) {
        if (!canaryEnabled || canaryRouter == null) {
            return null;
        }
        RuleDefinition def = rule.getRuleDefinition();
        if (def == null || def.getCanaryRatio() <= 0) {
            return null;
        }
        if (def.getCanaryConditionExpression() == null && def.getCanarySeverityExpression() == null) {
            return null;
        }
        return def;
    }

    @Override
    public RuleResult topResult(RuleContext context) {
        List<RuleResult> all = evaluate(context);
        return all.isEmpty() ? null : all.get(0);
    }

    @Override
    public List<RuleResult> dryRun(RuleContext context) {
        List<RuleResult> all = new ArrayList<>();
        String contextTenantId = context.getTenantId();
        for (Rule rule : rules) {
            // 租户隔离（1.5.0）：dry-run 同样仅评估与上下文租户匹配的规则
            if (!java.util.Objects.equals(rule.getTenantId(), contextTenantId)) {
                continue;
            }
            try {
                RuleResult result = rule.evaluate(context);
                if (result == null) {
                    result = RuleResult.notTriggered(rule.getCode());
                }
                all.add(result);
            } catch (Exception e) {
                all.add(RuleResult.builder()
                        .ruleCode(rule.getCode())
                        .triggered(false)
                        .description("评估异常: " + e.getMessage())
                        .build());
            }
        }
        return all;
    }

    @Override
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    @Override
    public RuleEngineStats getStats() {
        Map<String, RuleEngineStats.RuleStat> snapshot = new ConcurrentHashMap<>();
        perRuleStats.forEach((k, v) -> snapshot.put(k, RuleEngineStats.RuleStat.builder()
                .executions(v.getExecutions())
                .triggered(v.getTriggered())
                .errors(v.getErrors())
                .totalElapsedMs(v.getTotalElapsedMs())
                .build()));
        return RuleEngineStats.builder()
                .totalEvaluations(totalEvaluations.get())
                .totalTriggered(totalTriggered.get())
                .totalErrors(totalErrors.get())
                .totalElapsedMs(totalElapsedMs.get())
                .registeredRules(rules.size())
                .lastEvaluatedRules(metrics != null ? metrics.getLastEvaluatedRules() : 0)
                .perRuleStats(snapshot)
                .build();
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        totalEvaluations.set(0);
        totalTriggered.set(0);
        totalErrors.set(0);
        totalElapsedMs.set(0);
        perRuleStats.clear();
    }

    /**
     * 设置是否启用统计
     *
     * @param statsEnabled 是否启用
     * @since 1.3.0
     */
    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    /**
     * 获取是否启用统计
     *
     * @return 是否启用
     * @since 1.3.0
     */
    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    /**
     * 将引擎作为统计记录器暴露给编排层使用
     *
     * @return StatsRecorder 实例
     * @since 1.3.0
     */
    public StatsRecorder asStatsRecorder() {
        return this;
    }

    /**
     * 设置轨迹记录器
     *
     * @param traceRecorder 轨迹记录器；null 表示禁用 Trace
     * @since 1.4.0
     */
    public void setTraceRecorder(TraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    /**
     * 获取轨迹记录器
     *
     * @return 轨迹记录器；未配置返回 null
     * @since 1.4.0
     */
    public TraceRecorder getTraceRecorder() {
        return traceRecorder;
    }

    /**
     * 设置超时执行器
     *
     * @param timeoutExecutor 超时执行器；null 表示禁用超时控制
     * @since 1.4.0
     */
    public void setTimeoutExecutor(RuleTimeoutExecutor timeoutExecutor) {
        this.timeoutExecutor = timeoutExecutor;
    }

    /**
     * 获取超时执行器
     *
     * @return 超时执行器；未配置返回 null
     * @since 1.4.0
     */
    public RuleTimeoutExecutor getTimeoutExecutor() {
        return timeoutExecutor;
    }

    /**
     * 设置熔断器
     *
     * @param circuitBreaker 熔断器；null 表示禁用熔断
     * @since 1.4.0
     */
    public void setCircuitBreaker(RuleCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 获取熔断器
     *
     * @return 熔断器；未配置返回 null
     * @since 1.4.0
     */
    public RuleCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * 设置监控指标
     *
     * @param metrics 监控指标；null 表示禁用
     * @since 1.4.0
     */
    public void setMetrics(RuleMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 获取监控指标
     *
     * @return 监控指标；未配置返回 null
     * @since 1.4.0
     */
    public RuleMetrics getMetrics() {
        return metrics;
    }

    /**
     * 设置灰度路由器
     *
     * @param canaryRouter 灰度路由器；null 表示禁用灰度
     * @since 1.4.0
     */
    public void setCanaryRouter(RuleCanaryRouter canaryRouter) {
        this.canaryRouter = canaryRouter;
    }

    /**
     * 获取灰度路由器
     *
     * @return 灰度路由器；未配置返回 null
     * @since 1.4.0
     */
    public RuleCanaryRouter getCanaryRouter() {
        return canaryRouter;
    }

    /**
     * 设置是否启用灰度路由
     *
     * @param canaryEnabled 是否启用
     * @since 1.4.0
     */
    public void setCanaryEnabled(boolean canaryEnabled) {
        this.canaryEnabled = canaryEnabled;
    }

    /**
     * 获取是否启用灰度路由
     *
     * @return 是否启用
     * @since 1.4.0
     */
    public boolean isCanaryEnabled() {
        return canaryEnabled;
    }

    /**
     * 设置断点调试 Hook（P2-3）
     *
     * @param breakpointHook 断点 Hook；null 表示禁用断点调试
     * @since 1.4.0
     */
    public void setBreakpointHook(BreakpointHook breakpointHook) {
        this.breakpointHook = breakpointHook;
        if (breakpointHook != null) {
            log.info("[LiteRule] 断点调试 Hook 已注入: {}", breakpointHook.getClass().getSimpleName());
        }
    }

    /**
     * 获取断点调试 Hook（P2-3）
     *
     * @return 断点 Hook；未配置返回 null
     * @since 1.4.0
     */
    public BreakpointHook getBreakpointHook() {
        return breakpointHook;
    }

    /**
     * 构建执行轨迹记录
     *
     * @param context   规则上下文
     * @param rule      规则
     * @param result    评估结果（可能为 null）
     * @param elapsedMs 耗时
     * @param exception 评估异常（可能为 null）
     * @return 轨迹记录
     * @since 1.4.0
     */
    private RuleExecutionTrace buildTrace(RuleContext context, Rule rule, RuleResult result,
                                          long elapsedMs, Exception exception) {
        String severity = result != null && result.getSeverity() != null
                ? result.getSeverity().getCode() : null;
        String conditionResult = result != null && result.getThreshold() != null
                ? result.getThreshold() : null;

        Map<String, Object> resultSnapshot = new LinkedHashMap<>();
        if (result != null) {
            resultSnapshot.put("triggered", result.isTriggered());
            resultSnapshot.put("severity", severity);
            resultSnapshot.put("title", result.getTitle());
            resultSnapshot.put("description", result.getDescription());
        }

        return new RuleExecutionTrace(
                context.getTraceId(),
                rule.getCode(),
                rule.getName(),
                context.getScenario(),
                result != null && result.isTriggered(),
                severity,
                conditionResult,
                elapsedMs,
                new LinkedHashMap<>(context.getFacts()),
                resultSnapshot,
                exception != null ? exception.getMessage() : null
        );
    }

    /**
     * 优雅关闭：释放 TraceRecorder 与超时执行器资源
     *
     * @since 1.4.0
     */
    @PreDestroy
    public void destroy() {
        if (traceRecorder instanceof AsyncTraceRecorder asyncRecorder) {
            asyncRecorder.shutdown(5);
            log.info("[LiteRule] 异步 Trace 记录器已关闭");
        }
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdown();
        }
    }

    /**
     * 判断规则是否应在当前场景下评估
     *
     * <p>过滤规则：
     * <ul>
     *   <li>scenario 为 null 或 "DEFAULT" 时，评估全部规则（向后兼容）</li>
     *   <li>rule.getScope() 为 null 或 "ALL" 时，适用于全部场景</li>
     *   <li>否则仅当 rule.getScope() 与 scenario 匹配时评估</li>
     * </ul>
     *
     * @param rule     规则
     * @param scenario 当前场景
     * @return 是否应评估
     * @since 1.3.0
     */
    private boolean shouldEvaluate(Rule rule, String scenario) {
        if (scenario == null || "DEFAULT".equals(scenario)) {
            return true;
        }
        String scope = rule.getScope();
        if (scope == null || "ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        return scope.equalsIgnoreCase(scenario);
    }

    /**
     * 记录统计（实现 {@link StatsRecorder}）
     *
     * @param ruleCode   规则编码
     * @param triggered  是否触发
     * @param error      是否异常
     * @param elapsedMs  耗时
     */
    @Override
    public void record(String ruleCode, boolean triggered, boolean error, long elapsedMs) {
        if (!statsEnabled) {
            return;
        }
        totalEvaluations.incrementAndGet();
        totalElapsedMs.addAndGet(elapsedMs);
        if (triggered) totalTriggered.incrementAndGet();
        if (error) totalErrors.incrementAndGet();
        perRuleStats.compute(ruleCode, (k, v) -> {
            if (v == null) v = RuleEngineStats.RuleStat.builder().build();
            v.setExecutions(v.getExecutions() + 1);
            if (triggered) v.setTriggered(v.getTriggered() + 1);
            if (error) v.setErrors(v.getErrors() + 1);
            v.setTotalElapsedMs(v.getTotalElapsedMs() + elapsedMs);
            return v;
        });
    }

    /**
     * 严重度权重
     *
     * @param result 规则结果
     * @return 权重值
     */
    private int severityWeight(RuleResult result) {
        if (result == null || result.getSeverity() == null) return 0;
        return result.getSeverity().getWeight();
    }
}
