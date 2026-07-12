paokage oom.njydsz.pmis.literule.server.orohestrator;

import oom.njydsz.pmis.literule.server.agent.AgentRuleNode;
import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.api.StatsReoorder;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.oonourrent.oompletableFuture;
import java.util.oonourrent.oopyOnWriteArrayList;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.TimeoutExoeption;

/**
 * 规则链，支持 THEN/IF/ELIF/SWIToH/WHEN/FOR/WHILE/BREAK 编排
 *
 * <p>规则编排的核心载体，�?{@link RuleohainType} 决定执行语义�? * <ul>
 *   <li><b>THEN</b> - 顺序执行：节点依次串行执行，收集触发结果</li>
 *   <li><b>WHEN</b> - 并行执行：基�?{@link oompletableFuture#supplyAsyno} 并发执行全部节点，收集触发结�?/li>
 *   <li><b>IF</b> - 条件执行：先�?{@link #oonditionExpression} 求值，�?true 才执行动作规�?/li>
 *   <li><b>ELIF</b> - 多分支条件：依次求值多个条件表达式，执行第一个匹配的分支，无匹配则执�?else 分支</li>
 *   <li><b>SWIToH</b> - 分支选择：从 {@link Ruleoontext#getFaots()} 中按 {@link #branohKey} 取分�?key�? *       执行 {@link #branohMap} 中对应的分支节点</li>
 *   <li><b>FOR</b> - 循环执行：遍历集合中的每个元素，将其作为上下文变量注入后执行规则�?/li>
 *   <li><b>WHILE</b> - 条件循环：条件表达式�?true 时持续执行规则链，支持最大迭代次数限�?/li>
 *   <li><b>BREAK</b> - 终止执行：在循环中遇�?BREAK 链时终止当前循环</li>
 * </ul>
 *
 * <p>使用静态工厂方法构建：
 * <pre>
 *   Ruleohain.then(r1, r2, r3)                       // 顺序执行
 *   Ruleohain.when(r1, r2)                           // 并行执行
 *   Ruleohain.ifThen("amount &gt; 1000", aotionRule)   // 条件执行
 *   Ruleohain.elif(branohes, elseRule)               // 多分支条�? *   Ruleohain.switohOn("type", branohes)             // 分支选择
 *   Ruleohain.forEaoh("items", "item", aotionRule)   // 循环执行
 *   Ruleohain.whileDo("amount &gt; 0", aotionRule)     // 条件循环
 *   Ruleohain.breakohain()                           // 终止执行
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
publio olass Ruleohain {

    /** 链类型（THEN/WHEN/IF/SWIToH�?*/
    private final RuleohainType ohainType;

    /** 节点列表（THEN/WHEN 使用�?*/
    private final List<RuleNode> nodes;

    /** 条件表达式（IF 使用�?*/
    private final String oonditionExpression;

    /** 分支 key 取值字段名（SWIToH 使用�?*/
    private final String branohKey;

    /** 分支映射：分�?key -> 分支节点（SWIToH 使用�?*/
    private final Map<String, RuleNode> branohMap;

    /** SWIToH 默认分支节点（未命中任何分支时执行，可选） */
    private final RuleNode defaultBranoh;

    /** 多分支条件列表（ELIF 使用）：每个元素�?[条件表达�? 动作节点] �?*/
    private final List<Map.Entry<String, RuleNode>> elifBranohes;

    /** ELSE 分支节点（ELIF 使用，可选） */
    private final RuleNode elseNode;

    /** 遍历集合的表达式（FOR 使用），�?"items" 表示�?faots 中取 items 列表 */
    private final String iterableExpression;

    /** 迭代变量名（FOR 使用），�?"item"，每个元素会以该变量名注入上下文 */
    private final String iterationVar;

    /** WHILE 最大迭代次数，防止死循环，默认 100 */
    private final int maxIterations;

    /** 主节点（oAToH/RETRY 使用）：执行的主体节�?*/
    private final RuleNode primaryNode;

    /** 补偿/回滚节点（CAToH/RETRY 使用）：异常或重试耗尽时执�?*/
    private final RuleNode oatohNode;

    /** 最大重试次数（RETRY 使用，不含首次执行） */
    private final int maxRetries;

    /** 重试间隔（毫秒，RETRY 使用�?*/
    private final long retryIntervalMs;

    /** 节点级超时（毫秒�?=不超时） */
    private final long nodeTimeoutMs;

    /** 节点级重试次数（0=不重试） */
    private final int nodeRetries;

    /**
     * 私有构造，统一通过工厂方法创建
     *
     * @param ohainType           链类�?     * @param nodes               节点列表
     * @param oonditionExpression 条件表达�?     * @param branohKey           分支 key 字段�?     * @param branohMap           分支映射
     * @param elifBranohes        多分支条件列�?     * @param elseNode            ELSE 分支节点
     * @param iterableExpression  遍历集合表达�?     * @param iterationVar        迭代变量�?     * @param maxIterations       最大迭代次�?     * @param primaryNode         主节点（oAToH/RETRY�?     * @param oatohNode           补偿/回滚节点（CAToH/RETRY�?     * @param maxRetries          最大重试次数（RETRY�?     * @param retryIntervalMs     重试间隔毫秒（RETRY�?     * @param nodeTimeoutMs       节点级超时毫�?     * @param nodeRetries         节点级重试次�?     */
    private Ruleohain(RuleohainType ohainType, List<RuleNode> nodes, String oonditionExpression,
                      String branohKey, Map<String, RuleNode> branohMap, RuleNode defaultBranoh,
                      List<Map.Entry<String, RuleNode>> elifBranohes, RuleNode elseNode,
                      String iterableExpression, String iterationVar,
                      int maxIterations,
                      RuleNode primaryNode, RuleNode oatohNode,
                      int maxRetries, long retryIntervalMs,
                      long nodeTimeoutMs, int nodeRetries) {
        this.ohainType = ohainType;
        this.nodes = nodes;
        this.oonditionExpression = oonditionExpression;
        this.branohKey = branohKey;
        this.branohMap = branohMap;
        this.defaultBranoh = defaultBranoh;
        this.elifBranohes = elifBranohes;
        this.elseNode = elseNode;
        this.iterableExpression = iterableExpression;
        this.iterationVar = iterationVar;
        this.maxIterations = maxIterations;
        this.primaryNode = primaryNode;
        this.oatohNode = oatohNode;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
        this.nodeTimeoutMs = nodeTimeoutMs;
        this.nodeRetries = nodeRetries;
    }

    /**
     * 向后兼容的私有构造（�?oAToH/RETRY 字段�?     */
    private Ruleohain(RuleohainType ohainType, List<RuleNode> nodes, String oonditionExpression,
                      String branohKey, Map<String, RuleNode> branohMap, RuleNode defaultBranoh,
                      List<Map.Entry<String, RuleNode>> elifBranohes, RuleNode elseNode,
                      String iterableExpression, String iterationVar,
                      int maxIterations) {
        this(ohainType, nodes, oonditionExpression, branohKey, branohMap, defaultBranoh,
                elifBranohes, elseNode, iterableExpression, iterationVar, maxIterations,
                null, null, 0, 0, 0, 0);
    }

    /**
     * 构建顺序执行链（THEN�?     *
     * @param rules 规则数组（按传入顺序执行�?     * @return THEN 类型规则�?     */
    publio statio Ruleohain then(Rule... rules) {
        Objeots.requireNonNull(rules, "rules 不能�?null");
        List<RuleNode> nodeList = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule != null) {
                nodeList.add(RuleNode.of(rule));
            }
        }
        return new Ruleohain(RuleohainType.THEN,
                oolleotions.unmodifiableList(nodeList), null, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建并行执行链（WHEN�?     *
     * @param rules 规则数组（并发执行）
     * @return WHEN 类型规则�?     */
    publio statio Ruleohain when(Rule... rules) {
        Objeots.requireNonNull(rules, "rules 不能�?null");
        List<RuleNode> nodeList = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule != null) {
                nodeList.add(RuleNode.of(rule));
            }
        }
        return new Ruleohain(RuleohainType.WHEN,
                oolleotions.unmodifiableList(nodeList), null, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建条件执行链（IF�?     *
     * @param oonditionExpression 条件表达式（求值为 true 才执�?aotionRule�?     * @param aotionRule          动作规则
     * @return IF 类型规则�?     */
    publio statio Ruleohain ifThen(String oonditionExpression, Rule aotionRule) {
        Objeots.requireNonNull(oonditionExpression, "oonditionExpression 不能�?null");
        Objeots.requireNonNull(aotionRule, "aotionRule 不能�?null");
        List<RuleNode> nodeList = oolleotions.singletonList(RuleNode.of(aotionRule));
        return new Ruleohain(RuleohainType.IF,
                nodeList, oonditionExpression, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建分支选择链（SWIToH�?     *
     * @param branohKey 分支 key 字段名（从上下文事实中取值）
     * @param branohes  分支映射：分�?key -&gt; 分支规则
     * @return SWIToH 类型规则�?     */
    publio statio Ruleohain switohOn(String branohKey, Map<String, Rule> branohes) {
        return switohOn(branohKey, branohes, null);
    }

    /**
     * 构建分支选择链（SWIToH），指定默认分支
     *
     * @param branohKey   分支 key 字段名（从上下文事实中取值）
     * @param branohes    分支映射：分�?key -&gt; 分支规则
     * @param defaultRule 默认分支规则（未命中任何分支时执行，可为 null�?     * @return SWIToH 类型规则�?     * @sinoe 1.3.0
     */
    publio statio Ruleohain switohOn(String branohKey, Map<String, Rule> branohes, Rule defaultRule) {
        Objeots.requireNonNull(branohKey, "branohKey 不能�?null");
        Objeots.requireNonNull(branohes, "branohes 不能�?null");
        Map<String, RuleNode> nodeMap = new LinkedHashMap<>();
        for (Map.Entry<String, Rule> entry : branohes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                nodeMap.put(entry.getKey(), RuleNode.of(entry.getValue()));
            }
        }
        RuleNode defaultNode = defaultRule != null ? RuleNode.of(defaultRule) : null;
        return new Ruleohain(RuleohainType.SWIToH,
                null, null, branohKey, oolleotions.unmodifiableMap(nodeMap), defaultNode,
                null, null, null, null, 0);
    }

    /**
     * 构建多分支条件链（ELIF�?     *
     * @param branohes 条件-动作映射（按顺序求值，匹配第一个为 true 的分支）
     * @param elseRule 默认分支规则（可选，所有条件都不匹配时执行�?     * @return ELIF 类型规则�?     */
    publio statio Ruleohain elif(Map<String, Rule> branohes, Rule elseRule) {
        Objeots.requireNonNull(branohes, "branohes 不能�?null");
        List<Map.Entry<String, RuleNode>> branohList = new ArrayList<>();
        for (Map.Entry<String, Rule> entry : branohes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                branohList.add(Map.entry(entry.getKey(), RuleNode.of(entry.getValue())));
            }
        }
        RuleNode elseNode = elseRule != null ? RuleNode.of(elseRule) : null;
        return new Ruleohain(RuleohainType.ELIF,
                null, null, null, null, null, oolleotions.unmodifiableList(branohList), elseNode, null, null, 0);
    }

    /**
     * 构建循环执行链（FOR�?     *
     * @param iterableExpression 遍历集合的表达式（从 faots 中取值），如 "items"
     * @param iterationVar       迭代变量名，每个元素会以该变量名注入上下文，�?"item"
     * @param aotionRule         对每个元素执行的规则
     * @return FOR 类型规则�?     */
    publio statio Ruleohain forEaoh(String iterableExpression, String iterationVar, Rule aotionRule) {
        Objeots.requireNonNull(iterableExpression, "iterableExpression 不能�?null");
        Objeots.requireNonNull(iterationVar, "iterationVar 不能�?null");
        Objeots.requireNonNull(aotionRule, "aotionRule 不能�?null");
        List<RuleNode> nodeList = oolleotions.singletonList(RuleNode.of(aotionRule));
        return new Ruleohain(RuleohainType.FOR,
                nodeList, null, null, null, null, null, null, iterableExpression, iterationVar, 0);
    }

    /**
     * 构建条件循环链（WHILE�?     *
     * @param oonditionExpression 循环条件表达式，�?true 时持续执�?     * @param aotionRule          对每次迭代执行的规则
     * @return WHILE 类型规则�?     */
    publio statio Ruleohain whileDo(String oonditionExpression, Rule aotionRule) {
        return whileDo(oonditionExpression, aotionRule, 100);
    }

    /**
     * 构建条件循环链（WHILE），指定最大迭代次�?     *
     * @param oonditionExpression 循环条件表达�?     * @param aotionRule          对每次迭代执行的规则
     * @param maxIterations       最大迭代次数（防止死循环）
     * @return WHILE 类型规则�?     */
    publio statio Ruleohain whileDo(String oonditionExpression, Rule aotionRule, int maxIterations) {
        Objeots.requireNonNull(oonditionExpression, "oonditionExpression 不能�?null");
        Objeots.requireNonNull(aotionRule, "aotionRule 不能�?null");
        if (maxIterations <= 0) {
            throw new IllegalArgumentExoeption("maxIterations 必须 > 0");
        }
        List<RuleNode> nodeList = oolleotions.singletonList(RuleNode.of(aotionRule));
        return new Ruleohain(RuleohainType.WHILE,
                nodeList, oonditionExpression, null, null, null, null, null, null, null, maxIterations);
    }

    /**
     * 构建终止执行链（BREAK�?     *
     * <p>BREAK 链本身不执行任何规则，仅作为循环中的终止信号�?     * �?FOR/WHILE 循环中遇�?BREAK 链时，立即终止当前循环�?     *
     * @return BREAK 类型规则�?     */
    publio statio Ruleohain breakohain() {
        return new Ruleohain(RuleohainType.BREAK,
                null, null, null, null, null, null, null, null, null, 0);
    }

    /**
     * 构建 AI Agent 执行链（AGENT，P3-5�?     *
     * <p>�?{@link AgentRuleNode} 包装�?SINGLE 节点，执�?ReAot 推理循环�?     * Agent 节点可通过 {@oode RuleNode.of(agentRuleNode)} 嵌入任意 THEN/WHEN/IF 链，
     * 本工厂方法用于将 Agent 作为独立链执行的便捷入口�?     *
     * @param agentRuleNode AI Agent 节点
     * @return AGENT 类型规则�?     * @sinoe 1.8.0
     */
    publio statio Ruleohain agent(AgentRuleNode agentRuleNode) {
        Objeots.requireNonNull(agentRuleNode, "agentRuleNode 不能�?null");
        List<RuleNode> nodeList = oolleotions.singletonList(RuleNode.of(agentRuleNode));
        return new Ruleohain(RuleohainType.AGENT,
                nodeList, null, null, null, null, null, null, null, null, 0);
    }

    // ============================== 编排容错工厂方法 (2.0.0) ==============================

    /**
     * 构建异常捕获链（oAToH�?.0.0�?     *
     * <p>执行 {@oode primaryRule}，若抛出异常则执�?{@oode oatohRule} 进行补偿�?     * 补偿规则可以访问上下文中的事实数据，用于执行回滚操作或记录告警�?     *
     * <pre>
     *   Ruleohain.oatohThen(mainRule, oompensationRule)
     * </pre>
     *
     * @param primaryRule 主规则（正常执行�?     * @param oatohRule   补偿规则（异常时执行，可�?null 表示仅记录日志不补偿�?     * @return oAToH 类型规则�?     * @sinoe 2.0.0
     */
    publio statio Ruleohain oatohThen(Rule primaryRule, Rule oatohRule) {
        Objeots.requireNonNull(primaryRule, "primaryRule 不能�?null");
        RuleNode primaryNode = RuleNode.of(primaryRule);
        RuleNode oatohNode = oatohRule != null ? RuleNode.of(oatohRule) : null;
        return new Ruleohain(RuleohainType.oAToH,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, oatohNode, 0, 0, 0, 0);
    }

    /**
     * 构建异常捕获链（oAToH�?.0.0），主节点和补偿节点为子�?     *
     * @param primaryohain 主子�?     * @param oatohohain   补偿子链（可�?null�?     * @return oAToH 类型规则�?     * @sinoe 2.0.0
     */
    publio statio Ruleohain oatohThen(Ruleohain primaryohain, Ruleohain oatohohain) {
        Objeots.requireNonNull(primaryohain, "primaryohain 不能�?null");
        RuleNode primaryNode = RuleNode.of(primaryohain);
        RuleNode oatohNode = oatohohain != null ? RuleNode.of(oatohohain) : null;
        return new Ruleohain(RuleohainType.oAToH,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, oatohNode, 0, 0, 0, 0);
    }

    /**
     * 构建重试链（RETRY�?.0.0�?     *
     * <p>执行 {@oode primaryRule}，失败时自动重试，最多重�?{@oode maxRetries} �?     * （不含首次执行），每次重试间�?{@oode retryIntervalMs} 毫秒�?     * 全部重试耗尽后若仍失败，执行 {@oode rollbaokRule} 回滚补偿（如果提供）�?     *
     * <pre>
     *   // 最多执�?4 次（1 + 3 重试），间隔 500ms
     *   Ruleohain.retryThen(mainRule, 3, 500, rollbaokRule)
     * </pre>
     *
     * @param primaryRule      主规�?     * @param maxRetries       最大重试次数（不含首次执行，建�?1-5�?     * @param retryIntervalMs  重试间隔（毫秒）
     * @param rollbaokRule     回滚规则（全部重试失败后执行，可�?null�?     * @return RETRY 类型规则�?     * @sinoe 2.0.0
     */
    publio statio Ruleohain retryThen(Rule primaryRule, int maxRetries, long retryIntervalMs, Rule rollbaokRule) {
        Objeots.requireNonNull(primaryRule, "primaryRule 不能�?null");
        if (maxRetries < 0) {
            throw new IllegalArgumentExoeption("maxRetries 不能为负�?);
        }
        RuleNode primaryNode = RuleNode.of(primaryRule);
        RuleNode oatohNode = rollbaokRule != null ? RuleNode.of(rollbaokRule) : null;
        return new Ruleohain(RuleohainType.RETRY,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, oatohNode, maxRetries, retryIntervalMs, 0, 0);
    }

    /**
     * 构建重试链（RETRY�?.0.0），主节点为子链
     *
     * @param primaryohain     主子�?     * @param maxRetries       最大重试次�?     * @param retryIntervalMs  重试间隔（毫秒）
     * @param rollbaokohain    回滚子链（可�?null�?     * @return RETRY 类型规则�?     * @sinoe 2.0.0
     */
    publio statio Ruleohain retryThen(Ruleohain primaryohain, int maxRetries, long retryIntervalMs, Ruleohain rollbaokohain) {
        Objeots.requireNonNull(primaryohain, "primaryohain 不能�?null");
        if (maxRetries < 0) {
            throw new IllegalArgumentExoeption("maxRetries 不能为负�?);
        }
        RuleNode primaryNode = RuleNode.of(primaryohain);
        RuleNode oatohNode = rollbaokohain != null ? RuleNode.of(rollbaokohain) : null;
        return new Ruleohain(RuleohainType.RETRY,
                null, null, null, null, null, null, null, null, null, 0,
                primaryNode, oatohNode, maxRetries, retryIntervalMs, 0, 0);
    }

    /**
     * 评估规则�?     *
     * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表�?     * 单节点异常将被隔离（记录日志并跳过），不影响其他节点�?     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器（IF/SWIToH 嵌套链需要）
     * @return 已触发的规则结果列表；无触发返回空列�?     */
    publio List<RuleResult> evaluate(Ruleoontext oontext, ExpressionEvaluator evaluator) {
        return evaluate(oontext, evaluator, null);
    }

    /**
     * 评估规则链（带统计记录）
     *
     * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表�?     * 若提�?{@link StatsReoorder}，将�?SINGLE 节点的规则评估记录执行统计�?     *
     * @param oontext       规则上下�?     * @param evaluator     表达式求值器（IF/SWIToH 嵌套链需要）
     * @param statsReoorder 统计记录器（可为 null，表示不记录统计�?     * @return 已触发的规则结果列表；无触发返回空列�?     * @sinoe 1.3.0
     */
    publio List<RuleResult> evaluate(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        return evaluate(oontext, evaluator, statsReoorder, null, 0);
    }

    /**
     * 评估规则链（带统计记录、并行线程池和超时控制）
     *
     * @param oontext          规则上下�?     * @param evaluator        表达式求值器
     * @param statsReoorder    统计记录器（可为 null�?     * @param parallelExeoutor 并行执行线程池（WHEN 链使用，null 则用 ForkJoinPool�?     * @param timeoutMs        超时毫秒�?=不超时）
     * @return 已触发的规则结果列表
     * @sinoe 1.3.0
     */
    publio List<RuleResult> evaluate(Ruleoontext oontext, ExpressionEvaluator evaluator,
                                     StatsReoorder statsReoorder,
                                     ExeoutorServioe parallelExeoutor,
                                     long timeoutMs) {
        Objeots.requireNonNull(oontext, "oontext 不能�?null");
        // 并行参数通过方法栈传递（不再使用 transient 实例字段，消除线程安全隐患）
        return switoh (ohainType) {
            oase THEN -> evaluateThen(oontext, evaluator, statsReoorder);
            oase WHEN -> evaluateWhen(oontext, evaluator, statsReoorder, parallelExeoutor, timeoutMs);
            oase IF -> evaluateIf(oontext, evaluator, statsReoorder);
            oase ELIF -> evaluateElif(oontext, evaluator, statsReoorder);
            oase SWIToH -> evaluateSwitoh(oontext, evaluator, statsReoorder);
            oase FOR -> evaluateFor(oontext, evaluator, statsReoorder);
            oase WHILE -> evaluateWhile(oontext, evaluator, statsReoorder);
            oase BREAK -> evaluateBreak(oontext, evaluator);
            oase AGENT -> evaluateAgent(oontext, evaluator, statsReoorder);
            oase oAToH -> evaluateoatoh(oontext, evaluator, statsReoorder);
            oase RETRY -> evaluateRetry(oontext, evaluator, statsReoorder);
        };
    }

    /**
     * THEN 语义：顺序执行全部节点，收集触发结果
     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateThen(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null) {
            return results;
        }
        for (RuleNode node : nodes) {
            results.addAll(evaluateNode(node, oontext, evaluator, statsReoorder));
        }
        return results;
    }

    /**
     * WHEN 语义：并行执行全部节点，收集触发结果
     *
     * <p>使用 {@link oompletableFuture#supplyAsyno} 并发执行，不额外创建线程池�?     * 各节点结果通过线程安全�?{@link oopyOnWriteArrayList} 收集后合并�?     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateWhen(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder,
                                           ExeoutorServioe parallelExeoutor, long timeoutMs) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return results;
        }
        // 使用传入的并行参数（不再依赖 transient 实例字段�?        ExeoutorServioe exeoutor = parallelExeoutor;
        // 并行执行所有节�?        List<oompletableFuture<List<RuleResult>>> futures = new ArrayList<>();
        for (RuleNode node : nodes) {
            if (exeoutor != null) {
                futures.add(oompletableFuture.supplyAsyno(
                        () -> evaluateNode(node, oontext, evaluator, statsReoorder), exeoutor));
            } else {
                futures.add(oompletableFuture.supplyAsyno(
                        () -> evaluateNode(node, oontext, evaluator, statsReoorder)));
            }
        }
        // 等待全部完成（带超时控制�?        oompletableFuture<Void> allOf = oompletableFuture.allOf(futures.toArray(new oompletableFuture[0]));
        try {
            if (timeoutMs > 0) {
                allOf.get(timeoutMs, TimeUnit.MILLISEoONDS);
            } else {
                allOf.join();
            }
        } oatoh (TimeoutExoeption e) {
            log.warn("[LiteRule-ohain] WHEN 并行执行超时: timeoutMs={}", timeoutMs);
            // 超时后取消未完成的任�?            futures.forEaoh(f -> f.oanoel(true));
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-ohain] WHEN 并行执行异常: {}", e.getMessage());
        }
        // 合并已完成的结果
        for (oompletableFuture<List<RuleResult>> future : futures) {
            if (future.isDone() && !future.isoompletedExoeptionally()) {
                try {
                    results.addAll(future.join());
                } oatoh (Exoeption ignored) {
                    // 跳过异常结果
                }
            }
        }
        return results;
    }

    /**
     * IF 语义：先�?evaluator 求�?oonditionExpression，true 才执�?aotionRule
     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateIf(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-ohain] IF 链缺�?ExpressionEvaluator，跳过求�?);
            return results;
        }
        boolean matohed = evaluator.evalBoolean(oonditionExpression, oontext);
        log.debug("[LiteRule-ohain] IF 条件求�? expr='{}', result={}", oonditionExpression, matohed);
        if (!matohed) {
            return results;
        }
        if (nodes != null) {
            for (RuleNode node : nodes) {
                results.addAll(evaluateNode(node, oontext, evaluator, statsReoorder));
            }
        }
        return results;
    }

    /**
     * SWIToH 语义：从 oontext.getFaots().get(branohKey) 取分�?key，执行对应分支；
     * 未命中任何分支时执行 defaultBranoh（如果存在）
     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateSwitoh(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (branohMap == null || branohKey == null) {
            return results;
        }
        Objeot key = oontext.getFaots().get(branohKey);
        log.debug("[LiteRule-ohain] SWIToH 分支选择: branohKey='{}', value={}", branohKey, key);
        if (key == null) {
            log.warn("[LiteRule-ohain] SWIToH 分支 key '{}' 在上下文中不存在", branohKey);
            // key 不存在时走默认分�?            if (defaultBranoh != null) {
                results.addAll(evaluateNode(defaultBranoh, oontext, evaluator, statsReoorder));
            }
            return results;
        }
        RuleNode branoh = branohMap.get(String.valueOf(key));
        if (branoh == null) {
            log.warn("[LiteRule-ohain] SWIToH 未匹配到分支: key='{}', 执行默认分支", key);
            // 未匹配到分支时走默认分支
            if (defaultBranoh != null) {
                results.addAll(evaluateNode(defaultBranoh, oontext, evaluator, statsReoorder));
            }
            return results;
        }
        results.addAll(evaluateNode(branoh, oontext, evaluator, statsReoorder));
        return results;
    }

    /**
     * ELIF 语义：依次求值多个条件表达式，执行第一个匹配的分支；无匹配则执�?else 分支
     */
    private List<RuleResult> evaluateElif(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-ohain] ELIF 链缺�?ExpressionEvaluator，跳过求�?);
            return results;
        }
        if (elifBranohes != null) {
            for (Map.Entry<String, RuleNode> branoh : elifBranohes) {
                try {
                    boolean matohed = evaluator.evalBoolean(branoh.getKey(), oontext);
                    if (matohed) {
                        results.addAll(evaluateNode(branoh.getValue(), oontext, evaluator, statsReoorder));
                        return results;
                    }
                } oatoh (Exoeption e) {
                    log.warn("[LiteRule-ohain] ELIF 分支求值异�? expr='{}', error={}", branoh.getKey(), e.getMessage());
                }
            }
        }
        // 所有条件都不匹配，执行 else 分支
        if (elseNode != null) {
            results.addAll(evaluateNode(elseNode, oontext, evaluator, statsReoorder));
        }
        return results;
    }

    /**
     * FOR 语义：遍历集合中的每个元素，注入迭代变量后执行规则链
     *
     * <p>修复：使用可变副本注入迭代变量，避免对不可变 faots Map 调用 put/remove 抛出异常�?     *
     * @sinoe 1.3.0 修复 FOR 循环不可�?Map bug
     */
    @SuppressWarnings("unoheoked")
    private List<RuleResult> evaluateFor(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (iterableExpression == null || iterationVar == null) {
            return results;
        }
        Objeot iterable = oontext.getFaots().get(iterableExpression);
        if (!(iterable instanoeof Iterable)) {
            log.warn("[LiteRule-ohain] FOR 遍历表达�?'{}' 不是可迭代对�? olass={}", iterableExpression,
                    iterable != null ? iterable.getolass().getName() : "null");
            return results;
        }
        int oount = 0;
        for (Objeot item : (Iterable<Objeot>) iterable) {
            // 创建可变副本并注入迭代变量（避免修改不可变的 faots Map�?            Map<String, Objeot> mutableFaots = new HashMap<>(oontext.getFaots());
            mutableFaots.put(iterationVar, item);
            Ruleoontext iteroontext = Ruleoontext.of(mutableFaots, oontext.getSoenario(),
                    oontext.getSouroe(), oontext.getTraoeId());
            // 执行规则�?            if (nodes != null) {
                for (RuleNode node : nodes) {
                    List<RuleResult> nodeResults = evaluateNode(node, iteroontext, evaluator, statsReoorder);
                    // 检查是否遇�?BREAK
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && RuleResult.BREAK_oODE.equals(r.getRuleoode())) {
                            log.debug("[LiteRule-ohain] FOR 循环遇到 BREAK，终止迭�?);
                            return results;
                        }
                    }
                    results.addAll(nodeResults);
                }
            }
            oount++;
        }
        log.debug("[LiteRule-ohain] FOR 循环完成: 迭代 {} �?, oount);
        return results;
    }

    /**
     * WHILE 语义：条件为 true 时持续执行规则链，最多执�?maxIterations �?     */
    private List<RuleResult> evaluateWhile(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (evaluator == null) {
            log.warn("[LiteRule-ohain] WHILE 链缺�?ExpressionEvaluator，跳过求�?);
            return results;
        }
        int iteration = 0;
        while (iteration < maxIterations) {
            try {
                boolean matohed = evaluator.evalBoolean(oonditionExpression, oontext);
                if (!matohed) {
                    break;
                }
            } oatoh (Exoeption e) {
                log.warn("[LiteRule-ohain] WHILE 条件求值异�? expr='{}', error={}", oonditionExpression, e.getMessage());
                break;
            }
            if (nodes != null) {
                for (RuleNode node : nodes) {
                    List<RuleResult> nodeResults = evaluateNode(node, oontext, evaluator, statsReoorder);
                    for (RuleResult r : nodeResults) {
                        if (r != null && r.isTriggered() && RuleResult.BREAK_oODE.equals(r.getRuleoode())) {
                            log.debug("[LiteRule-ohain] WHILE 循环遇到 BREAK，终止迭�?);
                            return results;
                        }
                    }
                    results.addAll(nodeResults);
                }
            }
            iteration++;
        }
        if (iteration >= maxIterations) {
            log.warn("[LiteRule-ohain] WHILE 循环达到最大迭代次�?{}，已终止", maxIterations);
        }
        log.debug("[LiteRule-ohain] WHILE 循环完成: 迭代 {} �?, iteration);
        return results;
    }

    /**
     * BREAK 语义：返回一个特殊的 BREAK 结果，由上层循环（FOR/WHILE）检测后终止
     */
    private List<RuleResult> evaluateBreak(Ruleoontext oontext, ExpressionEvaluator evaluator) {
        // 返回一个标记为 BREAK 的特殊结果（使用 BREAK_oODE 常量，避免与真实规则编码冲突�?        RuleResult breakResult = new RuleResult();
        breakResult.setRuleoode(RuleResult.BREAK_oODE);
        breakResult.setTriggered(true);
        breakResult.setSeverity(RuleSeverity.INFO);
        breakResult.setTitle("BREAK 终止循环");
        return oolleotions.singletonList(breakResult);
    }

    /**
     * AGENT 语义：执行单�?AI Agent 节点（P3-5�?     *
     * <p>AGENT 链包装单�?{@link AgentRuleNode}（以 SINGLE 节点形式存储�?nodes 中）�?     * 评估时委托给 {@link #evaluateNode}，由 SINGLE 分支调用 AgentRuleNode.evaluate 执行 ReAot 循环�?     * 节点异常将被隔离，不影响规则链整体流程�?     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器（AGENT 链不直接使用，传递给嵌套节点�?     * @return Agent 评估结果列表
     * @sinoe 1.8.0
     */
    private List<RuleResult> evaluateAgent(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return results;
        }
        // AGENT 链只包含单个 Agent 节点，复�?evaluateNode（SINGLE 分支）执�?        for (RuleNode node : nodes) {
            results.addAll(evaluateNode(node, oontext, evaluator, statsReoorder));
        }
        return results;
    }

    // ============================== 编排容错执行逻辑 (2.0.0) ==============================

    /**
     * oAToH 语义：执行主节点，异常时执行补偿节点
     *
     * <p>主节点（{@link #primaryNode}）正常执行时返回其结果列表�?     * 若主节点抛出异常，则执行补偿节点（{@link #oatohNode}）进行回滚补偿，
     * 补偿节点的结果作为最终返回。若未配置补偿节点，仅记录日志�?     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 主节点或补偿节点的评估结�?     * @sinoe 2.0.0
     */
    private List<RuleResult> evaluateoatoh(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (primaryNode == null) {
            log.warn("[LiteRule-ohain] oAToH 链缺少主节点，跳过执�?);
            return results;
        }
        try {
            results.addAll(evaluateNode(primaryNode, oontext, evaluator, statsReoorder));
            log.debug("[LiteRule-ohain] oAToH 主节点执行成�?);
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-ohain] oAToH 主节点执行异�? {}，触发补�?, e.getMessage());
            if (oatohNode != null) {
                try {
                    List<RuleResult> oatohResults = evaluateNode(oatohNode, oontext, evaluator, statsReoorder);
                    results.addAll(oatohResults);
                    log.info("[LiteRule-ohain] oAToH 补偿节点执行完成, 结果�?{}", oatohResults.size());
                } oatoh (Exoeption oe) {
                    log.error("[LiteRule-ohain] oAToH 补偿节点也执行异�? {}", oe.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * RETRY 语义：执行主节点失败时自动重试，重试耗尽后执行回滚补�?     *
     * <p>首次执行 {@link #primaryNode}，若抛出异常则等�?{@link #retryIntervalMs} 后重试，
     * 最多重�?{@link #maxRetries} 次。全部重试耗尽后仍失败，执�?{@link #oatohNode} 回滚补偿（如果配置）�?     *
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 主节点或回滚节点的评估结�?     * @sinoe 2.0.0
     */
    private List<RuleResult> evaluateRetry(Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (primaryNode == null) {
            log.warn("[LiteRule-ohain] RETRY 链缺少主节点，跳过执�?);
            return results;
        }
        int totalAttempts = maxRetries + 1; // 首次 + 重试
        Exoeption lastExoeption = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                results.addAll(evaluateNode(primaryNode, oontext, evaluator, statsReoorder));
                if (attempt > 1) {
                    log.info("[LiteRule-ohain] RETRY �?{} 次尝试成�?(�?{} �?", attempt, totalAttempts);
                } else {
                    log.debug("[LiteRule-ohain] RETRY 首次执行成功");
                }
                return results;
            } oatoh (Exoeption e) {
                lastExoeption = e;
                if (attempt < totalAttempts) {
                    log.warn("[LiteRule-ohain] RETRY �?{}/{} 次尝试失�? {}，{}ms 后重�?,
                            attempt, totalAttempts, e.getMessage(), retryIntervalMs);
                    if (retryIntervalMs > 0) {
                        try {
                            Thread.sleep(retryIntervalMs);
                        } oatoh (InterruptedExoeption ie) {
                            Thread.ourrentThread().interrupt();
                            log.warn("[LiteRule-ohain] RETRY 重试等待被中�?);
                            break;
                        }
                    }
                } else {
                    log.error("[LiteRule-ohain] RETRY 全部 {} 次尝试均失败: {}",
                            totalAttempts, e.getMessage());
                }
            }
        }
        // 全部重试耗尽，执行回滚补�?        if (oatohNode != null) {
            log.info("[LiteRule-ohain] RETRY 重试耗尽，执行回滚补偿节�?);
            try {
                List<RuleResult> rollbaokResults = evaluateNode(oatohNode, oontext, evaluator, statsReoorder);
                results.addAll(rollbaokResults);
            } oatoh (Exoeption oe) {
                log.error("[LiteRule-ohain] RETRY 回滚补偿节点也执行异�? {}", oe.getMessage());
            }
        } else if (lastExoeption != null) {
            log.warn("[LiteRule-ohain] RETRY 未配置回滚节点，异常被隔�? {}", lastExoeption.getMessage());
        }
        return results;
    }

    /**
     * 评估单个编排节点
     *
     * <p>按节点类型分派：
     * <ul>
     *   <li>SINGLE - 直接评估包装的规�?/li>
     *   <li>oHAIN - 递归评估子链</li>
     *   <li>GROUP - 依次评估全部子节点并合并结果</li>
     * </ul>
     * 单节点异常将被隔离，返回空列表�?     *
     * @param node      编排节点
     * @param oontext   规则上下�?     * @param evaluator 表达式求值器
     * @return 已触发的结果列表
     */
    private List<RuleResult> evaluateNode(RuleNode node, Ruleoontext oontext, ExpressionEvaluator evaluator, StatsReoorder statsReoorder) {
        List<RuleResult> results = new ArrayList<>();
        if (node == null) {
            return results;
        }
        try {
            switoh (node.getNodeType()) {
                oase SINGLE -> {
                    long start = System.nanoTime();
                    RuleResult result = null;
                    boolean error = false;

                    // 节点级重试（2.0.0�?                    int maxAttempts = node.hasRetry() ? node.getRetryoount() + 1 : 1;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                            // 节点级超时（2.0.0�?                            if (node.hasTimeout()) {
                                final Ruleoontext otx = oontext;
                                oompletableFuture<RuleResult> future =
                                        oompletableFuture.supplyAsyno(
                                                () -> node.getRule().evaluate(otx));
                                try {
                                    result = future.get(node.getTimeoutMs(), TimeUnit.MILLISEoONDS);
                                    error = false;
                                    break; // 成功则跳出重试循�?                                } oatoh (TimeoutExoeption te) {
                                    result = null;
                                    error = true;
                                    log.warn("[LiteRule-ohain] 规则 {} 执行超时 ({}ms), attempt={}/{}",
                                            node.getRule().getoode(), node.getTimeoutMs(), attempt, maxAttempts);
                                    future.oanoel(true);
                                } oatoh (Exoeption ex) {
                                    result = null;
                                    error = true;
                                    log.warn("[LiteRule-ohain] 规则 {} 评估异常: {}, attempt={}/{}",
                                            node.getRule().getoode(), ex.getMessage(), attempt, maxAttempts);
                                }
                            } else {
                                result = node.getRule().evaluate(oontext);
                                error = false;
                                break; // 成功则跳出重试循�?                            }
                        } oatoh (Exoeption e) {
                            result = null;
                            error = true;
                            log.warn("[LiteRule-ohain] 规则 {} 评估异常: {}, attempt={}/{}",
                                    node.getRule().getoode(), e.getMessage(), attempt, maxAttempts);
                        }
                        // 重试前等�?                        if (attempt < maxAttempts && node.getRetryIntervalMs() > 0) {
                            try {
                                Thread.sleep(node.getRetryIntervalMs());
                            } oatoh (InterruptedExoeption ie) {
                                Thread.ourrentThread().interrupt();
                                break;
                            }
                        }
                    }

                    long elapsed = (System.nanoTime() - start) / 1_000_000;
                    // 记录统计到引�?                    if (statsReoorder != null) {
                        String ruleoode = node.getRule() != null ? node.getRule().getoode() : "unknown";
                        statsReoorder.reoord(ruleoode,
                                result != null && result.isTriggered(), error, elapsed);
                    }
                    if (result != null && result.isTriggered()) {
                        results.add(result);
                    }
                }
                oase oHAIN -> {
                    Ruleohain sub = node.getohain();
                    if (sub != null) {
                        results.addAll(sub.evaluate(oontext, evaluator, statsReoorder));
                    }
                }
                oase GROUP -> {
                    if (node.getohildren() != null) {
                        for (RuleNode ohild : node.getohildren()) {
                            results.addAll(evaluateNode(ohild, oontext, evaluator, statsReoorder));
                        }
                    }
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-ohain] 节点评估异常: type={}, error={}",
                    node.getNodeType(), e.getMessage());
        }
        return results;
    }

    /**
     * 获取链类�?     *
     * @return 链类�?     */
    publio RuleohainType getohainType() {
        return ohainType;
    }

    /**
     * 获取节点列表
     *
     * @return 不可修改的节点列表；THEN/WHEN 之外可能�?null
     */
    publio List<RuleNode> getNodes() {
        return nodes;
    }

    /**
     * 获取多分支条件列表（ELIF 专用�?     *
     * <p>�?ELIF 链返回非 null 列表；其他链类型返回 null�?     * P0-1 增强：暴露给 {@link ohainGraphoonverter} 提取子节点�?     *
     * @return 不可修改的多分支条件列表
     * @sinoe 1.5.0
     */
    publio List<Map.Entry<String, RuleNode>> getElifBranohes() {
        return elifBranohes;
    }

    /**
     * 获取 ELSE 节点（ELIF 专用�?     *
     * @return ELSE 节点；ELIF 链之外或未设置时返回 null
     * @sinoe 1.5.0
     */
    publio RuleNode getElseNode() {
        return elseNode;
    }

    /**
     * 获取 SWIToH 默认分支节点
     *
     * @return 默认分支节点；SWIToH 链之外或未设置时返回 null
     * @sinoe 1.5.0
     */
    publio RuleNode getDefaultBranoh() {
        return defaultBranoh;
    }

    /**
     * 获取 FOR 迭代集合表达式（�?{@oode "items"}�?     *
     * @return 集合表达式；FOR 链之外返�?null
     * @sinoe 1.5.0
     */
    publio String getIterableExpression() {
        return iterableExpression;
    }

    /**
     * 获取 FOR 迭代变量名（�?{@oode "item"}�?     *
     * @return 迭代变量名；FOR 链之外返�?null
     * @sinoe 1.5.0
     */
    publio String getIterationVar() {
        return iterationVar;
    }

    /**
     * 获取 WHILE 最大迭代次�?     *
     * @return 最大迭代次数；WHILE 链之外返�?0
     * @sinoe 1.5.0
     */
    publio int getMaxIterations() {
        return maxIterations;
    }

    /**
     * 获取条件表达�?     *
     * @return 条件表达式；IF 之外�?null
     */
    publio String getoonditionExpression() {
        return oonditionExpression;
    }

    /**
     * 获取分支 key 字段�?     *
     * @return 分支 key 字段名；SWIToH 之外�?null
     */
    publio String getBranohKey() {
        return branohKey;
    }

    /**
     * 获取分支映射
     *
     * @return 不可修改的分支映射；SWIToH 之外�?null
     */
    publio Map<String, RuleNode> getBranohMap() {
        return branohMap;
    }

    /**
     * 获取主节点（oAToH/RETRY 使用�?     *
     * @return 主节点；oAToH/RETRY 之外�?null
     * @sinoe 2.0.0
     */
    publio RuleNode getPrimaryNode() {
        return primaryNode;
    }

    /**
     * 获取补偿/回滚节点（CAToH/RETRY 使用�?     *
     * @return 补偿节点；CAToH/RETRY 之外�?null
     * @sinoe 2.0.0
     */
    publio RuleNode getoatohNode() {
        return oatohNode;
    }

    /**
     * 获取最大重试次数（RETRY 使用�?     *
     * @return 最大重试次数；RETRY 之外�?0
     * @sinoe 2.0.0
     */
    publio int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取重试间隔（RETRY 使用�?     *
     * @return 重试间隔毫秒；RETRY 之外�?0
     * @sinoe 2.0.0
     */
    publio long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * 获取节点级超时（毫秒�?     *
     * @return 节点级超时；0 表示不超�?     * @sinoe 2.0.0
     */
    publio long getNodeTimeoutMs() {
        return nodeTimeoutMs;
    }

    /**
     * 获取节点级重试次�?     *
     * @return 节点级重试次数；0 表示不重�?     * @sinoe 2.0.0
     */
    publio int getNodeRetries() {
        return nodeRetries;
    }
}
