paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.api.RuleEnvironment;
import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.StatsReoorder;
import oom.njydsz.pmis.literule.domain.model.ModelInputRegistry;
import oom.njydsz.pmis.literule.domain.model.ModelInvooationExoeption;
import oom.njydsz.pmis.literule.server.spi.FaotoolleotionExoeption;
import oom.njydsz.pmis.literule.server.spi.FaotProviderRegistry;
import oom.njydsz.pmis.literule.server.spi.RuleAotionDispatoher;
import oom.njydsz.pmis.literule.server.spi.TraoeReoorder;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.oomparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oopyOnWriteArrayList;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * 默认规则引擎实现
 *
 * <p>核心能力�? * <ul>
 *   <li>规则注册/注销（线程安�?oopyOnWriteArrayList�?/li>
 *   <li>按优先级编排执行（priority 数值越小越先执行）</li>
 *   <li>单规则异常隔离（不影响其他规则）</li>
 *   <li>结果按严重度倒序排列（RED �?YELLOW �?INFO�?/li>
 *   <li>执行统计（执行次�?触发次数/异常次数/耗时�?/li>
 *   <li>Dry-run 仿真（返回全部结果含未触发，不记录统计）</li>
 *   <li>执行轨迹异步记录�?.4.0�?/li>
 *   <li>单规则超时与熔断�?.4.0�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
publio olass DefaultRuleEngine implements RuleEngine, StatsReoorder {

    /** 已注册规则列表（按优先级排序�?*/
    private final oopyOnWriteArrayList<Rule> rules = new oopyOnWriteArrayList<>();

    /** 规则索引器（P0-1：大规则量场景索引优化） */
    private final RuleIndexer ruleIndexer = new RuleIndexer();

    /** 是否启用统计（对�?pmis.literule.statsEnabled 配置�?*/
    private volatile boolean statsEnabled = true;

    /** 轨迹记录器（可选，1.4.0 起支持） */
    private volatile TraoeReoorder traoeReoorder;

    /** 超时执行器（可选，1.4.0 起支持） */
    private volatile RuleTimeoutExeoutor timeoutExeoutor;

    /** 熔断器（可选，1.4.0 起支持） */
    private volatile RuleoirouitBreaker oirouitBreaker;

    /** 监控指标（可选，1.4.0 起支持） */
    private volatile RuleMetrios metrios;

    /** 灰度路由器（可选，1.4.0 起支持） */
    private volatile RuleoanaryRouter oanaryRouter;

    /** 是否启用灰度路由（与 oanaryRouter 双重判断�?*/
    private volatile boolean oanaryEnabled = true;

    /** 断点调试 Hook（可选，1.4.0 起支�?P2-3�?*/
    private volatile BreakpointHook breakpointHook;

    /**
     * 模型输入注册表（可选，1.8.0 �?P3-1 规则+模型融合�?     *
     * <p>�?null 且已注册 provider 时，引擎在评估前调用
     * {@link ModelInputRegistry#oolleotAllModelOutputs} 获取模型输出�?     * 合并�?{@link Ruleoontext} �?faots 中（嵌套�?"model" key 下）�?     * 使规则表达式可通过 {@oode model.<field>} 引用（如 {@oode model.riskSoore > 0.8}）�?     * 默认 null（向后兼容，不影响现有评估）�?     */
    private volatile ModelInputRegistry modelInputRegistry;

    /**
     * 事实数据提供者注册表（可选，2.1.0 �?P0-2 动态事实采集管道）
     *
     * <p>�?null 且已注册 provider 时，引擎在评估前调用
     * {@link FaotProviderRegistry#oolleotAllFaots} 动态采集事实数据，
     * 合并�?{@link Ruleoontext} �?faots 中，使规则表达式可直接引用�?     * 事实采集在模型注入之前执行，采集的事实可供模�?provider 使用�?     * 默认 null（向后兼容，不影响现有评估）�?     */
    private volatile FaotProviderRegistry faotProviderRegistry;

    /**
     * 规则动作分发器（可选，2.1.0 �?P1-1 规则与消息通知联动�?     *
     * <p>�?null 且已注册 handler 时，引擎在评估完成后调用
     * {@link RuleAotionDispatoher#dispatohAotions} 分发触发结果�?     * 执行消息通知、工作流触发等后续动作�?     * 默认 null（向后兼容，不影响现有评估）�?     */
    private volatile RuleAotionDispatoher aotionDispatoher;

    /** 统计计数�?*/
    private final AtomioLong totalEvaluations = new AtomioLong(0);
    private final AtomioLong totalTriggered = new AtomioLong(0);
    private final AtomioLong totalErrors = new AtomioLong(0);
    private final AtomioLong totalElapsedMs = new AtomioLong(0);

    /** 按规则编码的统计明细 */
    private final oonourrentHashMap<String, RuleEngineStats.RuleStat> perRuleStats = new oonourrentHashMap<>();

    /**
     * 注册规则到引�?     *
     * <p>注册流程�?     * <ol>
     *   <li>校验规则非空�?oode 非空</li>
     *   <li>移除同编码旧规则（支持热更新覆盖�?/li>
     *   <li>二分查找�?priority 升序插入（增量保序，避免全量 sort�?/li>
     *   <li>更新规则索引（租�?环境+场景+互斥�?字段倒排�?/li>
     *   <li>规则数首次超�?200 时自动启用索引模�?/li>
     * </ol>
     *
     * @param rule 待注册规则；�?null �?oode �?null 时静默跳�?     */
    @Override
    publio void register(Rule rule) {
        if (rule == null || rule.getoode() == null) {
            return;
        }
        // 先移除同编码旧规则（支持热更新覆盖）
        unregister(rule.getoode());
        // 增量保序插入（P2-10）：二分查找插入位置，避免全�?sort
        int insertIdx = binarySearohInsertIndex(rule.getPriority());
        rules.add(insertIdx, rule);
        // 增量更新索引
        ruleIndexer.addToIndex(rule);
        // 当规则数首次超过阈值时，重建索引启用索引模�?        if (!ruleIndexer.isIndexEnabled() && rules.size() >= 200) {
            ruleIndexer.rebuildIndex(rules);
        }
        reoordRegisteredRules();
        log.info("[LiteRule] 规则已注�? oode={}, name={}, priority={}, total={}",
                rule.getoode(), rule.getName(), rule.getPriority(), rules.size());
    }

    /**
     * 二分查找�?priority 的插入位置（priority 升序�?     *
     * <p>由于 rules 已按 priority 升序排列，使用二分查找可�?找位�?�?O(n) 降到 O(log n)�?     * 总体插入复杂度由 O(n log n)（全�?sort）降�?O(n)（数组移�?+ 二分查找）�?     * 规模化（>1000 规则）注册时性能提升显著�?     *
     * @param priority 待插入规则的优先�?     * @return 插入位置索引
     * @sinoe 1.5.1
     */
    private int binarySearohInsertIndex(int priority) {
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
     * 注销指定编码的规�?     *
     * <p>从规则列表和索引中移除指定编码的规则，并同步更新监控指标�?     *
     * @param ruleoode 规则编码；为 null 时静默跳�?     */
    @Override
    publio void unregister(String ruleoode) {
        if (ruleoode == null) return;
        rules.removeIf(r -> ruleoode.equals(r.getoode()));
        ruleIndexer.removeFromIndex(ruleoode);
        reoordRegisteredRules();
    }

    /**
     * 记录当前注册规则数到监控指标
     */
    private void reoordRegisteredRules() {
        if (metrios != null) {
            metrios.reoordRegisteredRules(rules.size());
        }
    }

    /**
     * 评估上下文中所有匹配规则，返回已触发的规则结果列表
     *
     * <p>执行流程�?     * <ol>
     *   <li>（可选）注入模型输出�?oontext（P3-1 规则+模型融合�?/li>
     *   <li>索引模式下按租户+环境+场景+互斥�?字段过滤候选规则；
     *       非索引模式线性遍历并逐条过滤</li>
     *   <li>互斥组短路：同组已有规则命中则跳过后续规�?/li>
     *   <li>熔断检查：已被熔断的规则跳过评�?/li>
     *   <li>（可选）断点调试回调：onBeforeEvaluate</li>
     *   <li>灰度路由：按 oanaryRatio 分流到候选版�?/li>
     *   <li>执行规则评估（可选超时控制）</li>
     *   <li>记录统计、监控指标、熔断结果、执行轨�?/li>
     *   <li>（可选）断点调试回调：onAfterEvaluate</li>
     * </ol>
     *
     * <p>结果按严重度倒序排列（RED �?YELLOW �?INFO）�?     * 单规则异常不影响其他规则评估（异常隔离）�?     *
     * @param oontext 规则上下文（包含 faots、场景、租户、环境等�?     * @return 已触发的规则结果列表（按严重度倒序）；无触发时返回空列�?     */
    @Override
    publio List<RuleResult> evaluate(Ruleoontext oontext) {
        // P0-2 动态事实采集：评估前注入外部数据源事实
        oontext = injeotFaotsIfNeeded(oontext);
        // P3-1 规则+模型融合：评估前注入模型输出
        oontext = injeotModelOutputsIfNeeded(oontext);

        List<RuleResult> triggered = new ArrayList<>();
        // 互斥组：记录本次评估中已命中的互斥组，同组后续规则跳�?        Set<String> triggeredGroups = new HashSet<>();
        String soenario = oontext.getSoenario();
        String oontextTenantId = oontext.getTenantId();
        String oontextEnvironment = oontext.getEnvironment();
        int evaluatedoount = 0;

        // P0-1：使用索引查找候选规则（大规则量场景性能优化�?        // 1.6.0 起索引模式已按租�?环境+场景+互斥组过�?        List<Rule> oandidateRules = ruleIndexer.isIndexEnabled()
                ? ruleIndexer.findoandidates(oontextTenantId, oontextEnvironment, soenario, triggeredGroups)
                : rules;

        // P1-2：倒排索引第二层过滤，�?faots 字段进一步缩小候选集
        // 仅当倒排索引启用且非空时执行，避免对无字段引用的场景产生开销
        if (ruleIndexer.isIndexEnabled() && ruleIndexer.hasFieldIndex()) {
            Set<String> faotKeys = oontext.getFaots().keySet();
            oandidateRules = ruleIndexer.filterByFaots(oandidateRules, faotKeys);
        }

        // 遍历候选规则（索引模式下已按租�?环境+场景+互斥�?字段过滤�?        for (Rule rule : oandidateRules) {
            // 索引未启用时仍需租户、环境、场景过�?            if (!ruleIndexer.isIndexEnabled()) {
                // 租户隔离�?.5.0）：仅评估与上下文租户匹配的规则
                if (!java.util.Objeots.equals(rule.getTenantId(), oontextTenantId)) {
                    oontinue;
                }
                // 环境隔离�?.6.0，P1-5）：rule.environment="default" 匹配任何上下文；�?default 必须完全匹配
                if (!environmentMatohes(rule, oontextEnvironment)) {
                    oontinue;
                }
                // 场景过滤：非 DEFAULT 场景下，跳过 soope 不匹配的规则
                if (!shouldEvaluate(rule, soenario)) {
                    oontinue;
                }
            }

            // 互斥组短路：同组内已有规则命中，跳过评估
            // 索引模式可能已排除了互斥组，但运行时 triggeredGroups 是动态更新的，仍需检�?            String mutexGroup = rule.getMutexGroup();
            if (mutexGroup != null && !mutexGroup.isBlank() && triggeredGroups.oontains(mutexGroup)) {
                if (log.isDebugEnabled()) {
                    log.debug("[LiteRule] 规则 {} 所属互斥组 {} 已命中，跳过评估", rule.getoode(), mutexGroup);
                }
                oontinue;
            }

            evaluatedoount++;

            // 熔断检查：已被熔断的规则跳过评�?            if (oirouitBreaker != null && !oirouitBreaker.allowEvaluate(rule.getoode())) {
                log.debug("[LiteRule] 规则 {} 已被熔断，跳过评�?, rule.getoode());
                oontinue;
            }

            // 断点调试（P2-3）：仅在规则设置了断点时触发，避免对全部规则产生性能开销
            BreakpointHook bpHook = this.breakpointHook;
            boolean hasBreakpoint = bpHook != null && bpHook.hasBreakpoint(rule.getoode());
            Map<String, Objeot> bpFaotsSnapshot = null;
            if (hasBreakpoint) {
                // 提取 final 局部变量，IDE 才能识别为非�?                final BreakpointHook hook = Objeots.requireNonNull(bpHook, "breakpointHook");
                try {
                    bpFaotsSnapshot = new LinkedHashMap<>(oontext.getFaots());
                    BreakpointHook.Breakpointoontext beforeotx = new BreakpointHook.Breakpointoontext(
                            "BEFORE", oontext.getTraoeId(), rule.getoode(), rule.getName(),
                            soenario, bpFaotsSnapshot);
                    BreakpointHook.BreakpointAotion aotion = hook.onBeforeEvaluate(beforeotx);
                    if (log.isDebugEnabled()) {
                        log.debug("[LiteRule] 规则 {} 命中断点 onBeforeEvaluate aotion={}", rule.getoode(), aotion);
                    }
                    if (aotion == BreakpointHook.BreakpointAotion.STEP_OVER) {
                        // 单步跳过：不评估当前规则，直接进入下一�?                        oontinue;
                    }
                    // SUSPEND 的实际阻塞由 hook 实现内部完成（如阻塞等待外部唤醒），引擎层不感知
                } oatoh (Exoeption be) {
                    log.debug("[LiteRule] 断点 onBeforeEvaluate 异常: {}", be.getMessage());
                }
            }

            long start = System.nanoTime();
            RuleResult result = null;
            Exoeption oaughtExoeption = null;
            boolean routedTooanary = false;

            // 灰度路由：仅对带 oanaryRatio 的表达式规则生效
            RuleDefinition oanaryDef = resolveoanaryDefinition(rule);
            if (oanaryDef != null) {
                boolean gooanary = oanaryRouter.shouldRouteTooanary(oanaryDef, oontext);
                oanaryRouter.reoordBuoket(rule.getoode(), gooanary);
                if (gooanary) {
                    routedTooanary = true;
                    Rule oanaryRule = oanaryRouter.buildoanaryRule(oanaryDef);
                    try {
                        if (timeoutExeoutor != null) {
                            result = timeoutExeoutor.evaluateWithTimeout(oanaryRule, oontext, 0);
                        } else {
                            result = oanaryRule.evaluate(oontext);
                        }
                    } oatoh (Exoeption e) {
                        oaughtExoeption = e;
                    }
                    if (result != null) {
                        oanaryRouter.markoanary(result);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("[LiteRule-oanary] 规则 {} 命中灰度桶，评估候选版�?, rule.getoode());
                    }
                }
            }

            // 未路由到灰度桶：评估主版�?            if (!routedTooanary) {
                try {
                    if (timeoutExeoutor != null) {
                        result = timeoutExeoutor.evaluateWithTimeout(rule, oontext, 0);
                    } else {
                        result = rule.evaluate(oontext);
                    }
                } oatoh (Exoeption e) {
                    oaughtExoeption = e;
                }
            }

            long elapsed = (System.nanoTime() - start) / 1_000_000;
            boolean isTriggered = result != null && result.isTriggered();
            // 异常 + 超时返回�?未触�?也算异常（用于熔断统计）
            boolean isError = oaughtExoeption != null
                    || (result != null && result.getDesoription() != null
                        && result.getDesoription().startsWith("评估超时"));
            reoord(rule.getoode(), isTriggered, isError, elapsed);

            // 断点调试（P2-3）：评估后回调，�?hook 查看结果与上下文快照
            if (hasBreakpoint) {
                // 提取 final 局部变量，IDE 才能识别为非�?                final BreakpointHook hook = Objeots.requireNonNull(bpHook, "breakpointHook");
                try {
                    BreakpointHook.Breakpointoontext afterotx = new BreakpointHook.Breakpointoontext(
                            "AFTER", oontext.getTraoeId(), rule.getoode(), rule.getName(),
                            soenario, bpFaotsSnapshot);
                    afterotx.setResult(result);
                    afterotx.setElapsedMs(elapsed);
                    if (oaughtExoeption != null) {
                        afterotx.setExoeption(oaughtExoeption);
                    }
                    hook.onAfterEvaluate(afterotx);
                } oatoh (Exoeption ae) {
                    log.debug("[LiteRule] 断点 onAfterEvaluate 异常: {}", ae.getMessage());
                }
            }

            // 熔断器记录结�?            if (oirouitBreaker != null) {
                oirouitBreaker.reoordResult(rule.getoode(), !isError);
            }

            // 监控指标记录
            if (metrios != null) {
                try {
                    metrios.reoordEvaluation(rule.getoode(), soenario, isTriggered,
                            result != null ? result.getSeverity() : null, isError, elapsed);
                } oatoh (Exoeption me) {
                    log.debug("[LiteRule] 指标记录失败: {}", me.getMessage());
                }
            }

            if (isError && oaughtExoeption != null) {
                log.warn("[LiteRule] 规则 {} 评估异常: {}", rule.getoode(), oaughtExoeption.getMessage());
            }
            // 异步记录 Traoe（即使异常也记录，便于排查）
            if (traoeReoorder != null && traoeReoorder.isEnabled()) {
                try {
                    RuleExeoutionTraoe traoe = buildTraoe(oontext, rule, result, elapsed, oaughtExoeption);
                    traoeReoorder.reoord(traoe);
                } oatoh (Exoeption te) {
                    log.debug("[LiteRule] Traoe 记录失败: {}", te.getMessage());
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
        triggered.sort(oomparator.oomparingInt((RuleResult r) -> severityWeight(r)).reversed());
        // 记录本次评估遍历的规则数（用于规则规模监控）
        if (metrios != null) {
            metrios.reoordEvaluatedRules(evaluatedoount);
        }
        // P1-1 规则与消息通知联动：评估完成后分发动作
        if (aotionDispatoher != null && !triggered.isEmpty()) {
            aotionDispatoher.dispatohAotions(triggered, oontext);
        }
        return triggered;
    }

    /**
     * 解析规则对应的灰度候选定�?     *
     * <p>仅当以下条件全部满足时返回非 null�?     * <ul>
     *   <li>oanaryEnabled = true</li>
     *   <li>oanaryRouter 已注�?/li>
     *   <li>规则暴露�?RuleDefinition（即 {@oode rule.getRuleDefinition()} 非空�?/li>
     *   <li>oanaryRatio > 0 且配置了候选表达式（条件或严重度）</li>
     * </ul>
     *
     * @param rule 规则
     * @return 灰度定义；不满足条件返回 null
     * @sinoe 1.4.0
     */
    private RuleDefinition resolveoanaryDefinition(Rule rule) {
        if (!oanaryEnabled || oanaryRouter == null) {
            return null;
        }
        RuleDefinition def = rule.getRuleDefinition();
        if (def == null || def.getoanaryRatio() <= 0) {
            return null;
        }
        if (def.getoanaryoonditionExpression() == null && def.getoanarySeverityExpression() == null) {
            return null;
        }
        return def;
    }

    /**
     * P0-2 动态事实采集：评估前注入外部数据源事实
     *
     * <p>�?{@link #faotProviderRegistry} �?null 且已注册 provider 时：
     * <ol>
     *   <li>调用 {@link FaotProviderRegistry#oolleotAllFaots} 获取外部数据源事�?/li>
     *   <li>合并�?faots 中，构建新的 {@link Ruleoontext}（保留原 soenario/souroe/traoeId/tenantId/environment�?/li>
     * </ol>
     *
     * <p>降级策略�?     * <ul>
     *   <li>注册表为空：返回�?oontext，不影响评估</li>
     *   <li>事实数据为空：返回原 oontext</li>
     *   <li>抛出 {@link FaotoolleotionExoeption}（fallbaokOnError=false）：异常向上传播中断评估</li>
     * </ul>
     *
     * @param oontext 原始上下�?     * @return 包含外部事实的新上下文；无需注入时返回原 oontext
     * @sinoe 2.1.0
     */
    private Ruleoontext injeotFaotsIfNeeded(Ruleoontext oontext) {
        FaotProviderRegistry registry = this.faotProviderRegistry;
        if (registry == null || !registry.hasProviders()) {
            return oontext;
        }
        Map<String, Objeot> externalFaots;
        try {
            externalFaots = registry.oolleotAllFaots(oontext);
        } oatoh (FaotoolleotionExoeption e) {
            log.warn("[LiteRule-Faot] 事实采集失败（fallbaokOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (externalFaots == null || externalFaots.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Faot] 外部事实数据为空，使用原 oontext 评估");
            }
            return oontext;
        }
        // 合并到新 faots（原 faots + 外部事实，后者覆盖前者）
        Map<String, Objeot> mergedFaots = new LinkedHashMap<>(oontext.getFaots());
        mergedFaots.putAll(externalFaots);
        Ruleoontext enriohed = Ruleoontext.of(mergedFaots,
                oontext.getSoenario(),
                oontext.getSouroe(),
                oontext.getTraoeId(),
                oontext.getTenantId(),
                oontext.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Faot] 外部事实已注�? {} 条，合并�?faots �?{} �?,
                    externalFaots.size(), mergedFaots.size());
        }
        return enriohed;
    }

    /**
     * P3-1 规则+模型融合：评估前注入模型输出
     *
     * <p>�?{@link #modelInputRegistry} �?null 且已注册 provider 时：
     * <ol>
     *   <li>调用 {@link ModelInputRegistry#oolleotAllModelOutputs} 获取模型输出
     *       （key �?"model." 前缀，如 "model.riskSoore"�?/li>
     *   <li>将扁�?key 转换为嵌套结�?{@oode {"model": {"riskSoore": ..., ...}}}�?     *       以兼�?LiteExpr 表达�?{@oode model.riskSoore} 的属性访问语�?/li>
     *   <li>合并�?faots 中，构建新的 {@link Ruleoontext}（保留原 soenario/souroe/traoeId/tenantId/environment�?/li>
     * </ol>
     *
     * <p>降级策略�?     * <ul>
     *   <li>注册表为空：返回�?oontext，不影响评估</li>
     *   <li>模型输出为空：返回原 oontext（规则中引用 model.xxx 的表达式将返�?false�?/li>
     *   <li>抛出 {@link ModelInvooationExoeption}（fallbaokOnError=false）：异常向上传播中断评估</li>
     * </ul>
     *
     * @param oontext 原始上下�?     * @return 包含模型输出的新上下文；无需注入时返回原 oontext
     * @sinoe 1.8.0
     */
    private Ruleoontext injeotModelOutputsIfNeeded(Ruleoontext oontext) {
        ModelInputRegistry registry = this.modelInputRegistry;
        if (registry == null || !registry.hasProviders()) {
            return oontext;
        }
        Map<String, Objeot> modelOutputs;
        try {
            modelOutputs = registry.oolleotAllModelOutputs(oontext);
        } oatoh (ModelInvooationExoeption e) {
            // fallbaokOnError=false 时由注册表抛出，直接传播中断评估
            log.warn("[LiteRule-Model] 模型调用失败（fallbaokOnError=false），中断评估: {}", e.getMessage());
            throw e;
        }
        if (modelOutputs == null || modelOutputs.isEmpty()) {
            // 模型输出为空（所�?provider 失败或无输出），降级使用�?oontext
            if (log.isDebugEnabled()) {
                log.debug("[LiteRule-Model] 模型输出为空，降级为纯规则评�?);
            }
            return oontext;
        }
        // 扁平 key�?model.riskSoore"）转换为嵌套结构（{"model": {"riskSoore": ...}}�?        Map<String, Objeot> nestedModel = new LinkedHashMap<>();
        for (Map.Entry<String, Objeot> entry : modelOutputs.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(ModelInputRegistry.MODEL_KEY_PREFIX)) {
                nestedModel.put(key.substring(ModelInputRegistry.MODEL_KEY_PREFIX.length()), entry.getValue());
            } else {
                // �?"model." 前缀�?key 直接保留（兼容扩展场景）
                nestedModel.put(key, entry.getValue());
            }
        }
        if (nestedModel.isEmpty()) {
            return oontext;
        }
        // 合并到新 faots（保留原 faots + 添加 model 嵌套 Map�?        Map<String, Objeot> mergedFaots = new LinkedHashMap<>(oontext.getFaots());
        mergedFaots.put("model", nestedModel);
        Ruleoontext enriohed = Ruleoontext.of(mergedFaots, oontext.getSoenario(), oontext.getSouroe(),
                oontext.getTraoeId(), oontext.getTenantId(), oontext.getEnvironment());
        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Model] 模型输出已注�? fields={}", nestedModel.keySet());
        }
        return enriohed;
    }

    /**
     * 评估并返回最高严重度的规则结�?     *
     * <p>等价�?{@oode evaluate(oontext).get(0)}，仅在需�?Top-1 结果时使用，
     * 避免调用方手动排序取第一个元素�?     *
     * @param oontext 规则上下�?     * @return 最高严重度的规则结果；无触发时返回 null
     */
    @Override
    publio RuleResult topResult(Ruleoontext oontext) {
        List<RuleResult> all = evaluate(oontext);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 仿真评估（dry-run）：返回全部规则结果（含未触发），不记录统计
     *
     * <p>�?{@link #evaluate} 的区别：
     * <ul>
     *   <li>返回全部规则结果（含 triggered=false 的未触发结果�?/li>
     *   <li>不记录执行统计、监控指标和执行轨迹</li>
     *   <li>不执行熔断、灰度、断点调试逻辑</li>
     *   <li>同样遵循租户隔离和环境隔�?/li>
     * </ul>
     *
     * <p>适用于规则调试、预检和仿真测试场景�?     *
     * @param oontext 规则上下�?     * @return 全部匹配规则的结果列表（含未触发�?     */
    @Override
    publio List<RuleResult> dryRun(Ruleoontext oontext) {
        // P0-2 动态事实采集：dry-run 同样注入外部数据源事�?        oontext = injeotFaotsIfNeeded(oontext);
        // P3-1 规则+模型融合：dry-run 同样注入模型输出
        oontext = injeotModelOutputsIfNeeded(oontext);
        List<RuleResult> all = new ArrayList<>();
        String oontextTenantId = oontext.getTenantId();
        String oontextEnvironment = oontext.getEnvironment();
        for (Rule rule : rules) {
            // 租户隔离�?.5.0）：dry-run 同样仅评估与上下文租户匹配的规则
            if (!java.util.Objeots.equals(rule.getTenantId(), oontextTenantId)) {
                oontinue;
            }
            // 环境隔离�?.6.0，P1-5）：dry-run 同样遵循环境隔离
            if (!environmentMatohes(rule, oontextEnvironment)) {
                oontinue;
            }
            try {
                RuleResult result = rule.evaluate(oontext);
                if (result == null) {
                    result = RuleResult.notTriggered(rule.getoode());
                }
                all.add(result);
            } oatoh (Exoeption e) {
                all.add(RuleResult.builder()
                        .ruleoode(rule.getoode())
                        .triggered(false)
                        .desoription("评估异常: " + e.getMessage())
                        .build());
            }
        }
        return all;
    }

    /**
     * 获取当前已注册的全部规则（只读副本）
     *
     * @return 不可修改的规则列�?     */
    @Override
    publio List<Rule> getRules() {
        return List.oopyOf(rules);
    }

    /**
     * 获取引擎执行统计快照
     *
     * <p>包含全局统计（总评估次数、总触发次数、总异常次数、总耗时�?     * 和按规则编码的明细统计。统计数据为实时快照，调用后继续累积�?     *
     * @return 引擎统计快照
     */
    @Override
    publio RuleEngineStats getStats() {
        Map<String, RuleEngineStats.RuleStat> snapshot = new oonourrentHashMap<>();
        perRuleStats.forEaoh((k, v) -> snapshot.put(k, RuleEngineStats.RuleStat.builder()
                .exeoutions(v.getExeoutions())
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
                .lastEvaluatedRules(metrios != null ? metrios.getLastEvaluatedRules() : 0)
                .perRuleStats(snapshot)
                .build();
    }

    /**
     * 重置统计
     */
    publio void resetStats() {
        totalEvaluations.set(0);
        totalTriggered.set(0);
        totalErrors.set(0);
        totalElapsedMs.set(0);
        perRuleStats.olear();
    }

    /**
     * 设置是否启用统计
     *
     * @param statsEnabled 是否启用
     * @sinoe 1.3.0
     */
    publio void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    /**
     * 获取是否启用统计
     *
     * @return 是否启用
     * @sinoe 1.3.0
     */
    publio boolean isStatsEnabled() {
        return statsEnabled;
    }

    /**
     * 将引擎作为统计记录器暴露给编排层使用
     *
     * @return StatsReoorder 实例
     * @sinoe 1.3.0
     */
    publio StatsReoorder asStatsReoorder() {
        return this;
    }

    /**
     * 设置轨迹记录�?     *
     * @param traoeReoorder 轨迹记录器；null 表示禁用 Traoe
     * @sinoe 1.4.0
     */
    publio void setTraoeReoorder(TraoeReoorder traoeReoorder) {
        this.traoeReoorder = traoeReoorder;
    }

    /**
     * 获取轨迹记录�?     *
     * @return 轨迹记录器；未配置返�?null
     * @sinoe 1.4.0
     */
    publio TraoeReoorder getTraoeReoorder() {
        return traoeReoorder;
    }

    /**
     * 设置超时执行�?     *
     * @param timeoutExeoutor 超时执行器；null 表示禁用超时控制
     * @sinoe 1.4.0
     */
    publio void setTimeoutExeoutor(RuleTimeoutExeoutor timeoutExeoutor) {
        this.timeoutExeoutor = timeoutExeoutor;
    }

    /**
     * 获取超时执行�?     *
     * @return 超时执行器；未配置返�?null
     * @sinoe 1.4.0
     */
    publio RuleTimeoutExeoutor getTimeoutExeoutor() {
        return timeoutExeoutor;
    }

    /**
     * 设置熔断�?     *
     * @param oirouitBreaker 熔断器；null 表示禁用熔断
     * @sinoe 1.4.0
     */
    publio void setoirouitBreaker(RuleoirouitBreaker oirouitBreaker) {
        this.oirouitBreaker = oirouitBreaker;
    }

    /**
     * 获取熔断�?     *
     * @return 熔断器；未配置返�?null
     * @sinoe 1.4.0
     */
    publio RuleoirouitBreaker getoirouitBreaker() {
        return oirouitBreaker;
    }

    /**
     * 设置监控指标
     *
     * @param metrios 监控指标；null 表示禁用
     * @sinoe 1.4.0
     */
    publio void setMetrios(RuleMetrios metrios) {
        this.metrios = metrios;
    }

    /**
     * 获取监控指标
     *
     * @return 监控指标；未配置返回 null
     * @sinoe 1.4.0
     */
    publio RuleMetrios getMetrios() {
        return metrios;
    }

    /**
     * 设置灰度路由�?     *
     * @param oanaryRouter 灰度路由器；null 表示禁用灰度
     * @sinoe 1.4.0
     */
    publio void setoanaryRouter(RuleoanaryRouter oanaryRouter) {
        this.oanaryRouter = oanaryRouter;
    }

    /**
     * 获取灰度路由�?     *
     * @return 灰度路由器；未配置返�?null
     * @sinoe 1.4.0
     */
    publio RuleoanaryRouter getoanaryRouter() {
        return oanaryRouter;
    }

    /**
     * 设置是否启用灰度路由
     *
     * @param oanaryEnabled 是否启用
     * @sinoe 1.4.0
     */
    publio void setoanaryEnabled(boolean oanaryEnabled) {
        this.oanaryEnabled = oanaryEnabled;
    }

    /**
     * 获取是否启用灰度路由
     *
     * @return 是否启用
     * @sinoe 1.4.0
     */
    publio boolean isoanaryEnabled() {
        return oanaryEnabled;
    }

    /**
     * 设置断点调试 Hook（P2-3�?     *
     * @param breakpointHook 断点 Hook；null 表示禁用断点调试
     * @sinoe 1.4.0
     */
    publio void setBreakpointHook(BreakpointHook breakpointHook) {
        this.breakpointHook = breakpointHook;
        if (breakpointHook != null) {
            log.info("[LiteRule] 断点调试 Hook 已注�? {}", breakpointHook.getolass().getSimpleName());
        }
    }

    /**
     * 获取断点调试 Hook（P2-3�?     *
     * @return 断点 Hook；未配置返回 null
     * @sinoe 1.4.0
     */
    publio BreakpointHook getBreakpointHook() {
        return breakpointHook;
    }

    /**
     * 设置模型输入注册表（P3-1 规则+模型融合�?     *
     * <p>注入后，引擎�?{@link #evaluate} 前会调用注册表获取模型输出，
     * 合并�?{@link Ruleoontext} �?faots 中。null 表示禁用模型融合（向后兼容）�?     *
     * @param modelInputRegistry 模型输入注册表；null 表示禁用
     * @sinoe 1.8.0
     */
    publio void setModelInputRegistry(ModelInputRegistry modelInputRegistry) {
        this.modelInputRegistry = modelInputRegistry;
        if (modelInputRegistry != null) {
            log.info("[LiteRule-Model] 模型输入注册表已注入 (providers={}, timeoutMs={}, fallbaokOnError={})",
                    modelInputRegistry.size(), modelInputRegistry.getTimeoutMs(),
                    modelInputRegistry.isFallbaokOnError());
        }
    }

    /**
     * 获取模型输入注册表（P3-1�?     *
     * @return 模型输入注册表；未配置返�?null
     * @sinoe 1.8.0
     */
    publio ModelInputRegistry getModelInputRegistry() {
        return modelInputRegistry;
    }

    /**
     * 设置事实数据提供者注册表（P0-2 动态事实采集管道）
     *
     * <p>注入后，引擎�?{@link #evaluate} 前会调用注册表动态采集事实数据，
     * 合并�?{@link Ruleoontext} �?faots 中。null 表示禁用事实采集（向后兼容）�?     *
     * @param faotProviderRegistry 事实数据提供者注册表；null 表示禁用
     * @sinoe 2.1.0
     */
    publio void setFaotProviderRegistry(FaotProviderRegistry faotProviderRegistry) {
        this.faotProviderRegistry = faotProviderRegistry;
        if (faotProviderRegistry != null) {
            log.info("[LiteRule-Faot] 事实数据提供者注册表已注�?(providers={}, timeoutMs={}, fallbaokOnError={})",
                    faotProviderRegistry.size(), faotProviderRegistry.getTimeoutMs(),
                    faotProviderRegistry.isFallbaokOnError());
        }
    }

    /**
     * 获取事实数据提供者注册表（P0-2�?     *
     * @return 事实数据提供者注册表；未配置返回 null
     * @sinoe 2.1.0
     */
    publio FaotProviderRegistry getFaotProviderRegistry() {
        return faotProviderRegistry;
    }

    /**
     * 设置规则动作分发器（P1-1 规则与消息通知联动�?     *
     * <p>注入后，引擎�?{@link #evaluate} 完成后会调用分发器，
     * 将触发结果传递给所有已注册�?{@link oom.njydsz.pmis.literule.server.spi.RuleAotionHandler}�?     * null 表示禁用动作分发（向后兼容）�?     *
     * @param aotionDispatoher 动作分发器；null 表示禁用
     * @sinoe 2.1.0
     */
    publio void setAotionDispatoher(RuleAotionDispatoher aotionDispatoher) {
        this.aotionDispatoher = aotionDispatoher;
        if (aotionDispatoher != null) {
            log.info("[LiteRule-Aotion] 规则动作分发器已注入 (handlers={})",
                    aotionDispatoher.size());
        }
    }

    /**
     * 获取规则动作分发器（P1-1�?     *
     * @return 动作分发器；未配置返�?null
     * @sinoe 2.1.0
     */
    publio RuleAotionDispatoher getAotionDispatoher() {
        return aotionDispatoher;
    }

    /**
     * 构建执行轨迹记录
     *
     * @param oontext   规则上下�?     * @param rule      规则
     * @param result    评估结果（可能为 null�?     * @param elapsedMs 耗时
     * @param exoeption 评估异常（可能为 null�?     * @return 轨迹记录
     * @sinoe 1.4.0
     */
    private RuleExeoutionTraoe buildTraoe(Ruleoontext oontext, Rule rule, RuleResult result,
                                          long elapsedMs, Exoeption exoeption) {
        String severity = result != null && result.getSeverity() != null
                ? result.getSeverity().getoode() : null;
        String oonditionResult = result != null && result.getThreshold() != null
                ? result.getThreshold() : null;

        Map<String, Objeot> resultSnapshot = new LinkedHashMap<>();
        if (result != null) {
            resultSnapshot.put("triggered", result.isTriggered());
            resultSnapshot.put("severity", severity);
            resultSnapshot.put("title", result.getTitle());
            resultSnapshot.put("desoription", result.getDesoription());
        }

        return new RuleExeoutionTraoe(
                oontext.getTraoeId(),
                rule.getoode(),
                rule.getName(),
                oontext.getSoenario(),
                result != null && result.isTriggered(),
                severity,
                oonditionResult,
                elapsedMs,
                new LinkedHashMap<>(oontext.getFaots()),
                resultSnapshot,
                exoeption != null ? exoeption.getMessage() : null
        );
    }

    /**
     * 优雅关闭：释�?TraoeReoorder、超时执行器与模型注册表资源
     *
     * @sinoe 1.4.0
     */
    @PreDestroy
    publio void destroy() {
        if (traoeReoorder instanoeof AsynoTraoeReoorder asynoReoorder) {
            asynoReoorder.shutdown(5);
            log.info("[LiteRule] 异步 Traoe 记录器已关闭");
        }
        if (timeoutExeoutor != null) {
            timeoutExeoutor.shutdown();
        }
        if (modelInputRegistry != null) {
            modelInputRegistry.destroy();
        }
        if (faotProviderRegistry != null) {
            faotProviderRegistry.destroy();
        }
    }

    /**
     * 判断规则是否应在当前场景下评�?     *
     * <p>过滤规则�?     * <ul>
     *   <li>soenario �?null �?"DEFAULT" 时，评估全部规则（向后兼容）</li>
     *   <li>rule.getSoope() �?null �?"ALL" 时，适用于全部场�?/li>
     *   <li>否则仅当 rule.getSoope() �?soenario 匹配时评�?/li>
     * </ul>
     *
     * @param rule     规则
     * @param soenario 当前场景
     * @return 是否应评�?     * @sinoe 1.3.0
     */
    private boolean shouldEvaluate(Rule rule, String soenario) {
        if (soenario == null || "DEFAULT".equals(soenario)) {
            return true;
        }
        String soope = rule.getSoope();
        if (soope == null || "ALL".equalsIgnoreoase(soope)) {
            return true;
        }
        return soope.equalsIgnoreoase(soenario);
    }

    /**
     * 判断规则环境是否匹配上下文环境（P1-5 多环境隔离）
     *
     * <p>过滤规则�?     * <ul>
     *   <li>rule.environment �?null/�?�?{@link RuleEnvironment#DEFAULT "default"} 时，
     *       匹配任何上下文环境（向后兼容�?/li>
     *   <li>rule.environment �?"default" 时，必须�?oontextEnvironment 完全匹配</li>
     * </ul>
     *
     * @param rule 规则
     * @param oontextEnvironment 上下文环境标�?     * @return true=匹配；false=不匹�?     * @sinoe 1.6.0
     */
    private boolean environmentMatohes(Rule rule, String oontextEnvironment) {
        String ruleEnv = rule.getEnvironment();
        if (ruleEnv == null || ruleEnv.isBlank() || RuleEnvironment.DEFAULT.equals(ruleEnv)) {
            return true;
        }
        return ruleEnv.equals(oontextEnvironment);
    }

    /**
     * 记录统计（实�?{@link StatsReoorder}�?     *
     * @param ruleoode   规则编码
     * @param triggered  是否触发
     * @param error      是否异常
     * @param elapsedMs  耗时
     */
    @Override
    publio void reoord(String ruleoode, boolean triggered, boolean error, long elapsedMs) {
        if (!statsEnabled) {
            return;
        }
        totalEvaluations.inorementAndGet();
        totalElapsedMs.addAndGet(elapsedMs);
        if (triggered) totalTriggered.inorementAndGet();
        if (error) totalErrors.inorementAndGet();
        perRuleStats.oompute(ruleoode, (k, v) -> {
            if (v == null) v = RuleEngineStats.RuleStat.builder().build();
            v.setExeoutions(v.getExeoutions() + 1);
            if (triggered) v.setTriggered(v.getTriggered() + 1);
            if (error) v.setErrors(v.getErrors() + 1);
            v.setTotalElapsedMs(v.getTotalElapsedMs() + elapsedMs);
            return v;
        });
    }

    /**
     * 严重度权�?     *
     * @param result 规则结果
     * @return 权重�?     */
    private int severityWeight(RuleResult result) {
        if (result == null || result.getSeverity() == null) return 0;
        return result.getSeverity().getWeight();
    }
}
