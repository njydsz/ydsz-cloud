package com.njydsz.literule.server.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.spi.GraphExecutionProvider;
import com.njydsz.literule.server.spi.RuleChainGraphProvider;

/**
 * 默认画布执行提供者（P0-A2 画布执行后端下沉）
 *
 * <p>literule 自带的画布执行后端默认实现，将可视化规则链画布还原为可执行编排并执行。
 * 的"画布即执行"能力，不再依赖外部模块提供 {@link GraphExecutionProvider} 实现。
 *
 * <p><b>执行链路</b>：
 *
 * <pre>
 *   dryRunGraph(ruleCode, facts)
 *       → 画布数据源（RuleChainGraphProvider SPI 或内存注册表）
 *       → ChainGraphConverter.toChain(graph, resolver)   （Graph → 可执行 RuleChain）
 *       → RuleChain.evaluate(context, evaluator)          （执行编排，支持 THEN/WHEN/IF/ELIF/SWITCH）
 * </pre>
 *
 * <p><b>画布数据源优先级</b>：
 *
 * <ol>
 *   <li>{@link RuleChainGraphProvider} SPI（消费方实现，如从 DB 加载画布）
 *   <li>内存注册表（{@link #registerGraph} 编程式注册，如从 DSL 导入/启动时预热）
 * </ol>
 *
 * <p>消费方仍可覆盖本实现（装配为 {@code @ConditionalOnMissingBean(GraphExecutionProvider.class)}）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class DefaultGraphExecutionProvider implements GraphExecutionProvider {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** 规则引擎（用于解析画布节点的 Rule 实例） */
  private final RuleEngine ruleEngine;

  /** 表达式求值器（用于 RuleChain 的 IF/ELIF/SWITCH 条件求值） */
  private final ExpressionEngine evaluator;

  /** 画布数据源 SPI（可选，消费方实现） */
  private final RuleChainGraphProvider graphProvider;

  /** 内存画布注册表：ruleCode -> 画布图（编程式注册兜底） */
  private final Map<String, RuleChainGraph> graphRegistry = new ConcurrentHashMap<>();

  /**
   * P1-F4（26.09.23）：Rule 编码 → Rule 实例的 volatile 缓存，避免每次节点解析都线性扫描 getRules()
   *
   * <p>在首次解析或规则引擎注册表变化时惰性重建。使用 volatile 保证多线程可见性。
   */
  private volatile Map<String, Rule> ruleCodeCache;

  /** 上一次重建缓存时的规则数（用于检测规则集变化） */
  private volatile int lastRulesCount = -1;

  /**
   * 构造默认画布执行提供者
   *
   * @param ruleEngine 规则引擎
   * @param evaluator 表达式求值器
   * @param graphProvider 画布数据源 SPI（可为 null，仅使用内存注册表）
   */
  public DefaultGraphExecutionProvider(
      RuleEngine ruleEngine, ExpressionEngine evaluator, RuleChainGraphProvider graphProvider) {
    this.ruleEngine = ruleEngine;
    this.evaluator = evaluator;
    this.graphProvider = graphProvider;
  }

  /**
   * 编程式注册画布到内存注册表
   *
   * @param graph 画布图（graphId 或 ruleCode 作为 key）
   */
  public void registerGraph(RuleChainGraph graph) {
    if (graph == null) {
      return;
    }
    String key = graph.getRuleCode() != null ? graph.getRuleCode() : graph.getGraphId();
    if (key != null) {
      graphRegistry.put(key, graph);
      log.info("[LiteRule-Graph] 画布已注册到内存注册表: key={}, nodes={}, edges={}",
          key, graph.getNodes().size(), graph.getEdges().size());
    }
  }

  /**
   * 注销画布
   *
   * @param ruleCode 规则编码（关联注册表中的规则链画布）
   */
  public void unregisterGraph(String ruleCode) {
    graphRegistry.remove(ruleCode);
  }

  /**
   * 对指定规则的画布执行 Dry-run 仿真（P0-A2）
   *
   * @param ruleCode 规则编码（画布关联 key）
   * @param facts 事实数据
   * @return 评估结果列表；画布为空或转换失败返回空列表
   */
  @Override
  public List<RuleResultVO> dryRunGraph(String ruleCode, Map<String, Object> facts) {
    if (ruleCode == null || ruleCode.isBlank()) {
      return List.of();
    }
    RuleChainGraph graph = resolveGraph(ruleCode);
    if (graph == null) {
      log.warn("[LiteRule-Graph] 画布不存在: ruleCode={}", ruleCode);
      return List.of();
    }

    // 画布结构校验（自环/悬空/孤立节点等，ERROR 级问题阻断执行）
    List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
    if (!RuleGraphValidator.isValid(issues)) {
      log.warn(
          "[LiteRule-Graph] 画布校验未通过，拒绝执行: ruleCode={}, issues={}",
          ruleCode,
          issues.stream().filter(i -> i.getLevel() == RuleGraphValidator.Level.ERROR).toList());
      return List.of();
    }

    try {
      // Graph → 可执行 RuleChain（通过规则引擎解析 Rule 实例）
      RuleChain chain = ChainGraphConverter.toChain(graph, this::resolveRule);
      if (chain == null) {
        log.warn("[LiteRule-Graph] 画布转换为规则链失败: ruleCode={}", ruleCode);
        return List.of();
      }
      RuleContextVO context = RuleContextVO.of(facts != null ? facts : Map.of(), "GRAPH_DRY_RUN", "MANUAL");
      List<RuleResultVO> results = chain.evaluate(context, evaluator);
      log.info("[LiteRule-Graph] 画布执行完成: ruleCode={}, triggered={}", ruleCode, results.size());
      return results;
    } catch (Exception e) {
      log.warn("[LiteRule-Graph] 画布执行异常: ruleCode={}, err={}", ruleCode, e.getMessage());
      return List.of();
    }
  }

  /**
   * 收集画布中引用了但已失效（不存在/已禁用）的规则编码
   *
   * @param ruleCode 规则编码
   * @return 失效规则编码列表（无失效返回空列表）
   */
  @Override
  public List<String> collectInvalidReferences(String ruleCode) {
    if (ruleCode == null) {
      return List.of();
    }
    RuleChainGraph graph = resolveGraph(ruleCode);
    if (graph == null) {
      return List.of(ruleCode);
    }
    List<String> invalid = new ArrayList<>(COLLECTION_CAPACITY);
    for (ChainNodeDTO node : graph.getNodes()) {
      if (!"SINGLE".equals(node.getNodeType()) || node.getRuleCode() == null) {
        continue;
      }
      if (resolveRule(node.getRuleCode()) == null) {
        invalid.add(node.getRuleCode());
      }
    }
    return invalid;
  }

  /** 解析画布：优先 SPI，其次内存注册表 */
  private RuleChainGraph resolveGraph(String ruleCode) {
    if (graphProvider != null) {
      try {
        RuleChainGraph graph = graphProvider.getByRuleCode(ruleCode);
        if (graph != null) {
          return graph;
        }
      } catch (Exception e) {
        log.warn("[LiteRule-Graph] 画布 SPI 查询失败，回退内存注册表: ruleCode={}, err={}",
            ruleCode, e.getMessage());
      }
    }
    return graphRegistry.get(ruleCode);
  }

  /** 按规则编码解析 Rule 实例（P1-F4：通过缓存查找，O(1) 替代 O(N*R) 线性扫描） */
  private Rule resolveRule(String code) {
    if (code == null || ruleEngine == null) {
      return null;
    }
    try {
      Map<String, Rule> cache = getRuleCache();
      return cache.get(code);
    } catch (Exception e) {
      log.debug("[LiteRule-Graph] 规则解析异常: code={}, err={}", code, e.getMessage());
    }
    return null;
  }

  /**
   * P1-F4：获取或重建规则编码缓存
   *
   * <p>通过对比当前规则数与上次缓存时的规则数检测变化。如果规则引擎发生热加载（规则数变化），触发缓存重建。
   */
  private Map<String, Rule> getRuleCache() {
    Map<String, Rule> cache = ruleCodeCache;
    if (cache != null && ruleEngine != null && ruleEngine.getRules().size() == lastRulesCount) {
      return cache;
    }
    return rebuildCache();
  }

  /** 重建规则编码缓存（synchronized 防止并发重建） */
  private synchronized Map<String, Rule> rebuildCache() {
    // 双重检查（其他线程可能已重建）
    if (ruleCodeCache != null && ruleEngine != null
        && ruleEngine.getRules().size() == lastRulesCount) {
      return ruleCodeCache;
    }
    List<Rule> rules = ruleEngine.getRules();
    Map<String, Rule> newCache = new ConcurrentHashMap<>(Math.max(16, rules.size() * 2));
    for (Rule rule : rules) {
      if (rule.getCode() != null) {
        newCache.put(rule.getCode(), rule);
      }
    }
    ruleCodeCache = newCache;
    lastRulesCount = rules.size();
    return newCache;
  }
}
