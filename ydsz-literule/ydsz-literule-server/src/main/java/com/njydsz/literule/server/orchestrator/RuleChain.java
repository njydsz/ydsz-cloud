package com.njydsz.literule.server.orchestrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.RuleSeverity;
import com.njydsz.literule.api.StatsRecorder;
import com.njydsz.literule.api.expr.ExpressionEvaluator;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则链，支持 THEN/IF/ELIF/SWITCH/WHEN/FOR/WHILE/BREAK 编排
 *
 * <p>规则编排的核心载体，按 {@link RuleChainType} 决定执行语义：
 * <ul>
 *   <li><b>THEN</b> - 顺序执行：节点依次串行执行，收集触发结果</li>
 *   <li><b>WHEN</b> - 并行执行：基于 {@link CompletableFuture#supplyAsync} 并发执行全部节点，收集触发结果</li>
 *   <li><b>IF</b> - 条件执行：先对 {@link #conditionExpression} 求值，为 true 才执行动作规则</li>
 *   <li><b>ELIF</b> - 多分支条件：依次求值多个条件表达式，执行第一个匹配的分支，无匹配则执行 else 分支</li>
 *   <li><b>SWITCH</b> - 分支选择：从 {@link RuleContext#getFacts()} 中按 {@link #branchKey} 取分支 key，
 *       执行 {@link #branchMap} 中对应的分支节点</li>
 *   <li><b>FOR</b> - 循环执行：遍历集合中的每个元素，将其作为上下文变量注入后执行规则链</li>
 *   <li><b>WHILE</b> - 条件循环：条件表达式为 true 时持续执行规则链，支持最大迭代次数限制</li>
 *   <li><b>BREAK</b> - 终止执行：在循环中遇到 BREAK 链时终止当前循环</li>
 * </ul>
 *
 * <p>使用静态工厂方法构建：
 * <pre>
 *   RuleChain.then(r1, r2, r3)                       // 顺序执行
 *   RuleChain.when(r1, r2)                           // 并行执行
 *   RuleChain.ifThen("amount &gt; 1000", actionRule)   // 条件执行
 *   RuleChain.elif(branches, elseRule)               // 多分支条件
 *   RuleChain.switchOn("type", branches)             // 分支选择
 *   RuleChain.forEach("items", "item", actionRule)   // 循环执行
 *   RuleChain.whileDo("amount &gt; 0", actionRule)     // 条件循环
 *   RuleChain.breakChain()                           // 终止执行
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleChain {

    /**
     * WHEN 链专用守护线程池（P1-4）
     *
     * <p>当调用方未提供 parallelExecutor 时，使用此线程池替代 ForkJoinPool.commonPool()，
     * 避免 WHEN 链并行任务污染公共线程池导致其他组件线程饥饿。
     * 使用守护线程确保不阻止 JVM 退出。
     *
     * <p>P1-T4：提供 {@link #shutdownFallbackExecutor()} 方法用于优雅关闭，
     * 建议在应用关闭时（如 @PreDestroy 方法中）调用。
     */
    private static final ExecutorService WHEN_FALLBACK_EXECUTOR =
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    r -> {
                        Thread t = new Thread(r, "literule-when-fallback");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * 优雅关闭 WHEN 回退线程池（P1-T4）
     *
     * <p>建议在 Spring 容器关闭时调用（如通过 @PreDestroy 方法）。
     * 由于线程池使用守护线程，即使不显式关闭也不会阻止 JVM 退出，
     * 但显式关闭可以更快速地释放线程资源。
     */
    public static void shutdownFallbackExecutor() {
        if (!WHEN_FALLBACK_EXECUTOR.isShutdown()) {
            WHEN_FALLBACK_EXECUTOR.shutdown();
            log.info("[LiteRule-Chain] WHEN 回退线程池已关闭");
        }
    }

    /** 链类型（THEN/WHEN/IF/SWITCH） */
    private final RuleChainType chainType;

    /** 节点列表（THEN/WHEN 使用） */
    private final List<RuleNode> nodes;

    /** 条件表达式（IF 使用） */
    private final String conditionExpression;

    /** 分支 key 取值字段名（SWITCH 使用） */
    private final String branchKey;

    /** 分支映射：分支 key -> 分支节点（SWITCH 使用） */
    private final Map<String, RuleNode> branchMap;

    /** SWITCH 默认分支节点（未命中任何分支时执行，可选） */
    private final RuleNode defaultBranch;

    /** 多分支条件列表（ELIF 使用）：每个元素为 [条件表达式, 动作节点] 对 */
    private final List<Map.Entry<String, RuleNode>> elifBranches;

    /** ELSE 分支节点（ELIF 使用，可选） */
    private final RuleNode elseNode;

    /** 遍历集合的表达式（FOR 使用），如 "items" 表示从 facts 中取 items 列表 */
    private final String iterableExpression;

    /** 迭代变量名（FOR 使用），如 "item"，每个元素会以该变量名注入上下文 */
    private final String iterationVar;

    /** WHILE 最大迭代次数，防止死循环，默认 100 */
    private final int maxIterations;

    /** 主节点（CATCH/RETRY 使用）：执行的主体节点 */
    private final RuleNode primaryNode;

    /** 补偿/回滚节点（CATCH/RETRY 使用）：异常或重试耗尽时执行 */
    private final RuleNode catchNode;

    /** 最大重试次数（RETRY 使用，不含首次执行） */
    private final int maxRetries;

    /** 重试间隔（毫秒，RETRY 使用） */
    private final long retryIntervalMs;

    /** 节点级超时（毫秒，0=不超时） */
    private final long nodeTimeoutMs;

    /** 节点级重试次数（0=不重试） */
    private final int nodeRetries;

    /**
     * 私有构造，统一通过工厂方法创建
     *
     * @param chainType           链类型
     * @param nodes               节点列表
     * @param conditionExpression 条件表达式
     * @param branchKey           分支 key 字段名
     * @param branchMap           分支映射
     * @param elifBranches        多分支条件列表
     * @param elseNode            ELSE 分支节点
     * @param iterableExpression  遍历集合表达式
     * @param iterationVar        迭代变量名
     * @param maxIterations       最大迭代次数
     * @param primaryNode         主节点（CATCH/RETRY）
     * @param catchNode           补偿/回滚节点（CATCH/RETRY）
     * @param maxRetries          最大重试次数（RETRY）
     * @param retryIntervalMs     重试间隔毫秒（RETRY）
     * @param nodeTimeoutMs       节点级超时毫秒
     * @param nodeRetries         节点级重试次数
     */
    private RuleChain(RuleChainType chainType, List<RuleNode> nodes, String conditionExpression,
                      String branchKey, Map<String, RuleNode> branchMap, RuleNode defaultBranch,
                      List<Map.Entry<String, RuleNode>> elifBranches, RuleNode elseNode,
                      String iterableExpression, String iterationVar,
                      int maxIterations,
                      RuleNode primaryNode, RuleNode catchNode,
                      int maxRetries, long retryIntervalMs,
                      long nodeTimeoutMs, int nodeRetries) {
        this.chainType = chainType;
        this.nodes = nodes;
        this.conditionExpression = conditionExpression;
        this.branchKey = branchKey;
        this.branchMap = branchMap;
        this.defaultBranch = defaultBranch;
        this.elifBranches = elifBranches;
        this.elseNode = elseNode;
        this.iterableExpression = iterableExpression;
        this.iterationVar = iterationVar;
        this.maxIterations = maxIterations;
        this.primaryNode = primaryNode;
        this.catchNode = catchNode;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
        this.nodeTimeoutMs = nodeTimeoutMs;
        this.nodeRetries = nodeRetries;
    }

    /**
     * 向后兼容的私有构造（无 CATCH/RETRY 字段）
     */
    private RuleChain(RuleChainType chainType, List<RuleNode> nodes, String conditionExpression,
                      String branchKey, Map<String, RuleNode> branchMap, RuleNode defaultBranch,
                      List<Map.Entry<String, RuleNode>> elifBranches, RuleNode elseNode,
                      String iterableExpression, String iterationVar,
                      int maxIterations) {
        this(chainType, nodes, conditionExpression, branchKey, branchMap, defaultBranch,
                elifBranches, elseNode, iterableExpression, iterationVar, maxIterations,
                null, null, 0, 0, 0, 0);
    }

    /**
     * 构建顺序执行链（THEN）
     *
     * @param rules 规则数组（按传入顺序执行）
     * @return THEN 类型规则链
     */
    public static RuleChain then(Rule... rules) {
        Objects.requireNonNull(rules, "rules 不能为 null");
        List<RuleNode> nodeList = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule != null) {
                nodeList.add(RuleNode.of(rule));
            }
        }
        return new RuleChain(RuleChainType.THEN,
                Collections.unmodifiableList(nodeList), null, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建并行执行链（WHEN）
     *
     * @param rules 规则数组（并发执行）
     * @return WHEN 类型规则链
     */
    public static RuleChain when(Rule... rules) {
        Objects.requireNonNull(rules, "rules 不能为 null");
        List<RuleNode> nodeList = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule != null) {
                nodeList.add(RuleNode.of(rule));
            }
        }
        return new RuleChain(RuleChainType.WHEN,
                Collections.unmodifiableList(nodeList), null, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建条件执行链（IF）
     *
     * @param conditionExpression 条件表达式（求值为 true 才执行 actionRule）
     * @param actionRule          动作规则
     * @return IF 类型规则链
     */
    public static RuleChain ifThen(String conditionExpression, Rule actionRule) {
        Objects.requireNonNull(conditionExpression, "conditionExpression 不能为 null");
        Objects.requireNonNull(actionRule, "actionRule 不能为 null");
        List<RuleNode> nodeList = Collections.singletonList(RuleNode.of(actionRule));
        return new RuleChain(RuleChainType.IF,
                nodeList, conditionExpression, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建分支选择链（SWITCH）
     *
     * @param branchKey 分支 key 字段名（从上下文事实中取值）
     * @param branches  分支映射：分支 key -&gt; 分支规则
     * @return SWITCH 类型规则链
     */
    public static RuleChain switchOn(String branchKey, Map<String, Rule> branches) {
        return switchOn(branchKey, branches, null);
    }

    /**
     * 构建分支选择链（SWITCH），指定默认分支
     *
     * @param branchKey   分支 key 字段名（从上下文事实中取值）
     * @param branches    分支映射：分支 key -&gt; 分支规则
     * @param defaultRule 默认分支规则（未命中任何分支时执行，可为 null）
     * @return SWITCH 类型规则链
     * @since 1.0.0
     */
    public static RuleChain switchOn(String branchKey, Map<String, Rule> branches, Rule defaultRule) {
        Objects.requireNonNull(branchKey, "branchKey 不能为 null");
        Objects.requireNonNull(branches, "branches 不能为 null");
        Map<String, RuleNode> nodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, Rule> entry : branches.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                nodeMap.put(entry.getKey(), RuleNode.of(entry.getValue()));
            }
        }
        RuleNode defaultNode = defaultRule != null ? RuleNode.of(defaultRule) : null;
        return new RuleChain(RuleChainType.SWITCH,
                null, null, branchKey, Collections.unmodifiableMap(nodeMap), defaultNode,
                null, null, null, null, 0);
    }

    /**
     * 构建多分支条件链（ELIF）
     *
     * @param branches 条件-动作映射（按顺序求值，匹配第一个为 true 的分支）
     * @param elseRule 默认分支规则（可选，所有条件都不匹配时执行）
     * @return ELIF 类型规则链
     */
    public static RuleChain elif(Map<String, Rule> branches, Rule elseRule) {
        Objects.requireNonNull(branches, "branches 不能为 null");
        List<Map.Entry<String, RuleNode>> branchList = new ArrayList<>();
        for (Map.Entry<String, Rule> entry : branches.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                branchList.add(Map.entry(entry.getKey(), RuleNode.of(entry.getValue())));
            }
        }
        RuleNode elseNode = elseRule != null ? RuleNode.of(elseRule) : null;
        return new RuleChain(RuleChainType.ELIF,
                null, null, null, null, null, Collections.unmodifiableList(branchList), elseNode, null, null, 0);
    }

    /**
     * 构建循环执行链（FOR）
     *
     * @param iterableExpression 遍历集合的表达式（从 facts 中取值），如 "items"
     * @param iterationVar       迭代变量名，每个元素会以该变量名注入上下文，如 "item"
     * @param actionRule         对每个元素执行的规则
     * @return FOR 类型规则链
     */
    public static RuleChain forEach(String iterableExpression, String iterationVar, Rule actionRule) {
        Objects.requireNonNull(iterableExpression, "iterableExpression 不能为 null");
        Objects.requireNonNull(iterationVar, "iterationVar 不能为 null");
        Objects.requireNonNull(actionRule, "actionRule 不能为 null");
        List<RuleNode> nodeList = Collections.singletonList(RuleNode.of(actionRule));
        return new RuleChain(RuleChainType.FOR,
                nodeList, null, null, null, null, null, null, iterableExpression, iterationVar, 0);
    }

    /**
     * 构建条件循环链（WHILE）
     *
     * @param conditionExpression 循环条件表达式，为 true 时持续执行
     * @param actionRule          对每次迭代执行的规则
     * @return WHILE 类型规则链
     */
    public static RuleChain whileDo(String conditionExpression, Rule actionRule) {
        return whileDo(conditionExpression, actionRule, 100);
    }

    /**
     * 构建条件循环链（WHILE），指定最大迭代次数
     *
     * @param conditionExpression 循环条件表达式
     * @param actionRule          对每次迭代执行的规则
     * @param maxIterations       最大迭代次数（防止死循环）
     * @return WHILE 类型规则链
     */
    public static RuleChain whileDo(String conditionExpression, Rule actionRule, int maxIterations) {
        Objects.requireNonNull(conditionExpression, "conditionExpression 不能为 null");
        Objects.requireNonNull(actionRule, "actionRule 不能为 null");
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations 必须 > 0");
        }
        List<RuleNode> nodeList = Collections.singletonList(RuleNode.of(actionRule));
        return new RuleChain(RuleChainType.WHILE,
                nodeList, conditionExpression, null, null, null, null, null, null, null, maxIterations);
    }

    /**
     * 构建终止执行链（BREAK）
     *
     * <p>BREAK 链本身不执行任何规则，仅作为循环中的终止信号。
     * 在 FOR/WHILE 循环中遇到 BREAK 链时，立即终止当前循环。
     *
     * @return BREAK 类型规则链
     */
    public static RuleChain breakChain() {
        return new RuleChain(RuleChainType.BREAK,
                null, null, null, null, null, null, null, null, null, 0);
    }

    // ============================== 编排容错工厂方法 (2.0.0) ==============================

    /**
     * 构建异常捕获链（CATCH，2.0.0）
     *
     * <p>执行 {@code primaryRule}，若抛出异常则执行 {@code catchRule} 进行补偿。
     * 补偿规则可以访问上下文中的事实数据，用于执行回滚操作或记录告警。
     *
     * <pre>
     *   RuleChain.catchThen(mainRule, compensationRule)
     * </pre>
     *
     * @param primaryRule 主规则（正常执行）
     * @param catchRule   补偿规则（异常时执行，可为 null 表示仅记录日志不补偿）
     * @return CATCH 类型规则链
     * @since 1.0.0
     */
    public static RuleChain catchThen(Rule primaryRule, Rule catchRule) {
        Objects.requireNonNull(primaryRule, "primaryRule 不能为 null");
        RuleNode primaryNode = RuleNode.of(primaryRule);
        RuleNode catchNode = catchRule != null ? RuleNode.of(catchRule) : null;
        return new RuleChain(RuleChainType.CATCH,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, catchNode, 0, 0, 0, 0);
    }

    /**
     * 构建异常捕获链（CATCH，2.0.0），主节点和补偿节点为子链
     *
     * @param primaryChain 主子链
     * @param catchChain   补偿子链（可为 null）
     * @return CATCH 类型规则链
     * @since 1.0.0
     */
    public static RuleChain catchThen(RuleChain primaryChain, RuleChain catchChain) {
        Objects.requireNonNull(primaryChain, "primaryChain 不能为 null");
        RuleNode primaryNode = RuleNode.of(primaryChain);
        RuleNode catchNode = catchChain != null ? RuleNode.of(catchChain) : null;
        return new RuleChain(RuleChainType.CATCH,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, catchNode, 0, 0, 0, 0);
    }

    /**
     * 构建重试链（RETRY，2.0.0）
     *
     * <p>执行 {@code primaryRule}，失败时自动重试，最多重试 {@code maxRetries} 次
     * （不含首次执行），每次重试间隔 {@code retryIntervalMs} 毫秒。
     * 全部重试耗尽后若仍失败，执行 {@code rollbackRule} 回滚补偿（如果提供）。
     *
     * <pre>
     *   // 最多执行 4 次（1 + 3 重试），间隔 500ms
     *   RuleChain.retryThen(mainRule, 3, 500, rollbackRule)
     * </pre>
     *
     * @param primaryRule      主规则
     * @param maxRetries       最大重试次数（不含首次执行，建议 1-5）
     * @param retryIntervalMs  重试间隔（毫秒）
     * @param rollbackRule     回滚规则（全部重试失败后执行，可为 null）
     * @return RETRY 类型规则链
     * @since 1.0.0
     */
    public static RuleChain retryThen(Rule primaryRule, int maxRetries, long retryIntervalMs, Rule rollbackRule) {
        Objects.requireNonNull(primaryRule, "primaryRule 不能为 null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
        RuleNode primaryNode = RuleNode.of(primaryRule);
        RuleNode catchNode = rollbackRule != null ? RuleNode.of(rollbackRule) : null;
        return new RuleChain(RuleChainType.RETRY,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, catchNode, maxRetries, retryIntervalMs, 0, 0);
    }

    /**
     * 构建重试链（RETRY，2.0.0），主节点为子链
     *
     * @param primaryChain     主子链
     * @param maxRetries       最大重试次数
     * @param retryIntervalMs  重试间隔（毫秒）
     * @param rollbackChain    回滚子链（可为 null）
     * @return RETRY 类型规则链
     * @since 1.0.0
     */
    public static RuleChain retryThen(RuleChain primaryChain, int maxRetries, long retryIntervalMs, RuleChain rollbackChain) {
        Objects.requireNonNull(primaryChain, "primaryChain 不能为 null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
        RuleNode primaryNode = RuleNode.of(primaryChain);
        RuleNode catchNode = rollbackChain != null ? RuleNode.of(rollbackChain) : null;
        return new RuleChain(RuleChainType.RETRY,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, catchNode, maxRetries, retryIntervalMs, 0, 0);
    }

    /**
     * 评估规则链
     *
     * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表。
     * 单节点异常将被隔离（记录日志并跳过），不影响其他节点。
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器（IF/SWITCH 嵌套链需要）
     * @return 已触发的规则结果列表；无触发返回空列表
     */
    public List<RuleResult> evaluate(RuleContext context, ExpressionEvaluator evaluator) {
        return evaluate(context, evaluator, null);
    }

    /**
     * 评估规则链（带统计记录）
     *
     * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表。
     * 若提供 {@link StatsRecorder}，将对 SINGLE 节点的规则评估记录执行统计。
     *
     * @param context       规则上下文
     * @param evaluator     表达式求值器（IF/SWITCH 嵌套链需要）
     * @param statsRecorder 统计记录器（可为 null，表示不记录统计）
     * @return 已触发的规则结果列表；无触发返回空列表
     * @since 1.0.0
     */
    public List<RuleResult> evaluate(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        return evaluate(context, evaluator, statsRecorder, null, 0);
    }

    /**
     * 评估规则链（带统计记录、并行线程池和超时控制）
     *
     * @param context          规则上下文
     * @param evaluator        表达式求值器
     * @param statsRecorder    统计记录器（可为 null）
     * @param parallelExecutor 并行执行线程池（WHEN 链使用，null 则用 ForkJoinPool）
     * @param timeoutMs        超时毫秒（0=不超时）
     * @return 已触发的规则结果列表
     * @since 1.0.0
     */
    public List<RuleResult> evaluate(RuleContext context, ExpressionEvaluator evaluator,
                                     StatsRecorder statsRecorder,
                                     ExecutorService parallelExecutor,
                                     long timeoutMs) {
        Objects.requireNonNull(context, "context 不能为 null");
        // 并行参数通过方法栈传递（不再使用 transient 实例字段，消除线程安全隐患）
        return switch (chainType) {
            case THEN -> evaluateThen(context, evaluator, statsRecorder);
            case WHEN -> evaluateWhen(context, evaluator, statsRecorder, parallelExecutor, timeoutMs);
            case IF -> evaluateIf(context, evaluator, statsRecorder);
            case ELIF -> evaluateElif(context, evaluator, statsRecorder);
            case SWITCH -> evaluateSwitch(context, evaluator, statsRecorder);
            case FOR -> evaluateFor(context, evaluator, statsRecorder);
            case WHILE -> evaluateWhile(context, evaluator, statsRecorder);
            case BREAK -> evaluateBreak(context, evaluator);
            case CATCH -> evaluateCatch(context, evaluator, statsRecorder);
            case RETRY -> evaluateRetry(context, evaluator, statsRecorder);
        };
    }

    /**
     * THEN 语义：顺序执行全部节点，收集触发结果
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateThen(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null) {
            return results;
        }
        for (RuleNode node : nodes) {
            Response.addAll(evaluateNode(node, context, evaluator, statsRecorder));
        }
        return results;
    }

    /**
     * WHEN 语义：并行执行全部节点，收集触发结果
     *
     * <p>使用 {@link CompletableFuture#supplyAsync} 并发执行，不额外创建线程池。
     * 各节点结果通过线程安全的 {@link CopyOnWriteArrayList} 收集后合并。
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateWhen(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder,
                                           ExecutorService parallelExecutor, long timeoutMs) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return results;
        }
        // 使用传入的并行参数（不再依赖 transient 实例字段）
        // P1-4: 当调用方未提供 executor 时，使用专用守护线程池而非 ForkJoinPool.commonPool()
        ExecutorService executor = parallelExecutor != null ? parallelExecutor : WHEN_FALLBACK_EXECUTOR;
        // 并行执行所有节点
        List<CompletableFuture<List<RuleResult>>> futures = new ArrayList<>();
        for (RuleNode node : nodes) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> evaluateNode(node, context, evaluator, statsRecorder), executor));
        }
        // 等待全部完成（带超时控制）
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            if (timeoutMs > 0) {
                allOf.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                allOf.join();
            }
        } catch (TimeoutException e) {
            log.warn("[LiteRule-Chain] WHEN 并行执行超时: timeoutMs={}", timeoutMs);
            // 超时后取消未完成的任务
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.warn("[LiteRule-Chain] WHEN 并行执行异常: {}", e.getMessage());
        }
        // 合并已完成的结果
        for (CompletableFuture<List<RuleResult>> future : futures) {
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    Response.addAll(future.join());
                } catch (Exception ignored) {
                    // 跳过异常结果
                }
            }
        }
        return results;
    }

    /**
     * IF 语义：先用 evaluator 求值 conditionExpression，true 才执行 actionRule
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateIf(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-Chain] IF 链缺少 ExpressionEvaluator，跳过求值");
            return results;
        }
        boolean matched = evaluator.evalBoolean(conditionExpression, context);
        log.debug("[LiteRule-Chain] IF 条件求值: expr='{}', result={}", conditionExpression, matched);
        if (!matched) {
            return results;
        }
        if (nodes != null) {
            for (RuleNode node : nodes) {
                Response.addAll(evaluateNode(node, context, evaluator, statsRecorder));
            }
        }
        return results;
    }

    /**
     * SWITCH 语义：从 context.getFacts().get(branchKey) 取分支 key，执行对应分支；
     * 未命中任何分支时执行 defaultBranch（如果存在）
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateSwitch(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (branchMap == null || branchKey == null) {
            return results;
        }
        Object key = context.getFacts().get(branchKey);
        log.debug("[LiteRule-Chain] SWITCH 分支选择: branchKey='{}', value={}", branchKey, key);
        if (key == null) {
            log.warn("[LiteRule-Chain] SWITCH 分支 key '{}' 在上下文中不存在", branchKey);
            // key 不存在时走默认分支
            if (defaultBranch != null) {
                Response.addAll(evaluateNode(defaultBranch, context, evaluator, statsRecorder));
            }
            return results;
        }
        RuleNode branch = branchMap.get(String.valueOf(key));
        if (branch == null) {
            log.warn("[LiteRule-Chain] SWITCH 未匹配到分支: key='{}', 执行默认分支", key);
            // 未匹配到分支时走默认分支
            if (defaultBranch != null) {
                Response.addAll(evaluateNode(defaultBranch, context, evaluator, statsRecorder));
            }
            return results;
        }
        Response.addAll(evaluateNode(branch, context, evaluator, statsRecorder));
        return results;
    }

    /**
     * ELIF 语义：依次求值多个条件表达式，执行第一个匹配的分支；无匹配则执行 else 分支
     */
    private List<RuleResult> evaluateElif(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-Chain] ELIF 链缺少 ExpressionEvaluator，跳过求值");
            return results;
        }
        if (elifBranches != null) {
            for (Map.Entry<String, RuleNode> branch : elifBranches) {
                try {
                    boolean matched = evaluator.evalBoolean(branch.getKey(), context);
                    if (matched) {
                        Response.addAll(evaluateNode(branch.getValue(), context, evaluator, statsRecorder));
                        return results;
                    }
                } catch (Exception e) {
                    log.warn("[LiteRule-Chain] ELIF 分支求值异常: expr='{}', error={}", branch.getKey(), e.getMessage());
                }
            }
        }
        // 所有条件都不匹配，执行 else 分支
        if (elseNode != null) {
            Response.addAll(evaluateNode(elseNode, context, evaluator, statsRecorder));
        }
        return results;
    }

    /**
     * FOR 语义：遍历集合中的每个元素，注入迭代变量后执行规则链
     *
     * <p>修复：使用可变副本注入迭代变量，避免对不可变 facts Map 调用 put/remove 抛出异常。
     *
     * @since 1.0.0 修复 FOR 循环不可变 Map bug
     */
    private List<RuleResult> evaluateFor(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (iterableExpression == null || iterationVar == null) {
            return results;
        }
        Object iterable = context.getFacts().get(iterableExpression);
        if (!(iterable instanceof Iterable<?> iterableObj)) {
            log.warn("[LiteRule-Chain] FOR 遍历表达式 '{}' 不是可迭代对象: class={}", iterableExpression,
                    iterable != null ? iterable.getClass().getName() : "null");
            return results;
        }
        int count = 0;
        for (Object item : iterableObj) {
            // 创建可变副本并注入迭代变量（避免修改不可变的 facts Map）
            Map<String, Object> mutableFacts = new HashMap<>(context.getFacts());
            mutableFacts.put(iterationVar, item);
            RuleContext iterContext = RuleContext.of(mutableFacts, context.getScenario(),
                    context.getSource(), context.getTraceId());
            // 执行规则链
            if (nodes != null) {
                for (RuleNode node : nodes) {
                    List<RuleResult> nodeResults = evaluateNode(node, iterContext, evaluator, statsRecorder);
                    // 检查是否遇到 BREAK
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && RuleResult.BREAK_CODE.equals(r.getRuleCode())) {
                            log.debug("[LiteRule-Chain] FOR 循环遇到 BREAK，终止迭代");
                            return results;
                        }
                    }
                    Response.addAll(nodeResults);
                }
            }
            count++;
        }
        log.debug("[LiteRule-Chain] FOR 循环完成: 迭代 {} 次", count);
        return results;
    }

    /**
     * WHILE 语义：条件为 true 时持续执行规则链，最多执行 maxIterations 次
     */
    private List<RuleResult> evaluateWhile(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-Chain] WHILE 链缺少 ExpressionEvaluator，跳过求值");
            return results;
        }
        int iteration = 0;
        while (iteration < maxIterations) {
            try {
                boolean matched = evaluator.evalBoolean(conditionExpression, context);
                if (!matched) {
                    break;
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Chain] WHILE 条件求值异常: expr='{}', error={}", conditionExpression, e.getMessage());
                break;
            }
            if (nodes != null) {
                for (RuleNode node : nodes) {
                    List<RuleResult> nodeResults = evaluateNode(node, context, evaluator, statsRecorder);
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && RuleResult.BREAK_CODE.equals(r.getRuleCode())) {
                            log.debug("[LiteRule-Chain] WHILE 循环遇到 BREAK，终止迭代");
                            return results;
                        }
                    }
                    Response.addAll(nodeResults);
                }
            }
            iteration++;
        }
        if (iteration >= maxIterations) {
            log.warn("[LiteRule-Chain] WHILE 循环达到最大迭代次数 {}，已终止", maxIterations);
        }
        log.debug("[LiteRule-Chain] WHILE 循环完成: 迭代 {} 次", iteration);
        return results;
    }

    /**
     * BREAK 语义：返回一个特殊的 BREAK 结果，由上层循环（FOR/WHILE）检测后终止
     */
    private List<RuleResult> evaluateBreak(RuleContext context, ExpressionEvaluator evaluator) {
        // 返回一个标记为 BREAK 的特殊结果（使用 BREAK_CODE 常量，避免与真实规则编码冲突）
        RuleResult breakResult = new RuleResult();
        breakResult.setRuleCode(RuleResult.BREAK_CODE);
        breakResult.setTriggered(true);
        breakResult.setSeverity(RuleSeverity.INFO);
        breakResult.setTitle("BREAK 终止循环");
        return Collections.singletonList(breakResult);
    }

    // ============================== 编排容错执行逻辑 (2.0.0) ==============================

    /**
     * CATCH 语义：执行主节点，异常时执行补偿节点
     *
     * <p>主节点（{@link #primaryNode}）正常执行时返回其结果列表。
     * 若主节点抛出异常，则执行补偿节点（{@link #catchNode}）进行回滚补偿，
     * 补偿节点的结果作为最终返回。若未配置补偿节点，仅记录日志。
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 主节点或补偿节点的评估结果
     * @since 1.0.0
     */
    private List<RuleResult> evaluateCatch(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (primaryNode == null) {
            log.warn("[LiteRule-Chain] CATCH 链缺少主节点，跳过执行");
            return results;
        }
        try {
            Response.addAll(evaluateNode(primaryNode, context, evaluator, statsRecorder));
            log.debug("[LiteRule-Chain] CATCH 主节点执行成功");
        } catch (Exception e) {
            log.warn("[LiteRule-Chain] CATCH 主节点执行异常: {}，触发补偿", e.getMessage());
            if (catchNode != null) {
                try {
                    List<RuleResult> catchResults = evaluateNode(catchNode, context, evaluator, statsRecorder);
                    Response.addAll(catchResults);
                    log.info("[LiteRule-Chain] CATCH 补偿节点执行完成, 结果数={}", catchResults.size());
                } catch (Exception ce) {
                    log.error("[LiteRule-Chain] CATCH 补偿节点也执行异常: {}", ce.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * RETRY 语义：执行主节点失败时自动重试，重试耗尽后执行回滚补偿
     *
     * <p>首次执行 {@link #primaryNode}，若抛出异常则等待 {@link #retryIntervalMs} 后重试，
     * 最多重试 {@link #maxRetries} 次。全部重试耗尽后仍失败，执行 {@link #catchNode} 回滚补偿（如果配置）。
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 主节点或回滚节点的评估结果
     * @since 1.0.0
     */
    private List<RuleResult> evaluateRetry(RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (primaryNode == null) {
            log.warn("[LiteRule-Chain] RETRY 链缺少主节点，跳过执行");
            return results;
        }
        int totalAttempts = maxRetries + 1; // 首次 + 重试
        Exception lastException = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                Response.addAll(evaluateNode(primaryNode, context, evaluator, statsRecorder));
                if (attempt > 1) {
                    log.info("[LiteRule-Chain] RETRY 第 {} 次尝试成功 (共 {} 次)", attempt, totalAttempts);
                } else {
                    log.debug("[LiteRule-Chain] RETRY 首次执行成功");
                }
                return results;
            } catch (Exception e) {
                lastException = e;
                if (attempt < totalAttempts) {
                    log.warn("[LiteRule-Chain] RETRY 第 {}/{} 次尝试失败: {}，{}ms 后重试",
                            attempt, totalAttempts, e.getMessage(), retryIntervalMs);
                    if (retryIntervalMs > 0) {
                        // 注意：Thread.sleep 会阻塞当前线程，在高吞吐场景下可能成为瓶颈。
                        // 如需非阻塞重试，建议使用 ScheduledExecutorService 或延迟队列。
                        try {
                            Thread.sleep(retryIntervalMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("[LiteRule-Chain] RETRY 重试等待被中断");
                            break;
                        }
                    }
                } else {
                    log.error("[LiteRule-Chain] RETRY 全部 {} 次尝试均失败: {}",
                            totalAttempts, e.getMessage());
                }
            }
        }
        // 全部重试耗尽，执行回滚补偿
        if (catchNode != null) {
            log.info("[LiteRule-Chain] RETRY 重试耗尽，执行回滚补偿节点");
            try {
                List<RuleResult> rollbackResults = evaluateNode(catchNode, context, evaluator, statsRecorder);
                Response.addAll(rollbackResults);
            } catch (Exception ce) {
                log.error("[LiteRule-Chain] RETRY 回滚补偿节点也执行异常: {}", ce.getMessage());
            }
        } else if (lastException != null) {
            log.warn("[LiteRule-Chain] RETRY 未配置回滚节点，异常被隔离: {}", lastException.getMessage());
        }
        return results;
    }

    /**
     * 评估单个编排节点
     *
     * <p>按节点类型分派：
     * <ul>
     *   <li>SINGLE - 直接评估包装的规则</li>
     *   <li>CHAIN - 递归评估子链</li>
     *   <li>GROUP - 依次评估全部子节点并合并结果</li>
     * </ul>
     * 单节点异常将被隔离，返回空列表。
     *
     * @param node      编排节点
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateNode(RuleNode node, RuleContext context, ExpressionEvaluator evaluator, StatsRecorder statsRecorder) {
        List<RuleResult> results = new ArrayList<>();
        if (node == null) {
            return results;
        }
        try {
            switch (node.getNodeType()) {
                case SINGLE -> {
                    long start = System.nanoTime();
                    RuleResult result = null;
                    boolean error = false;

                    // 节点级重试（2.0.0）
                    int maxAttempts = node.hasRetry() ? node.getRetryCount() + 1 : 1;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                            // 节点级超时（2.0.0）
                            if (node.hasTimeout()) {
                                final RuleContext ctx = context;
                                CompletableFuture<RuleResult> future =
                                        CompletableFuture.supplyAsync(
                                                () -> node.getRule().evaluate(ctx));
                                try {
                                    result = future.get(node.getTimeoutMs(), TimeUnit.MILLISECONDS);
                                    error = false;
                                    break; // 成功则跳出重试循环
                                } catch (TimeoutException te) {
                                    result = null;
                                    error = true;
                                    log.warn("[LiteRule-Chain] 规则 {} 执行超时 ({}ms), attempt={}/{}",
                                            node.getRule().getCode(), node.getTimeoutMs(), attempt, maxAttempts);
                                    future.cancel(true);
                                } catch (Exception ex) {
                                    result = null;
                                    error = true;
                                    log.warn("[LiteRule-Chain] 规则 {} 评估异常: {}, attempt={}/{}",
                                            node.getRule().getCode(), ex.getMessage(), attempt, maxAttempts);
                                }
                            } else {
                                result = node.getRule().evaluate(context);
                                error = false;
                                break; // 成功则跳出重试循环
                            }
                        } catch (Exception e) {
                            result = null;
                            error = true;
                            log.warn("[LiteRule-Chain] 规则 {} 评估异常: {}, attempt={}/{}",
                                    node.getRule().getCode(), e.getMessage(), attempt, maxAttempts);
                        }
                        // 重试前等待
                        if (attempt < maxAttempts && node.getRetryIntervalMs() > 0) {
                            try {
                                Thread.sleep(node.getRetryIntervalMs());
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }

                    long elapsed = (System.nanoTime() - start) / 1_000_000;
                    // 记录统计到引擎
                    if (statsRecorder != null) {
                        String ruleCode = node.getRule() != null ? node.getRule().getCode() : "unknown";
                        statsRecorder.record(ruleCode,
                                result != null && result.isTriggered(), error, elapsed);
                    }
                    if (result != null && result.isTriggered()) {
                        Response.add(result);
                    }
                }
                case CHAIN -> {
                    RuleChain sub = node.getChain();
                    if (sub != null) {
                        Response.addAll(sub.evaluate(context, evaluator, statsRecorder));
                    }
                }
                case GROUP -> {
                    if (node.getChildren() != null) {
                        for (RuleNode child : node.getChildren()) {
                            Response.addAll(evaluateNode(child, context, evaluator, statsRecorder));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[LiteRule-Chain] 节点评估异常: type={}, error={}",
                    node.getNodeType(), e.getMessage());
        }
        return results;
    }

    /**
     * 获取链类型
     *
     * @return 链类型
     */
    public RuleChainType getChainType() {
        return chainType;
    }

    /**
     * 获取节点列表
     *
     * @return 不可修改的节点列表；THEN/WHEN 之外可能为 null
     */
    public List<RuleNode> getNodes() {
        return nodes;
    }

    /**
     * 获取多分支条件列表（ELIF 专用）
     *
     * <p>仅 ELIF 链返回非 null 列表；其他链类型返回 null。
     * P0-1 增强：暴露给 {@link ChainGraphConverter} 提取子节点。
     *
     * @return 不可修改的多分支条件列表
     * @since 1.0.0
     */
    public List<Map.Entry<String, RuleNode>> getElifBranches() {
        return elifBranches;
    }

    /**
     * 获取 ELSE 节点（ELIF 专用）
     *
     * @return ELSE 节点；ELIF 链之外或未设置时返回 null
     * @since 1.0.0
     */
    public RuleNode getElseNode() {
        return elseNode;
    }

    /**
     * 获取 SWITCH 默认分支节点
     *
     * @return 默认分支节点；SWITCH 链之外或未设置时返回 null
     * @since 1.0.0
     */
    public RuleNode getDefaultBranch() {
        return defaultBranch;
    }

    /**
     * 获取 FOR 迭代集合表达式（如 {@code "items"}）
     *
     * @return 集合表达式；FOR 链之外返回 null
     * @since 1.0.0
     */
    public String getIterableExpression() {
        return iterableExpression;
    }

    /**
     * 获取 FOR 迭代变量名（如 {@code "item"}）
     *
     * @return 迭代变量名；FOR 链之外返回 null
     * @since 1.0.0
     */
    public String getIterationVar() {
        return iterationVar;
    }

    /**
     * 获取 WHILE 最大迭代次数
     *
     * @return 最大迭代次数；WHILE 链之外返回 0
     * @since 1.0.0
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * 获取条件表达式
     *
     * @return 条件表达式；IF 之外为 null
     */
    public String getConditionExpression() {
        return conditionExpression;
    }

    /**
     * 获取分支 key 字段名
     *
     * @return 分支 key 字段名；SWITCH 之外为 null
     */
    public String getBranchKey() {
        return branchKey;
    }

    /**
     * 获取分支映射
     *
     * @return 不可修改的分支映射；SWITCH 之外为 null
     */
    public Map<String, RuleNode> getBranchMap() {
        return branchMap;
    }

    /**
     * 获取主节点（CATCH/RETRY 使用）
     *
     * @return 主节点；CATCH/RETRY 之外为 null
     * @since 1.0.0
     */
    public RuleNode getPrimaryNode() {
        return primaryNode;
    }

    /**
     * 获取补偿/回滚节点（CATCH/RETRY 使用）
     *
     * @return 补偿节点；CATCH/RETRY 之外为 null
     * @since 1.0.0
     */
    public RuleNode getCatchNode() {
        return catchNode;
    }

    /**
     * 获取最大重试次数（RETRY 使用）
     *
     * @return 最大重试次数；RETRY 之外为 0
     * @since 1.0.0
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取重试间隔（RETRY 使用）
     *
     * @return 重试间隔毫秒；RETRY 之外为 0
     * @since 1.0.0
     */
    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * 获取节点级超时（毫秒）
     *
     * @return 节点级超时；0 表示不超时
     * @since 1.0.0
     */
    public long getNodeTimeoutMs() {
        return nodeTimeoutMs;
    }

    /**
     * 获取节点级重试次数
     *
     * @return 节点级重试次数；0 表示不重试
     * @since 1.0.0
     */
    public int getNodeRetries() {
        return nodeRetries;
    }
}
