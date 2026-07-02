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
     * @param elifBranches        多分支条件列表
     * @param elseNode            ELSE 分支节点
     * @param iterableExpression  遍历集合表达式
     * @param iterationVar        迭代变量名
     * @param maxIterations       最大迭代次数
     * @param isBreak             是否终止
     */
    private RuleChain(RuleChainType chainType, List<RuleNode> nodes, String conditionExpression,
                      String branchKey, Map<String, RuleNode> branchMap,
                      List<Map.Entry<String, RuleNode>> elifBranches, RuleNode elseNode,
                      String iterableExpression, String iterationVar,
                      int maxIterations, boolean isBreak) {
        this.chainType = chainType;
        this.nodes = nodes;
        this.conditionExpression = conditionExpression;
        this.branchKey = branchKey;
        this.branchMap = branchMap;
        this.elifBranches = elifBranches;
        this.elseNode = elseNode;
        this.iterableExpression = iterableExpression;
        this.iterationVar = iterationVar;
        this.maxIterations = maxIterations;
        this.isBreak = isBreak;
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
                Collections.unmodifiableList(nodeList), null, null, null, null, null, null, null, 0, false);
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
                Collections.unmodifiableList(nodeList), null, null, null, null, null, null, null, 0, false);
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
                nodeList, conditionExpression, null, null, null, null, null, null, 0, false);
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
                null, null, branchKey, Collections.unmodifiableMap(nodeMap), null, null, null, null, 0, false);
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
                null, null, null, null, Collections.unmodifiableList(branchList), elseNode, null, null, 0, false);
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
                nodeList, null, null, null, null, null, iterableExpression, iterationVar, 0, false);
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
                nodeList, conditionExpression, null, null, null, null, null, null, maxIterations, false);
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
                null, null, null, null, null, null, null, null, 0, true);
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
            case ELIF -> evaluateElif(context, evaluator);
            case SWITCH -> evaluateSwitch(context, evaluator);
            case FOR -> evaluateFor(context, evaluator);
            case WHILE -> evaluateWhile(context, evaluator);
            case BREAK -> evaluateBreak(context, evaluator);
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
     * ELIF 语义：依次求值多个条件表达式，执行第一个匹配的分支；无匹配则执行 else 分支
     */
    private List<RuleResult> evaluateElif(RuleContext context, ExpressionEvaluator evaluator) {
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
                        results.addAll(evaluateNode(branch.getValue(), context, evaluator));
                        return results;
                    }
                } catch (Exception e) {
                    log.warn("[LiteRule-Chain] ELIF 分支求值异常: expr='{}', error={}", branch.getKey(), e.getMessage());
                }
            }
        }
        // 所有条件都不匹配，执行 else 分支
        if (elseNode != null) {
            results.addAll(evaluateNode(elseNode, context, evaluator));
        }
        return results;
    }

    /**
     * FOR 语义：遍历集合中的每个元素，注入迭代变量后执行规则链
     */
    @SuppressWarnings("unchecked")
    private List<RuleResult> evaluateFor(RuleContext context, ExpressionEvaluator evaluator) {
        List<RuleResult> results = new ArrayList<>();
        if (iterableExpression == null || iterationVar == null) {
            return results;
        }
        Object iterable = context.getFacts().get(iterableExpression);
        if (!(iterable instanceof Iterable)) {
            log.warn("[LiteRule-Chain] FOR 遍历表达式 '{}' 不是可迭代对象: class={}", iterableExpression,
                    iterable != null ? iterable.getClass().getName() : "null");
            return results;
        }
        int count = 0;
        for (Object item : (Iterable<Object>) iterable) {
            // 注入迭代变量到上下文
            context.getFacts().put(iterationVar, item);
            // 执行规则链
            if (nodes != null) {
                for (RuleNode node : nodes) {
                    List<RuleResult> nodeResults = evaluateNode(node, context, evaluator);
                    // 检查是否遇到 BREAK
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && "BREAK".equals(r.getRuleCode())) {
                            log.debug("[LiteRule-Chain] FOR 循环遇到 BREAK，终止迭代");
                            context.getFacts().remove(iterationVar);
                            return results;
                        }
                    }
                    results.addAll(nodeResults);
                }
            }
            count++;
        }
        // 清理迭代变量
        context.getFacts().remove(iterationVar);
        log.debug("[LiteRule-Chain] FOR 循环完成: 迭代 {} 次", count);
        return results;
    }

    /**
     * WHILE 语义：条件为 true 时持续执行规则链，最多执行 maxIterations 次
     */
    private List<RuleResult> evaluateWhile(RuleContext context, ExpressionEvaluator evaluator) {
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
                    List<RuleResult> nodeResults = evaluateNode(node, context, evaluator);
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && "BREAK".equals(r.getRuleCode())) {
                            log.debug("[LiteRule-Chain] WHILE 循环遇到 BREAK，终止迭代");
                            return results;
                        }
                    }
                    results.addAll(nodeResults);
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
        // 返回一个标记为 BREAK 的特殊结果
        RuleResult breakResult = new RuleResult();
        breakResult.setRuleCode("BREAK");
        breakResult.setTriggered(true);
        breakResult.setSeverity(RuleSeverity.INFO);
        breakResult.setTitle("BREAK 终止循环");
        return Collections.singletonList(breakResult);
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
