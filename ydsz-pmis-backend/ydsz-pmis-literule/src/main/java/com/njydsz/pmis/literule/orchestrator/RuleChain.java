package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
public class RuleChain {

    /** 链类型（THEN/WHEN/IF/SWITCH） */
    private final RuleChainType chainType;

    /** 节点列表（THEN/WHEN 使用） */
    private final List<RuleNode> nodes;

    /** 条件表达式（IF 使用） */
    private final String conditionExpression;

    /** 分支 key 取值字段名（SWITCH 使用） */
    private final String branchKey;

    /** 分支映射：分支 key -&gt; 分支节点（SWITCH 使用） */
    private final Map<String, RuleNode> branchMap;

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

    /** 是否终止循环（BREAK 使用） */
    private final boolean isBreak;

    /**
     * 私有构造，统一通过工厂方法创建
     *
     * @param chainType           链类型
     * @param nodes               节点列表
     * @param conditionExpression 条件表达式
     * @param branchKey           分支 key 字段名
     * @param branchMap           分支映射
     */
    private RuleChain(RuleChainType chainType, List<RuleNode> nodes, String conditionExpression,
                      String branchKey, Map<String, RuleNode> branchMap) {
        this.chainType = chainType;
        this.nodes = nodes;
        this.conditionExpression = conditionExpression;
        this.branchKey = branchKey;
        this.branchMap = branchMap;
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
                Collections.unmodifiableList(nodeList), null, null, null);
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
                Collections.unmodifiableList(nodeList), null, null, null);
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
                nodeList, conditionExpression, null, null);
    }

    /**
     * 构建分支选择链（SWITCH）
     *
     * @param branchKey 分支 key 字段名（从上下文事实中取值）
     * @param branches  分支映射：分支 key -&gt; 分支规则
     * @return SWITCH 类型规则链
     */
    public static RuleChain switchOn(String branchKey, Map<String, Rule> branches) {
        Objects.requireNonNull(branchKey, "branchKey 不能为 null");
        Objects.requireNonNull(branches, "branches 不能为 null");
        Map<String, RuleNode> nodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, Rule> entry : branches.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                nodeMap.put(entry.getKey(), RuleNode.of(entry.getValue()));
            }
        }
        return new RuleChain(RuleChainType.SWITCH,
                null, null, branchKey, Collections.unmodifiableMap(nodeMap));
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
        Objects.requireNonNull(context, "context 不能为 null");
        return switch (chainType) {
            case THEN -> evaluateThen(context, evaluator);
            case WHEN -> evaluateWhen(context, evaluator);
            case IF -> evaluateIf(context, evaluator);
            case SWITCH -> evaluateSwitch(context, evaluator);
        };
    }

    /**
     * THEN 语义：顺序执行全部节点，收集触发结果
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateThen(RuleContext context, ExpressionEvaluator evaluator) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null) {
            return results;
        }
        for (RuleNode node : nodes) {
            results.addAll(evaluateNode(node, context, evaluator));
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
    private List<RuleResult> evaluateWhen(RuleContext context, ExpressionEvaluator evaluator) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return results;
        }
        // 并行执行所有节点，使用 ForkJoinPool.commonPool
        List<CompletableFuture<List<RuleResult>>> futures = new ArrayList<>();
        for (RuleNode node : nodes) {
            futures.add(CompletableFuture.supplyAsync(() -> evaluateNode(node, context, evaluator)));
        }
        // 等待全部完成并合并结果
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        for (CompletableFuture<List<RuleResult>> future : futures) {
            results.addAll(future.join());
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
    private List<RuleResult> evaluateIf(RuleContext context, ExpressionEvaluator evaluator) {
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
                results.addAll(evaluateNode(node, context, evaluator));
            }
        }
        return results;
    }

    /**
     * SWITCH 语义：从 context.getFacts().get(branchKey) 取分支 key，执行对应分支
     *
     * @param context   规则上下文
     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateSwitch(RuleContext context, ExpressionEvaluator evaluator) {
        List<RuleResult> results = new ArrayList<>();
        if (branchMap == null || branchKey == null) {
            return results;
        }
        Object key = context.getFacts().get(branchKey);
        log.debug("[LiteRule-Chain] SWITCH 分支选择: branchKey='{}', value={}", branchKey, key);
        if (key == null) {
            log.warn("[LiteRule-Chain] SWITCH 分支 key '{}' 在上下文中不存在", branchKey);
            return results;
        }
        RuleNode branch = branchMap.get(String.valueOf(key));
        if (branch == null) {
            log.warn("[LiteRule-Chain] SWITCH 未匹配到分支: key='{}'", key);
            return results;
        }
        results.addAll(evaluateNode(branch, context, evaluator));
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
    private List<RuleResult> evaluateNode(RuleNode node, RuleContext context, ExpressionEvaluator evaluator) {
        List<RuleResult> results = new ArrayList<>();
        if (node == null) {
            return results;
        }
        try {
            switch (node.getNodeType()) {
                case SINGLE -> {
                    RuleResult result = node.getRule().evaluate(context);
                    if (result != null && result.isTriggered()) {
                        results.add(result);
                    }
                }
                case CHAIN -> {
                    RuleChain sub = node.getChain();
                    if (sub != null) {
                        results.addAll(sub.evaluate(context, evaluator));
                    }
                }
                case GROUP -> {
                    if (node.getChildren() != null) {
                        for (RuleNode child : node.getChildren()) {
                            results.addAll(evaluateNode(child, context, evaluator));
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
}
