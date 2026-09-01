package com.njydsz.literule.server.orchestrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.StatsRecorderVO;

/**
 * 规则链，支持 THEN/IF/ELIF/SWITCH/WHEN 编排
 *
 * <p>规则编排的核心载体，按 {@link RuleChainType} 决定执行语义：
 *
 * <ul>
 *   <li><b>THEN</b> - 顺序执行：节点依次串行执行，收集触发结果
 *   <li><b>WHEN</b> - 并行执行：基于 {@link CompletableFuture#supplyAsync} 并发执行全部节点，收集触发结果
 *   <li><b>IF</b> - 条件执行：先对 {@link #conditionExpression} 求值，为 true 才执行动作规则
 *   <li><b>ELIF</b> - 多分支条件：依次求值多个条件表达式，执行第一个匹配的分支，无匹配则执行 else 分支
 *   <li><b>SWITCH</b> - 分支选择：从 {@link RuleContextVO#getFacts()} 中按 {@link #branchKey} 取分支 key， 执行
 *       {@link #branchMap} 中对应的分支节点
 * </ul>
 *
 * <p>使用静态工厂方法构建：
 *
 * <pre>
 *   RuleChain.then(r1, r2, r3)                       // 顺序执行
 *   RuleChain.when(r1, r2)                           // 并行执行
 *   RuleChain.ifThen("amount &gt; 1000", actionRule)   // 条件执行
 *   RuleChain.elif(branches, elseRule)               // 多分支条件
 *   RuleChain.switchOn("type", branches)             // 分支选择
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleChain {

    /** 线程池任务队列容量 */
  private static final int QUEUE_CAPACITY = 1024;

  /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  /**
   * WHEN 链专用守护线程池（P1-4）
   *
   * <p>当调用方未提供 parallelExecutor 时，使用此线程池替代 ForkJoinPool.commonPool()， 避免 WHEN
   * 链并行任务污染公共线程池导致其他组件线程饥饿。 使用守护线程确保不阻止 JVM 退出。
   *
   * <p><b>注意：</b>此降级池仅在未通过 {@code ydsz.thread.pools.whenChain} 配置 common-thread 时使用，
   * 生产环境应配置 common-thread 统一管理。
   *
   * <p>P1-T4：提供 {@link #shutdownFallbackExecutor()} 方法用于优雅关闭， 建议在应用关闭时（如 @PreDestroy 方法中）调用。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava - 降级兜底，common-thread 未配置时使用
  private static final ExecutorService WHEN_FALLBACK_EXECUTOR =
      ExecutorUtils.builder()
          .corePoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
          .maxPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
          .keepAliveTime(0L, TimeUnit.MILLISECONDS)
          .queueCapacity(QUEUE_CAPACITY)
          .threadNamePrefix("literule-when-fallback")
          .daemon(true)
          .build();
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /**
   * 优雅关闭 WHEN 回退线程池（P1-T4）
   *
   * <p>建议在 Spring 容器关闭时调用（如通过 @PreDestroy 方法）。 由于线程池使用守护线程，即使不显式关闭也不会阻止 JVM 退出， 但显式关闭可以更快速地释放线程资源。
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

  /**
   * 私有构造，统一通过工厂方法创建
   *
   * @param chainType 链类型
   * @param nodes 节点列表
   * @param conditionExpression 条件表达式
   * @param branchKey 分支 key 字段名
   * @param branchMap 分支映射
   * @param defaultBranch SWITCH 默认分支节点
   * @param elifBranches 多分支条件列表
   * @param elseNode ELSE 分支节点
   */
  private RuleChain(
      RuleChainType chainType,
      List<RuleNode> nodes,
      String conditionExpression,
      String branchKey,
      Map<String, RuleNode> branchMap,
      RuleNode defaultBranch,
      List<Map.Entry<String, RuleNode>> elifBranches,
      RuleNode elseNode) {
    this.chainType = chainType;
    this.nodes = nodes;
    this.conditionExpression = conditionExpression;
    this.branchKey = branchKey;
    this.branchMap = branchMap;
    this.defaultBranch = defaultBranch;
    this.elifBranches = elifBranches;
    this.elseNode = elseNode;
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
    return new RuleChain(
        RuleChainType.THEN,
        Collections.unmodifiableList(nodeList),
        null,
        null,
        null,
        null,
        null,
        null);
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
    return new RuleChain(
        RuleChainType.WHEN,
        Collections.unmodifiableList(nodeList),
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * 构建条件执行链（IF）
   *
   * @param conditionExpression 条件表达式（求值为 true 才执行 actionRule）
   * @param actionRule 动作规则
   * @return IF 类型规则链
   */
  public static RuleChain ifThen(String conditionExpression, Rule actionRule) {
    Objects.requireNonNull(conditionExpression, "conditionExpression 不能为 null");
    Objects.requireNonNull(actionRule, "actionRule 不能为 null");
    List<RuleNode> nodeList = Collections.singletonList(RuleNode.of(actionRule));
    return new RuleChain(
        RuleChainType.IF,
        nodeList,
        conditionExpression,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * 构建分支选择链（SWITCH）
   *
   * @param branchKey 分支 key 字段名（从上下文事实中取值）
   * @param branches 分支映射：分支 key -&gt; 分支规则
   * @return SWITCH 类型规则链
   */
  public static RuleChain switchOn(String branchKey, Map<String, Rule> branches) {
    return switchOn(branchKey, branches, null);
  }

  /**
   * 构建分支选择链（SWITCH），指定默认分支
   *
   * @param branchKey 分支 key 字段名（从上下文事实中取值）
   * @param branches 分支映射：分支 key -&gt; 分支规则
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
    return new RuleChain(
        RuleChainType.SWITCH,
        null,
        null,
        branchKey,
        Collections.unmodifiableMap(nodeMap),
        defaultNode,
        null,
        null);
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
    return new RuleChain(
        RuleChainType.ELIF,
        null,
        null,
        null,
        null,
        null,
        Collections.unmodifiableList(branchList),
        elseNode);
  }

  /**
   * 评估规则链
   *
   * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表。 单节点异常将被隔离（记录日志并跳过），不影响其他节点。
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器（IF/SWITCH 嵌套链需要）
   * @return 已触发的规则结果列表；无触发返回空列表
   */
  public List<RuleResultVO> evaluate(RuleContextVO context, ExpressionEngine evaluator) {
    return evaluate(context, evaluator, null);
  }

  /**
   * 评估规则链（带统计记录）
   *
   * <p>按链类型分派执行语义，返回已触发（triggered=true）的结果列表。 若提供 {@link StatsRecorderVO}，将对 SINGLE 节点的规则评估记录执行统计。
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器（IF/SWITCH 嵌套链需要）
   * @param statsRecorder 统计记录器（可为 null，表示不记录统计）
   * @return 已触发的规则结果列表；无触发返回空列表
   * @since 1.0.0
   */
  public List<RuleResultVO> evaluate(
      RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    return evaluate(context, evaluator, statsRecorder, null, 0);
  }

  /**
   * 评估规则链（带统计记录、并行线程池和超时控制）
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @param statsRecorder 统计记录器（可为 null）
   * @param parallelExecutor 并行执行线程池（WHEN 链使用，null 则用 ForkJoinPool）
   * @param timeoutMs 超时毫秒（0=不超时）
   * @return 已触发的规则结果列表
   * @since 1.0.0
   */
  public List<RuleResultVO> evaluate(
      RuleContextVO context,
      ExpressionEngine evaluator,
      StatsRecorderVO statsRecorder,
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
    };
  }

  /**
   * THEN 语义：顺序执行全部节点，收集触发结果
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @return 已触发的结果列表
   */
  private List<RuleResultVO> evaluateThen(
      RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    List<RuleResultVO> results = new ArrayList<>();
    if (nodes == null) {
      return results;
    }
    for (RuleNode node : nodes) {
      results.addAll(evaluateNode(node, context, evaluator, statsRecorder));
    }
    return results;
  }

  /**
   * WHEN 语义：并行执行全部节点，收集触发结果
   *
   * <p>使用 {@link CompletableFuture#supplyAsync} 并发执行，不额外创建线程池。 各节点结果通过线程安全的 {@link
   * CopyOnWriteArrayList} 收集后合并。
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @return 已触发的结果列表
   */
  private List<RuleResultVO> evaluateWhen(
      RuleContextVO context,
      ExpressionEngine evaluator,
      StatsRecorderVO statsRecorder,
      ExecutorService parallelExecutor,
      long timeoutMs) {
    List<RuleResultVO> results = new ArrayList<>();
    if (nodes == null || nodes.isEmpty()) {
      return results;
    }
    // 使用传入的并行参数（不再依赖 transient 实例字段）
    // P1-4: 当调用方未提供 executor 时，使用专用守护线程池而非 ForkJoinPool.commonPool()
    ExecutorService executor = parallelExecutor != null ? parallelExecutor : WHEN_FALLBACK_EXECUTOR;
    // 并行执行所有节点
    List<CompletableFuture<List<RuleResultVO>>> futures = new ArrayList<>();
    for (RuleNode node : nodes) {
      futures.add(
          CompletableFuture.supplyAsync(
              () -> evaluateNode(node, context, evaluator, statsRecorder), executor));
    }
    // 等待全部完成（带超时控制）
    CompletableFuture<Void> allOf =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
    for (CompletableFuture<List<RuleResultVO>> future : futures) {
      if (future.isDone() && !future.isCompletedExceptionally()) {
        try {
          results.addAll(future.join());
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
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @return 已触发的结果列表
   */
  private List<RuleResultVO> evaluateIf(
      RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    List<RuleResultVO> results = new ArrayList<>();
    if (evaluator == null) {
      log.warn("[LiteRule-Chain] IF 链缺少 ExpressionEngine，跳过求值");
      return results;
    }
    boolean matched = evaluator.evalBoolean(conditionExpression, context);
    log.debug("[LiteRule-Chain] IF 条件求值: expr='{}', result={}", conditionExpression, matched);
    if (!matched) {
      return results;
    }
    if (nodes != null) {
      for (RuleNode node : nodes) {
        results.addAll(evaluateNode(node, context, evaluator, statsRecorder));
      }
    }
    return results;
  }

  /**
   * SWITCH 语义：从 context.getFacts().get(branchKey) 取分支 key，执行对应分支； 未命中任何分支时执行 defaultBranch（如果存在）
   *
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @return 已触发的结果列表
   */
  private List<RuleResultVO> evaluateSwitch(
      RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    List<RuleResultVO> results = new ArrayList<>();
    if (branchMap == null || branchKey == null) {
      return results;
    }
    Object key = context.getFacts().get(branchKey);
    log.debug("[LiteRule-Chain] SWITCH 分支选择: branchKey='{}', value={}", branchKey, key);
    if (key == null) {
      log.warn("[LiteRule-Chain] SWITCH 分支 key '{}' 在上下文中不存在", branchKey);
      // key 不存在时走默认分支
      if (defaultBranch != null) {
        results.addAll(evaluateNode(defaultBranch, context, evaluator, statsRecorder));
      }
      return results;
    }
    RuleNode branch = branchMap.get(String.valueOf(key));
    if (branch == null) {
      log.warn("[LiteRule-Chain] SWITCH 未匹配到分支: key='{}', 执行默认分支", key);
      // 未匹配到分支时走默认分支
      if (defaultBranch != null) {
        results.addAll(evaluateNode(defaultBranch, context, evaluator, statsRecorder));
      }
      return results;
    }
    results.addAll(evaluateNode(branch, context, evaluator, statsRecorder));
    return results;
  }

  /** ELIF 语义：依次求值多个条件表达式，执行第一个匹配的分支；无匹配则执行 else 分支 */
  private List<RuleResultVO> evaluateElif(
      RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    List<RuleResultVO> results = new ArrayList<>();
    if (evaluator == null) {
      log.warn("[LiteRule-Chain] ELIF 链缺少 ExpressionEngine，跳过求值");
      return results;
    }
    if (elifBranches != null) {
      for (Map.Entry<String, RuleNode> branch : elifBranches) {
        try {
          boolean matched = evaluator.evalBoolean(branch.getKey(), context);
          if (matched) {
            results.addAll(evaluateNode(branch.getValue(), context, evaluator, statsRecorder));
            return results;
          }
        } catch (Exception e) {
          log.warn(
              "[LiteRule-Chain] ELIF 分支求值异常: expr='{}', error={}", branch.getKey(), e.getMessage());
        }
      }
    }
    // 所有条件都不匹配，执行 else 分支
    if (elseNode != null) {
      results.addAll(evaluateNode(elseNode, context, evaluator, statsRecorder));
    }
    return results;
  }

  /**
   * 评估单个编排节点
   *
   * <p>按节点类型分派：
   *
   * <ul>
   *   <li>SINGLE - 直接评估包装的规则
   *   <li>CHAIN - 递归评估子链
   *   <li>GROUP - 依次评估全部子节点并合并结果
   * </ul>
   *
   * 单节点异常将被隔离，返回空列表。
   *
   * @param node 编排节点
   * @param context 规则上下文
   * @param evaluator 表达式求值器
   * @return 已触发的结果列表
   */
  private List<RuleResultVO> evaluateNode(
      RuleNode node, RuleContextVO context, ExpressionEngine evaluator, StatsRecorderVO statsRecorder) {
    List<RuleResultVO> results = new ArrayList<>();
    if (node == null) {
      return results;
    }
    try {
      switch (node.getNodeType()) {
        case SINGLE -> {
          long start = System.nanoTime();
          RuleResultVO result = null;
          boolean error = false;
          try {
            result = node.getRule().evaluate(context);
          } catch (Exception e) {
            result = null;
            error = true;
            log.warn(
                "[LiteRule-Chain] 规则 {} 评估异常: {}",
                node.getRule().getCode(),
                e.getMessage());
          }
          long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
          // 记录统计到引擎
          if (statsRecorder != null) {
            String ruleCode = node.getRule() != null ? node.getRule().getCode() : "unknown";
            statsRecorder.record(ruleCode, result != null && result.isTriggered(), error, elapsed);
          }
          if (result != null && result.isTriggered()) {
            results.add(result);
          }
        }
        case CHAIN -> {
          RuleChain sub = node.getChain();
          if (sub != null) {
            results.addAll(sub.evaluate(context, evaluator, statsRecorder));
          }
        }
        case GROUP -> {
          if (node.getChildren() != null) {
            for (RuleNode child : node.getChildren()) {
              results.addAll(evaluateNode(child, context, evaluator, statsRecorder));
            }
          }
        }
        default -> log.warn("[RuleChain] 未知节点类型，跳过: {}", node.getNodeType());

      }
    } catch (Exception e) {
      log.warn("[LiteRule-Chain] 节点评估异常: type={}, error={}", node.getNodeType(), e.getMessage());
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
   * <p>仅 ELIF 链返回非 null 列表；其他链类型返回 null。 P0-1 增强：暴露给 {@link ChainGraphConverter} 提取子节点。
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
