package com.njydsz.literule.server.core;

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

import jakarta.annotation.PreDestroy;

import com.njydsz.common.exception.observability.TraceContext;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RuleEnvironment;
import com.njydsz.literule.api.RuleExecutionTrace;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.StatsRecorder;
import com.njydsz.literule.domain.model.ModelInputRegistry;
import com.njydsz.literule.domain.model.ModelInvocationException;
import com.njydsz.literule.server.spi.FactCollectionException;
import com.njydsz.literule.server.spi.FactProviderRegistry;
import com.njydsz.literule.server.spi.RuleActionDispatcher;
import com.njydsz.literule.server.spi.TraceRecorder;

import lombok.extern.slf4j.Slf4j;

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
 * @since 1.0.0
 */
@Slf4j
public class DefaultRuleEngine implements RuleEngine, StatsRecorder {

    /** 已注册规则列表（按优先级排序） */
    private final CopyOnWriteArrayList<Rule> rules = new CopyOnWriteArrayList<>();

    /** 规则索引器（P0-1：大规则量场景索引优化） */
    private final RuleIndexer ruleIndexer = new RuleIndexer();

    /** 是否启用统计（对应 ydsz.literule.statsEnabled 配置） */
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

    /**
     * 模型输入注册表（可选，1.8.0 起 P3-1 规则+模型融合）
     *
     * <p>非 null 且已注册 provider 时，引擎在评估前调用
     * {@link ModelInputRegistry#collectAllModelOutputs} 获取模型输出，
     * 合并到 {@link RuleContext} 的 facts 中（嵌套在 "model" key 下），
     * 使规则表达式可通过 {@code model.<field>} 引用（如 {@code model.riskScore > 0.8}）。
     * 默认 null（向后兼容，不影响现有评估）。
     */
    private volatile ModelInputRegistry modelInputRegistry;

    /**
     * 事实数据提供者注册表（可选，2.1.0 起 P0-2 动态事实采集管道）
     *
     * <p>非 null 且已注册 provider 时，引擎在评估前调用
     * {@link FactProviderRegistry#collectAllFacts} 动态采集事实数据，
     * 合并到 {@link RuleContext} 的 facts 中，使规则表达式可直接引用。
     * 事实采集在模型注入之前执行，采集的事实可供模型 provider 使用。
     * 默认 null（向后兼容，不影响现有评估）。
     */
    private volatile FactProviderRegistry factProviderRegistry;

    /**
     * 规则动作分发器（可选，2.1.0 起 P1-1 规则与消息通知联动）
     *
     * <p>非 null 且已注册 handler 时，引擎在评估完成后调用
     * {@link RuleActionDispatcher#dispatchActions} 分发触发结果，
     * 执行消息通知、工作流触发等后续动作。
     * 默认 null（向后兼容，不影响现有评估）。
     */
    private volatile RuleActionDispatcher actionDispatcher;

    /**
     * 并行规则评估器（可选，2.2.0 起 P2-2 大规则量并行优化）
     *
     * <p>非 null 且候选规则数 ≥ {@link #parallelThreshold} 且无断点时，
     * 引擎将候选规则按互斥组分组并行评估，组内串行保持互斥语义。
     * 并行评估期间通过 {@link TraceContext#withContext} 为每个工作线程传播 MDC traceId。
     * 默认 null（串行评估，向后兼容）。
     */
    private volatile ParallelRuleEvaluator parallelEvaluator;

    /** 并行评估触发阈值（候选规则数 ≥ 此值时启用并行），默认 50 */
    private volatile int parallelThreshold = 50;

    /**
     * 慢规则阈值（毫秒，P2-4）
     *
     * <p>单规则评估耗时超过此值时记录慢规则告警（{@link RuleMetrics#recordSlowRule}）。
     * 0 表示不启用慢规则检测（默认）。
     */
    private volatile long slowRuleThresholdMs = 0L;

    /** 统计计数器 */
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalTriggered = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalElapsedMs = new AtomicLong(0);

    /** 按规则编码的统计明细 */
    private final ConcurrentHashMap<String, RuleEngineStats.RuleStat> perRuleStats = new ConcurrentHashMap<>();

    /** 评估结果缓存（P1-7：可选，通过 setEvaluationResultCache 注入） */
    private volatile EvaluationResultCache evaluationResultCache;

    /**
     * 注册规则到引擎
     *
     * <p>注册流程：
     * <ol>
     *   <li>校验规则非空且 code 非空</li>
     *   <li>移除同编码旧规则（支持热更新覆盖）</li>
     *   <li>二分查找按 priority 升序插入（增量保序，避免全量 sort）</li>
     *   <li>更新规则索引（租户+环境+场景+互斥组+字段倒排）</li>
     *   <li>规则数首次超过 200 时自动启用索引模式</li>
     * </ol>
     *
     * @param rule 待注册规则；为 null 或 code 为 null 时静默跳过
     */
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
        // P1-7：规则变更时清除评估结果缓存
        if (evaluationResultCache != null) {
            evaluationResultCache.clear();
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

    /**
     * 注销指定编码的规则
     *
     * <p>从规则列表和索引中移除指定编码的规则，并同步更新监控指标。
     *
     * @param ruleCode 规则编码；为 null 时静默跳过
     */
    @Override
    public void unregister(String ruleCode) {
        if (ruleCode == null) return;
        rules.removeIf(r -> ruleCode.equals(r.getCode()));
        ruleIndexer.removeFromIndex(ruleCode);
        // P1-6: 注销规则时同步清理该规则的统计数据，避免 perRuleStats Map 无限增长
        perRuleStats.remove(ruleCode);
        // 清理熔断器状态
        if (circuitBreaker != null) {
            circuitBreaker.reset(ruleCode);
        }
        // P1-7：规则变更时清除评估结果缓存
        if (evaluationResultCache != null) {
            evaluationResultCache.clear();
        }
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

    /**
     * 评估上下文中所有匹配规则，返回已触发的规则结果列表
     *
     * <p>执行流程：
     * <ol>
     *   <li>设置 MDC traceId（优先 context.traceId，回退当前线程 MDC，最后生成新值）</li>
     *   <li>（可选）注入外部事实数据到 context（P0-2 动态事实采集）</li>
     *   <li>（可选）注入模型输出到 context（P3-1 规则+模型融合）</li>
     *   <li>索引模式下按租户+环境+场景+互斥组+字段过滤候选规则；
     *       非索引模式线性遍历并逐条过滤</li>
     *   <li>互斥组短路：同组已有规则命中则跳过后续规则</li>
     *   <li>熔断检查：已被熔断的规则跳过评估</li>
     *   <li>（可选）断点调试回调：onBeforeEvaluate</li>
     *   <li>灰度路由：按 canaryRatio 分流到候选版本</li>
     *   <li>执行规则评估（可选超时控制）</li>
     *   <li>记录统计、监控指标、熔断结果、执行轨迹</li>
     *   <li>（可选）断点调试回调：onAfterEvaluate</li>
     * </ol>
     *
     * <p>结果按严重度倒序排列（RED → YELLOW → INFO）。
     * 单规则异常不影响其他规则评估（异常隔离）。
     *
     * <p>评估期间 MDC 中设置 traceId，确保全链路日志可追踪；
     * 评估结束后恢复原有 MDC 状态（由 {@link TraceContext#withContext} 保证）。
     *
     * @param context 规则上下文（包含 facts、场景、租户、环境等）
     * @return 已触发的规则结果列表（按严重度倒序）；无触发时返回空列表
     */
    @Override
    public List<RuleResult> evaluate(RuleContext context) {
        String traceId = resolveTraceId(context);
        return TraceContext.withContext(traceId, () -> doEvaluate(context));
    }

    private List<RuleResult> doEvaluate(RuleContext context) {
        // P1-7：评估结果缓存查询
        if (evaluationResultCache != null) {
            List<RuleResult> cached = evaluationResultCache.get(context);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule] 评估结果缓存命中: scenario={}", context.getScenario());
                }
                return cached;
            }
        }

        // P0-2 动态事实采集：评估前注入外部数据源事实
        context = injectFactsIfNeeded(context);
        // P3-1 规则+模型融合：评估前注入模型输出
        context = injectModelOutputsIfNeeded(context);

        List<RuleResult> triggered = new ArrayList<>();
        // 互斥组：记录本次评估中已命中的互斥组，同组后续规则跳过
        Set<String> triggeredGroups = new HashSet<>();
        String scenario = context.getScenario();
        String contextTenantId = context.getTenantId();
        String contextEnvironment = context.getEnvironment();
        int evaluatedCount = 0;

        // P0-1：使用索引查找候选规则（大规则量场景性能优化）
        // 1.6.0 起索引模式已按租户+环境+场景+互斥组过滤
        List<Rule> candidateRules = ruleIndexer.isIndexEnabled()
                ? ruleIndexer.findCandidates(contextTenantId, contextEnvironment, scenario, triggeredGroups)
                : rules;

        // P1-2：倒排索引第二层过滤，按 facts 字段进一步缩小候选集
        // 仅当倒排索引启用且非空时执行，避免对无字段引用的场景产生开销
        if (ruleIndexer.isIndexEnabled() && ruleIndexer.hasFieldIndex()) {
            Set<String> factKeys = context.getFacts().keySet();
            candidateRules = ruleIndexer.filterByFacts(candidateRules, factKeys);
        }

        // P2-2：并行评估路径（大规则量 + 无断点时自动启用）
        if (shouldUseParallelEvaluation(candidateRules)) {
            return evaluateInParallel(candidateRules, context, scenario);
        }

        // 遍历候选规则（索引模式下已按租户+环境+场景+互斥组+字段过滤）
        for (Rule rule : candidateRules) {
            // 索引未启用时仍需租户、环境、场景过滤
            if (!ruleIndexer.isIndexEnabled()) {
                // 租户隔离（1.5.0）：仅评估与上下文租户匹配的规则
                if (!Objects.equals(rule.getTenantId(), contextTenantId)) {
                    continue;
                }
                // 环境隔离（1.6.0，P1-5）：rule.environment="default" 匹配任何上下文；非 default 必须完全匹配
                if (!environmentMatches(rule, contextEnvironment)) {
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
        // P1-1 规则与消息通知联动：评估完成后分发动作
        if (actionDispatcher != null && !triggered.isEmpty()) {
            actionDispatcher.dispatchActions(triggered, context);
        }
        // P1-7：评估结果写入缓存
        if (evaluationResultCache != null) {
            evaluationResultCache.put(context, triggered);
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

    /**
     * P0-2 动态事实采集：评估前注入外部数据源事实
     *
     * <p>当 {@link #factProviderRegistry} 非 null 且已注册 provider 时：
     * <ol>
     *   <li>调用 {@link FactProviderRegistry#collectAllFacts} 获取外部数据源事实</li>
     *   <li>合并到 facts 中，构建新的 {@link RuleContext}（保留原 scenario/source/traceId/tenantId/environment）</li>
     * </ol>
     *
     * <p>降级策略：
     * <ul>
     *   <li>注册表为空：返回原 context，不影响评估</li>
     *   <li>事实数据为空：返回原 context</li>
     *   <li>抛出 {@link FactCollectionException}（fallbackOnError=false）：异常向上传播中断评估</li>
     * </ul>
     *
     * @param context 原始上下文
     * @return 包含外部事实的新上下文；无需注入时返回原 context
     * @since 2.1.0
     */
    private RuleContext injectFactsIfNeeded(RuleContext context) {
        FactProviderRegistry registry = this.factProviderRegistry;
        if (registry == null || !registry.hasProviders()) {
            return context;
        }
        Map<String, Object> externalFacts;
        try {
            externalFacts = registry.collectAllFacts(context);
        } catch (FactCollectionException e) {
            log.warn("[LiteRule-Fact] 事实采集失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (externalFacts == null || externalFacts.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Fact] 外部事实数据为空，使用原 context 评估");
            }
            return context;
        }
        // 合并到新 facts（原 facts + 外部事实，后者覆盖前者）
        Map<String, Object> mergedFacts = new LinkedHashMap<>(context.getFacts());
        mergedFacts.putAll(externalFacts);
        RuleContext enriched = RuleContext.of(mergedFacts,
                context.getScenario(),
                context.getSource(),
                context.getTraceId(),
                context.getTenantId(),
                context.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Fact] 外部事实已注入: {} 条，合并后 facts 共 {} 条",
                    externalFacts.size(), mergedFacts.size());
        }
        return enriched;
    }

    /**
     * P3-1 规则+模型融合：评估前注入模型输出
     *
     * <p>当 {@link #modelInputRegistry} 非 null 且已注册 provider 时：
     * <ol>
     *   <li>调用 {@link ModelInputRegistry#collectAllModelOutputs} 获取模型输出
     *       （key 带 "model." 前缀，如 "model.riskScore"）</li>
     *   <li>将扁平 key 转换为嵌套结构 {@code {"model": {"riskScore": ..., ...}}}，
     *       以兼容 LiteExpr 表达式 {@code model.riskScore} 的属性访问语法</li>
     *   <li>合并到 facts 中，构建新的 {@link RuleContext}（保留原 scenario/source/traceId/tenantId/environment）</li>
     * </ol>
     *
     * <p>降级策略：
     * <ul>
     *   <li>注册表为空：返回原 context，不影响评估</li>
     *   <li>模型输出为空：返回原 context（规则中引用 model.xxx 的表达式将返回 false）</li>
     *   <li>抛出 {@link ModelInvocationException}（fallbackOnError=false）：异常向上传播中断评估</li>
     * </ul>
     *
     * @param context 原始上下文
     * @return 包含模型输出的新上下文；无需注入时返回原 context
     * @since 1.8.0
     */
    private RuleContext injectModelOutputsIfNeeded(RuleContext context) {
        ModelInputRegistry registry = this.modelInputRegistry;
        if (registry == null || !registry.hasProviders()) {
            return context;
        }
        Map<String, Object> modelOutputs;
        try {
            modelOutputs = registry.collectAllModelOutputs(context);
        } catch (ModelInvocationException e) {
            // fallbackOnError=false 时由注册表抛出，直接传播中断评估
            log.warn("[LiteRule-Model] 模型调用失败（fallbackOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (modelOutputs == null || modelOutputs.isEmpty()) {
            // 模型输出为空（所有 provider 失败或无输出），降级使用原 context
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Model] 模型输出为空，降级为纯规则评估");
            }
            return context;
        }
        // 扁平 key（"model.riskScore"）转换为嵌套结构（{"model": {"riskScore": ...}}）
        Map<String, Object> nestedModel = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : modelOutputs.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(ModelInputRegistry.MODEL_KEY_PREFIX)) {
                nestedModel.put(key.substring(ModelInputRegistry.MODEL_KEY_PREFIX.length()), entry.getValue());
            } else {
                // 非 "model." 前缀的 key 直接保留（兼容扩展场景）
                nestedModel.put(key, entry.getValue());
            }
        }
        if (nestedModel.isEmpty()) {
            return context;
        }
        // 合并到新 facts（保留原 facts + 添加 model 嵌套 Map）
        Map<String, Object> mergedFacts = new LinkedHashMap<>(context.getFacts());
        mergedFacts.put("model", nestedModel);
        RuleContext enriched = RuleContext.of(mergedFacts, context.getScenario(), context.getSource(),
                context.getTraceId(), context.getTenantId(), context.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Model] 模型输出已注入: fields={}", nestedModel.keySet());
        }
        return enriched;
    }

    /**
     * 评估并返回最高严重度的规则结果
     *
     * <p>等价于 {@code evaluate(context).get(0)}，仅在需要 Top-1 结果时使用，
     * 避免调用方手动排序取第一个元素。
     *
     * @param context 规则上下文
     * @return 最高严重度的规则结果；无触发时返回 null
     */
    @Override
    public RuleResult topResult(RuleContext context) {
        List<RuleResult> all = evaluate(context);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 仿真评估（dry-run）：返回全部规则结果（含未触发），不记录统计
     *
     * <p>与 {@link #evaluate} 的区别：
     * <ul>
     *   <li>返回全部规则结果（含 triggered=false 的未触发结果）</li>
     *   <li>不记录执行统计、监控指标和执行轨迹</li>
     *   <li>不执行熔断、灰度、断点调试逻辑</li>
     *   <li>同样遵循租户隔离和环境隔离</li>
     *   <li>同样设置 MDC traceId，确保仿真日志可追踪</li>
     * </ul>
     *
     * <p>适用于规则调试、预检和仿真测试场景。
     *
     * @param context 规则上下文
     * @return 全部匹配规则的结果列表（含未触发）
     */
    @Override
    public List<RuleResult> dryRun(RuleContext context) {
        String traceId = resolveTraceId(context);
        return TraceContext.withContext(traceId, () -> doDryRun(context));
    }

    private List<RuleResult> doDryRun(RuleContext context) {
        // P0-2 动态事实采集：dry-run 同样注入外部数据源事实
        context = injectFactsIfNeeded(context);
        // P3-1 规则+模型融合：dry-run 同样注入模型输出
        context = injectModelOutputsIfNeeded(context);
        List<RuleResult> all = new ArrayList<>();
        String contextTenantId = context.getTenantId();
        String contextEnvironment = context.getEnvironment();
        for (Rule rule : rules) {
            // 租户隔离（1.5.0）：dry-run 同样仅评估与上下文租户匹配的规则
            if (!Objects.equals(rule.getTenantId(), contextTenantId)) {
                continue;
            }
            // 环境隔离（1.6.0，P1-5）：dry-run 同样遵循环境隔离
            if (!environmentMatches(rule, contextEnvironment)) {
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

    /**
     * 解析规则评估的 traceId
     *
     * <p>优先级：
     * <ol>
     *   <li>{@link RuleContext#getTraceId()} — 调用方显式传入的 traceId</li>
     *   <li>当前线程 MDC 中的 traceId — 继承上游链路（如 Web 请求过滤器设置的）</li>
     *   <li>自动生成新 UUID — 确保评估期间日志始终有 traceId</li>
     * </ol>
     *
     * @param context 规则上下文
     * @return 有效 traceId（非 null、非空）
     * @since 2.2.0
     */
    private String resolveTraceId(RuleContext context) {
        String traceId = context.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return TraceContext.extractOrGenerate(null);
    }

    /**
     * 获取当前已注册的全部规则（只读副本）
     *
     * @return 不可修改的规则列表
     */
    @Override
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    /**
     * 获取引擎执行统计快照
     *
     * <p>包含全局统计（总评估次数、总触发次数、总异常次数、总耗时）
     * 和按规则编码的明细统计。统计数据为实时快照，调用后继续累积。
     *
     * @return 引擎统计快照
     */
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
     * @since 1.0.0
     */
    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    /**
     * 获取是否启用统计
     *
     * @return 是否启用
     * @since 1.0.0
     */
    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    /**
     * 将引擎作为统计记录器暴露给编排层使用
     *
     * @return StatsRecorder 实例
     * @since 1.0.0
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
     * 获取规则索引器
     *
     * @return 规则索引器实例
     * @since 2.3.0
     */
    public RuleIndexer getRuleIndexer() {
        return ruleIndexer;
    }

    /**
     * 设置评估结果缓存（P1-7）
     *
     * <p>注入后，evaluate 方法会先查缓存，命中则直接返回；
     * 未命中则执行评估后写入缓存。规则注册/注销/热加载时自动清除缓存。
     *
     * @param cache 评估结果缓存实例
     * @since 2.3.0
     */
    public void setEvaluationResultCache(EvaluationResultCache cache) {
        this.evaluationResultCache = cache;
    }

    /**
     * 获取评估结果缓存
     *
     * @return 评估结果缓存实例；未配置返回 null
     * @since 2.3.0
     */
    public EvaluationResultCache getEvaluationResultCache() {
        return evaluationResultCache;
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
     * 设置模型输入注册表（P3-1 规则+模型融合）
     *
     * <p>注入后，引擎在 {@link #evaluate} 前会调用注册表获取模型输出，
     * 合并到 {@link RuleContext} 的 facts 中。null 表示禁用模型融合（向后兼容）。
     *
     * @param modelInputRegistry 模型输入注册表；null 表示禁用
     * @since 1.8.0
     */
    public void setModelInputRegistry(ModelInputRegistry modelInputRegistry) {
        this.modelInputRegistry = modelInputRegistry;
        if (modelInputRegistry != null) {
            log.info("[LiteRule-Model] 模型输入注册表已注入 (providers={}, timeoutMs={}, fallbackOnError={})",
                    modelInputRegistry.size(), modelInputRegistry.getTimeoutMs(),
                    modelInputRegistry.isFallbackOnError());
        }
    }

    /**
     * 获取模型输入注册表（P3-1）
     *
     * @return 模型输入注册表；未配置返回 null
     * @since 1.8.0
     */
    public ModelInputRegistry getModelInputRegistry() {
        return modelInputRegistry;
    }

    /**
     * 设置事实数据提供者注册表（P0-2 动态事实采集管道）
     *
     * <p>注入后，引擎在 {@link #evaluate} 前会调用注册表动态采集事实数据，
     * 合并到 {@link RuleContext} 的 facts 中。null 表示禁用事实采集（向后兼容）。
     *
     * @param factProviderRegistry 事实数据提供者注册表；null 表示禁用
     * @since 2.1.0
     */
    public void setFactProviderRegistry(FactProviderRegistry factProviderRegistry) {
        this.factProviderRegistry = factProviderRegistry;
        if (factProviderRegistry != null) {
            log.info("[LiteRule-Fact] 事实数据提供者注册表已注入 (providers={}, timeoutMs={}, fallbackOnError={})",
                    factProviderRegistry.size(), factProviderRegistry.getTimeoutMs(),
                    factProviderRegistry.isFallbackOnError());
        }
    }

    /**
     * 获取事实数据提供者注册表（P0-2）
     *
     * @return 事实数据提供者注册表；未配置返回 null
     * @since 2.1.0
     */
    public FactProviderRegistry getFactProviderRegistry() {
        return factProviderRegistry;
    }

    /**
     * 设置规则动作分发器（P1-1 规则与消息通知联动）
     *
     * <p>注入后，引擎在 {@link #evaluate} 完成后会调用分发器，
     * 将触发结果传递给所有已注册的 {@link com.njydsz.literule.server.spi.RuleActionHandler}。
     * null 表示禁用动作分发（向后兼容）。
     *
     * @param actionDispatcher 动作分发器；null 表示禁用
     * @since 2.1.0
     */
    public void setActionDispatcher(RuleActionDispatcher actionDispatcher) {
        this.actionDispatcher = actionDispatcher;
        if (actionDispatcher != null) {
            log.info("[LiteRule-Action] 规则动作分发器已注入 (handlers={})",
                    actionDispatcher.size());
        }
    }

    /**
     * 获取规则动作分发器（P1-1）
     *
     * @return 动作分发器；未配置返回 null
     * @since 2.1.0
     */
    public RuleActionDispatcher getActionDispatcher() {
        return actionDispatcher;
    }

    /**
     * 设置并行规则评估器（P2-2）
     *
     * <p>设置后，当候选规则数 ≥ {@link #parallelThreshold} 且无断点时，
     * 引擎自动切换为并行评估模式。
     *
     * @param parallelEvaluator 并行评估器；null 表示始终串行
     * @since 2.2.0
     */
    public void setParallelEvaluator(ParallelRuleEvaluator parallelEvaluator) {
        this.parallelEvaluator = parallelEvaluator;
        if (parallelEvaluator != null) {
            log.info("[LiteRule-Performance] 并行评估器已注入 (threshold={})", parallelThreshold);
        }
    }

    /**
     * 设置并行评估触发阈值（P2-2）
     *
     * @param threshold 候选规则数阈值；< 1 时视为 1
     * @since 2.2.0
     */
    public void setParallelThreshold(int threshold) {
        this.parallelThreshold = Math.max(1, threshold);
    }

    /**
     * 设置慢规则告警阈值（P2-4）
     *
     * @param thresholdMs 单规则评估耗时阈值（毫秒）；≤ 0 表示关闭慢规则检测
     * @since 2.2.0
     */
    public void setSlowRuleThresholdMs(long thresholdMs) {
        this.slowRuleThresholdMs = thresholdMs;
        if (thresholdMs > 0) {
            log.info("[LiteRule-Performance] 慢规则告警已启用 (threshold={}ms)", thresholdMs);
        }
    }

    /**
     * 判断是否应使用并行评估（P2-2）
     *
     * <p>同时满足以下条件时返回 true：
     * <ul>
     *   <li>并行评估器已注入（{@code parallelEvaluator != null}）</li>
     *   <li>候选规则数 ≥ {@link #parallelThreshold}</li>
     *   <li>无断点调试 Hook（断点要求串行执行，无法并行化）</li>
     * </ul>
     *
     * @param candidateRules 候选规则列表
     * @return true 表示应使用并行评估
     * @since 2.2.0
     */
    private boolean shouldUseParallelEvaluation(List<Rule> candidateRules) {
        return parallelEvaluator != null
                && candidateRules.size() >= parallelThreshold
                && breakpointHook == null;
    }

    /**
     * 并行评估候选规则（P2-2）
     *
     * <p>将候选规则委托给 {@link ParallelRuleEvaluator#evaluateParallel}，
     * 按互斥组分组并行评估。每个工作线程通过 {@link TraceContext#withContext}
     * 传播 MDC traceId，确保并行评估期间日志可追踪。
     *
     * <p>并行路径不支持断点调试（已由 {@link #shouldUseParallelEvaluation} 排除）。
     * 互斥组短路由 {@link ParallelRuleEvaluator} 内部处理。
     *
     * @param candidateRules 候选规则列表
     * @param context        规则上下文
     * @param scenario       业务场景
     * @return 触发的规则结果列表（按严重度倒序）
     * @since 2.2.0
     */
    private List<RuleResult> evaluateInParallel(List<Rule> candidateRules,
                                                 RuleContext context, String scenario) {
        String traceId = TraceContext.getTraceId();
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Parallel] 并行评估: rules={}, threshold={}",
                    candidateRules.size(), parallelThreshold);
        }
        List<RuleResult> results = parallelEvaluator.evaluateParallel(
                candidateRules, context,
                (rule, ctx) -> evaluateSingleRule(rule, ctx, scenario, traceId));
        // 记录本次评估遍历的规则数
        if (metrics != null) {
            metrics.recordEvaluatedRules(candidateRules.size());
        }
        // P1-1 规则与消息通知联动：评估完成后分发动作
        if (actionDispatcher != null && !results.isEmpty()) {
            actionDispatcher.dispatchActions(results, context);
        }
        return results;
    }

    /**
     * 评估单条规则（并行路径专用，P2-2）
     *
     * <p>封装单规则评估的完整逻辑：MDC 传播 → 熔断检查 → 灰度路由 → 超时控制 →
     * 统计/监控/轨迹记录。返回已触发的结果，未触发或被熔断时返回 null。
     *
     * <p>与串行路径的差异：
     * <ul>
     *   <li>不含断点调试（并行模式不支持）</li>
     *   <li>不含互斥组跟踪（由 ParallelRuleEvaluator 处理）</li>
     *   <li>含 MDC traceId 传播（工作线程需要显式设置）</li>
     * </ul>
     *
     * @param rule     规则
     * @param context  规则上下文
     * @param scenario 业务场景
     * @param traceId  MDC traceId（用于工作线程传播）
     * @return 已触发的 RuleResult；未触发/被熔断返回 null
     * @since 2.2.0
     */
    private RuleResult evaluateSingleRule(Rule rule, RuleContext context,
                                           String scenario, String traceId) {
        return TraceContext.withContext(traceId, () -> {
            // 熔断检查
            if (circuitBreaker != null && !circuitBreaker.allowEvaluate(rule.getCode())) {
                log.debug("[LiteRule-Parallel] 规则 {} 已被熔断，跳过评估", rule.getCode());
                return null;
            }
            long start = System.nanoTime();
            RuleResult result = null;
            Exception caughtException = null;
            boolean routedToCanary = false;

            // 灰度路由
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
                }
            }

            // 主版本评估
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
            boolean isError = caughtException != null
                    || (result != null && result.getDescription() != null
                        && result.getDescription().startsWith("评估超时"));
            record(rule.getCode(), isTriggered, isError, elapsed);

            // 熔断器记录
            if (circuitBreaker != null) {
                circuitBreaker.recordResult(rule.getCode(), !isError);
            }
            // 监控指标
            if (metrics != null) {
                try {
                    metrics.recordEvaluation(rule.getCode(), scenario, isTriggered,
                            result != null ? result.getSeverity() : null, isError, elapsed);
                } catch (Exception me) {
                    log.debug("[LiteRule-Parallel] 指标记录失败: {}", me.getMessage());
                }
            }
            if (isError && caughtException != null) {
                log.warn("[LiteRule-Parallel] 规则 {} 评估异常: {}",
                        rule.getCode(), caughtException.getMessage());
            }
            // 异步 Trace
            if (traceRecorder != null && traceRecorder.isEnabled()) {
                try {
                    RuleExecutionTrace trace = buildTrace(context, rule, result, elapsed, caughtException);
                    traceRecorder.record(trace);
                } catch (Exception te) {
                    log.debug("[LiteRule-Parallel] Trace 记录失败: {}", te.getMessage());
                }
            }
            return isTriggered ? result : null;
        });
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
     * 优雅关闭：释放 TraceRecorder、超时执行器与模型注册表资源
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
        if (parallelEvaluator != null) {
            parallelEvaluator.shutdown();
        }
        if (modelInputRegistry != null) {
            modelInputRegistry.destroy();
        }
        if (factProviderRegistry != null) {
            factProviderRegistry.destroy();
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
     * @since 1.0.0
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
     * 判断规则环境是否匹配上下文环境（P1-5 多环境隔离）
     *
     * <p>过滤规则：
     * <ul>
     *   <li>rule.environment 为 null/空 或 {@link RuleEnvironment#DEFAULT "default"} 时，
     *       匹配任何上下文环境（向后兼容）</li>
     *   <li>rule.environment 非 "default" 时，必须与 contextEnvironment 完全匹配</li>
     * </ul>
     *
     * @param rule 规则
     * @param contextEnvironment 上下文环境标识
     * @return true=匹配；false=不匹配
     * @since 1.6.0
     */
    private boolean environmentMatches(Rule rule, String contextEnvironment) {
        String ruleEnv = rule.getEnvironment();
        if (ruleEnv == null || ruleEnv.isBlank() || RuleEnvironment.DEFAULT.equals(ruleEnv)) {
            return true;
        }
        return ruleEnv.equals(contextEnvironment);
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
        // P2-4 慢规则告警：超过阈值时上报监控指标 + WARN 日志
        if (slowRuleThresholdMs > 0 && elapsedMs >= slowRuleThresholdMs) {
            if (metrics != null) {
                metrics.recordSlowRule(ruleCode, elapsedMs, slowRuleThresholdMs);
            }
            log.warn("[LiteRule-SlowRule] rule={}, elapsed={}ms, threshold={}ms",
                    ruleCode, elapsedMs, slowRuleThresholdMs);
        }
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
