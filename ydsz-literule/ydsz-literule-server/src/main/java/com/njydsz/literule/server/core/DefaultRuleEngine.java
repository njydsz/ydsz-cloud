package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleEnvironment;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.model.ModelInvocationException;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.StatsRecorderVO;
import com.njydsz.literule.server.model.ModelInputRegistry;
import com.njydsz.literule.server.spi.FactCollectionException;
import com.njydsz.literule.server.spi.FactProviderRegistry;
import com.njydsz.literule.server.spi.RuleActionDispatcher;
import com.njydsz.literule.server.spi.TraceRecorder;

/**
 * 默认规则引擎实现
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>规则注册/注销（线程安全 CopyOnWriteArrayList）
 *   <li>按优先级编排执行（priority 数值越小越先执行）
 *   <li>单规则异常隔离（不影响其他规则）
 *   <li>结果按严重度倒序排列（RED → YELLOW → INFO）
 *   <li>执行统计（执行次数/触发次数/异常次数/耗时）
 *   <li>Dry-run 仿真（返回全部结果含未触发，不记录统计）
 *   <li>执行轨迹异步记录（1.4.0）
 *   <li>单规则超时与熔断（1.4.0）
 *   <li>事实/模型并行注入（1.4.0，委托 {@link FactInjectionService}）
 *   <li>灰度路由评估（1.4.0，委托 {@link CanaryEvaluator}）
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class DefaultRuleEngine implements RuleEngine, StatsRecorderVO {

    /** 默认并行执行阈值 */
  private static final int DEFAULT_PARALLEL_THRESHOLD = 50;

  /** 默认线程池最小线程数 */
  private static final int DEFAULT_MIN_POOL_SIZE = 4;

  /** 默认线程池大小乘数 */
  private static final int DEFAULT_POOL_MULTIPLIER = 2;

/** 纳秒到毫秒的换算系数 */
private static final long NANOS_PER_MILLI = 1_000_000L;

/** 异步记录器终止等待秒数 */
private static final int AWAIT_RECORDER_SECONDS = 5;

/** 注入线程池终止等待秒数 */
private static final int AWAIT_INJECTION_SECONDS = 3;

/** 规则注册表（P0-1：从引擎核心拆出注册/注销/索引维护职责） */
private final RuleRegistry ruleRegistry = new RuleRegistry();

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

  /**
   * 模型输入注册表（可选，1.8.0 起 P3-1 规则+模型融合）
   *
   * <p>非 null 且已注册 provider 时，引擎在评估前调用 {@link ModelInputRegistry#collectAllModelOutputs} 获取模型输出，
   * 合并到 {@link RuleContextVO} 的 facts 中（嵌套在 "model" key 下）， 使规则表达式可通过 {@code model.<field>} 引用（如
   * {@code model.score > 0.8}）。 默认 null（向后兼容，不影响现有评估）。
   */
  private volatile ModelInputRegistry modelInputRegistry;

  /**
   * 事实数据提供者注册表（可选，2.1.0 起 P0-2 动态事实采集管道）
   *
   * <p>非 null 且已注册 provider 时，引擎在评估前调用 {@link FactProviderRegistry#collectAllFacts} 动态采集事实数据， 合并到
   * {@link RuleContextVO} 的 facts 中，使规则表达式可直接引用。 事实采集在模型注入之前执行，采集的事实可供模型 provider 使用。 默认
   * null（向后兼容，不影响现有评估）。
   */
  private volatile FactProviderRegistry factProviderRegistry;

  /**
   * 规则动作分发器（可选，2.1.0 起 P1-1 规则与消息通知联动）
   *
   * <p>非 null 且已注册 handler 时，引擎在评估完成后调用 {@link RuleActionDispatcher#dispatchActions} 分发触发结果，
   * 执行消息通知、工作流触发等后续动作。 默认 null（向后兼容，不影响现有评估）。
   */
  private volatile RuleActionDispatcher actionDispatcher;

  /**
   * 并行规则评估器（可选，2.2.0 起 P2-2 大规则量并行优化）
   *
   * <p>非 null 且候选规则数 ≥ {@link #parallelThreshold} 时， 引擎将候选规则按互斥组分组并行评估，组内串行保持互斥语义。 并行评估期间通过
   * {@link #withMdcTraceId} 为每个工作线程传播 MDC traceId。 默认 null（串行评估，向后兼容）。
   */
  private volatile ParallelRuleEvaluator parallelEvaluator;

  /** 并行评估触发阈值（候选规则数 ≥ 此值时启用并行），默认 50 */
  private volatile int parallelThreshold = DEFAULT_PARALLEL_THRESHOLD;

  /** 统计记录器（封装统计计数器、慢规则检测和告警） */
  private final RuleStatistics statistics = new RuleStatistics();

  /** 轨迹构建器 */
  private final RuleTraceBuilder traceBuilder = new RuleTraceBuilder();

  /** 事实注入服务（P1-1：从引擎核心拆出事实/模型注入职责） */
  private volatile FactInjectionService factInjectionService;

  /** 灰度评估器（P1-1：从引擎核心拆出灰度路由职责） */
  private volatile CanaryEvaluator canaryEvaluator;

  /** 评估结果缓存（P1-7：可选，通过 setEvaluationResultCache 注入） */
  private volatile EvaluationResultCache evaluationResultCache;

  /**
   * 初始化：将引擎内部的 statistics 引用注入 RuleRegistry
   *
   * <p>RuleRegistry 需要 statistics 来清理注销规则的统计数据。 由于 statistics 是 final 字段，通过 {@link PostConstruct} 在构造后注入。
   *
   * @since 26.09.01
   */
  @PostConstruct
  public void initRegistry() {
    ruleRegistry.setStatistics(statistics);
  }

  /**
   * 事实/模型并行注入专用线程池（P1-3 可配置）
   *
   * <p>默认 {@code max(4, CPU * 2)} 线程，分别用于事实采集和模型调用。可通过 {@link #setInjectionExecutor} 替换为配置化线程池。
   * 使用守护线程，不影响 JVM 关闭。
   */
  private volatile ExecutorService injectionExecutor = createDefaultInjectionExecutor();

  /**
   * 创建默认注入线程池
   *
   * <p>线程数 = {@code max(4, CPU 核数 * 2)}，使用守护线程避免阻止 JVM 关闭。
   *
   * @return 默认注入线程池
   */
  private static ExecutorService createDefaultInjectionExecutor() {
    int poolSize =
        Math.max(DEFAULT_MIN_POOL_SIZE, Runtime.getRuntime().availableProcessors() * DEFAULT_POOL_MULTIPLIER);
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 规则注入默认线程池，线程数由 CPU 核数动态计算，守护线程
    ExecutorService executor = ExecutorUtils.newFixedThreadPool(poolSize, "literule-injection");
    // CHECKSTYLE.ON: RegexpSinglelineJava
    return executor;
  }

  /**
   * 设置注入线程池（替代硬编码的默认线程池）
   *
   * <p>由 {@code LiteRuleAutoConfiguration} 调用，根据 {@code ydsz.literule.injection.poolSize} 配置创建线程池。
   * 使用 volatile 引用替换可以安全地在运行时切换（已提交任务继续在新旧线程池中处理）。
   *
   * @param injectionExecutor 自定义注入线程池
   * @since 26.09.01
   */
  public void setInjectionExecutor(ExecutorService injectionExecutor) {
    if (injectionExecutor != null) {
      // 关闭旧线程池（如果已初始化且不是同一个实例）
      ExecutorService old = this.injectionExecutor;
      this.injectionExecutor = injectionExecutor;
      if (old != null && old != injectionExecutor && !old.isShutdown()) {
        old.shutdown();
      }
    }
  }

  /**
   * 注册规则到引擎
   *
   * <p>注册流程：
   *
   * <ol>
   *   <li>校验规则非空且 code 非空
   *   <li>移除同编码旧规则（支持热更新覆盖）
   *   <li>二分查找按 priority 升序插入（增量保序，避免全量 sort）
   *   <li>更新规则索引（租户+环境+场景+互斥组+字段倒排）
   *   <li>规则数首次超过 200 时自动启用索引模式
   * </ol>
   *
   * @param rule 待注册规则；为 null 或 code 为 null 时静默跳过
   */
  @Override
  public void register(Rule rule) {
    ruleRegistry.register(rule);
  }

  /**
   * 注销指定编码的规则
   *
   * <p>委托 {@link RuleRegistry#unregister}，同步清理索引、统计、熔断器状态。
   *
   * @param ruleCode 规则编码；为 null 时静默跳过
   */
  @Override
  public void unregister(String ruleCode) {
    ruleRegistry.unregister(ruleCode);
  }

  /**
   * 获取统计记录器（供外部访问统计信息）
   *
   * @return 统计记录器
   */
  public RuleStatistics getStatistics() {
    return statistics;
  }

  /**
   * 评估上下文中所有匹配规则，返回已触发的规则结果列表
   *
   * <p>执行流程：
   *
   * <ol>
   *   <li>设置 MDC traceId（优先 context.traceId，回退当前线程 MDC，最后生成新值）
   *   <li>（可选）注入外部事实数据到 context（P0-2 动态事实采集）
   *   <li>（可选）注入模型输出到 context（P3-1 规则+模型融合）
   *   <li>索引模式下按租户+环境+场景+互斥组+字段过滤候选规则； 非索引模式线性遍历并逐条过滤
   *   <li>互斥组短路：同组已有规则命中则跳过后续规则
   *   <li>熔断检查：已被熔断的规则跳过评估
   *   <li>灰度路由：按 canaryRatio 分流到候选版本
   *   <li>执行规则评估（可选超时控制）
   *   <li>记录统计、监控指标、熔断结果、执行轨迹
   * </ol>
   *
   * <p>结果按严重度倒序排列（RED → YELLOW → INFO）。 单规则异常不影响其他规则评估（异常隔离）。
   *
   * <p>评估期间 MDC 中设置 traceId，确保全链路日志可追踪； 评估结束后恢复原有 MDC 状态（由 {@link #withMdcTraceId} 保证）。
   *
   * @param context 规则上下文（包含 facts、场景、租户、环境等）
   * @return 已触发的规则结果列表（按严重度倒序）；无触发时返回空列表
   */
  @Override
  public List<RuleResultVO> evaluate(RuleContextVO context) {
    String traceId = resolveTraceId(context);
    return withMdcTraceId(traceId, () -> doEvaluate(context));
  }

  private List<RuleResultVO> doEvaluate(RuleContextVO context) {
    // 1. 准备评估上下文（缓存查询 + 事实/模型注入）
    PreparationResult prep = prepareEvaluationContext(context);
    if (prep.cached != null) {
      return prep.cached;
    }

    // 2. 选择候选规则（索引或全量）
    List<Rule> candidateRules = selectCandidateRules(prep.enrichedContext);

    // 3. 并行评估路径（大规则量场景）
    if (shouldUseParallelEvaluation(candidateRules)) {
      return evaluateInParallel(
          candidateRules, prep.enrichedContext, prep.enrichedContext.getScenario());
    }

    // 4. 串行评估规则
    EvaluationState state = evaluateRules(candidateRules, prep.enrichedContext);

    // 5. 收尾处理（排序、指标、分发、缓存）
    return finalizeResults(state, prep.enrichedContext);
  }

  /** 准备评估上下文结果封装 */
  private record PreparationResult(List<RuleResultVO> cached, RuleContextVO enrichedContext) {}

  /**
   * 准备评估上下文（缓存查询 + 事实/模型注入）
   *
   * <p>职责：
   *
   * <ol>
   *   <li>查询评估结果缓存（P1-7），命中则直接返回
   *   <li>注入外部事实数据（P0-2 动态事实采集）
   *   <li>注入模型输出（P3-1 规则+模型融合）
   * </ol>
   *
   * <p>P0-2 并行优化：当事实注册表和模型注册表均注册了 provider 时，使用 {@link CompletableFuture}
   * 并行执行两者，将注入耗时从 T_fact + T_model 降至 max(T_fact, T_model)。
   *
   * @param context 原始评估上下文
   * @return 包含缓存结果（命中时）或 enriched context（未命中时）
   */
  private PreparationResult prepareEvaluationContext(RuleContextVO context) {
    // P1-7：评估结果缓存查询
    if (evaluationResultCache != null) {
      List<RuleResultVO> cached = evaluationResultCache.get(context);
      if (cached != null) {
        if (log.isDebugEnabled()) {
          log.debug("[LiteRule] 评估结果缓存命中: scenario={}", context.getScenario());
        }
        return new PreparationResult(cached, null);
      }
    }

    RuleContextVO enriched = injectDataInParallel(context);

    return new PreparationResult(null, enriched);
  }

  /**
   * 并行注入事实数据与模型输出（P0-2 并行优化）
   *
   * <p>当 {@link #factInjectionService} 已注入时，委托给 {@link FactInjectionService#injectDataInParallel}。
   * 未注入时，使用原始上下文（向后兼容）。
   *
   * @param context 原始评估上下文
   * @return 合并后的上下文
   */
  private RuleContextVO injectDataInParallel(RuleContextVO context) {
    if (factInjectionService != null) {
      return factInjectionService.injectDataInParallel(context);
    }
    // 未注入 FactInjectionService 时，使用原始上下文（向后兼容）
    return context;
  }

  /**
   * 选择候选规则（索引或全量）
   *
   * <p>职责：
   *
   * <ol>
   *   <li>索引模式下按租户+环境+场景+互斥组+字段过滤候选规则
   *   <li>非索引模式返回全量规则列表
   * </ol>
   *
   * @param context 评估上下文
   * @return 候选规则列表
   */
  private List<Rule> selectCandidateRules(RuleContextVO context) {
    String scenario = context.getScenario();
    String contextTenantId = context.getTenantId();
    String contextEnvironment = context.getEnvironment();

    // P0-1：使用索引查找候选规则（大规则量场景性能优化）
    RuleIndexer indexer = ruleRegistry.getRuleIndexer();
    List<Rule> candidateRules =
        indexer.isIndexEnabled()
            ? indexer.findCandidates(
                contextTenantId, contextEnvironment, scenario, new HashSet<>())
            : ruleRegistry.getRules();

    // P1-2：倒排索引第二层过滤，按 facts 字段进一步缩小候选集
    if (indexer.isIndexEnabled() && indexer.hasFieldIndex()) {
      Set<String> factKeys = context.getFacts().keySet();
      candidateRules = indexer.filterByFacts(candidateRules, factKeys);
    }

    return candidateRules;
  }

  /** 评估状态封装（评估过程中的可变状态） */
  private static class EvaluationState {
    final List<RuleResultVO> triggered = new ArrayList<>();

    /** 互斥组：记录本次评估中已命中的互斥组 */
    final Set<String> triggeredGroups = new HashSet<>();

    int evaluatedCount = 0;
  }

  /**
   * 串行评估规则
   *
   * <p>职责：
   *
   * <ol>
   *   <li>租户/环境/场景过滤（非索引模式）
   *   <li>互斥组短路
   *   <li>熔断检查
   *   <li>执行规则评估并记录统计/监控/熔断/轨迹
   * </ol>
   *
   * @param candidateRules 候选规则列表
   * @param context 评估上下文
   * @return 评估状态（已触发结果、互斥组、评估计数）
   */
  private EvaluationState evaluateRules(List<Rule> candidateRules, RuleContextVO context) {
    EvaluationState state = new EvaluationState();
    String scenario = context.getScenario();
    String contextTenantId = context.getTenantId();
    String contextEnvironment = context.getEnvironment();

    for (Rule rule : candidateRules) {
      // 索引未启用时仍需租户、环境、场景过滤
      if (!ruleRegistry.getRuleIndexer().isIndexEnabled()) {
        if (!Objects.equals(rule.getTenantId(), contextTenantId)) {
          continue;
        }
        if (!environmentMatches(rule, contextEnvironment)) {
          continue;
        }
        if (!shouldEvaluate(rule, scenario)) {
          continue;
        }
      }

      // 互斥组短路：同组内已有规则命中，跳过评估
      String mutexGroup = rule.getMutexGroup();
      if (mutexGroup != null
          && !mutexGroup.isBlank()
          && state.triggeredGroups.contains(mutexGroup)) {
        if (log.isDebugEnabled()) {
          log.debug("[LiteRule] 规则 {} 所属互斥组 {} 已命中，跳过评估", rule.getCode(), mutexGroup);
        }
        continue;
      }

      state.evaluatedCount++;

      // 熔断检查：已被熔断的规则跳过评估
      if (circuitBreaker != null && !circuitBreaker.allowEvaluate(rule.getCode())) {
        log.debug("[LiteRule] 规则 {} 已被熔断，跳过评估", rule.getCode());
        continue;
      }

      // 执行评估并记录统计/监控/熔断/轨迹
      RuleEvaluationOutcome outcome =
          executeAndRecordRuleEvaluation(rule, context, scenario, "[LiteRule]");

      if (outcome.isTriggered) {
        state.triggered.add(outcome.result);
        // 互斥组：记录已命中的组，同组后续规则跳过评估
        if (mutexGroup != null && !mutexGroup.isBlank()) {
          state.triggeredGroups.add(mutexGroup);
        }
      }
    }

    return state;
  }

  /**
   * 收尾处理（排序、指标、分发、缓存）
   *
   * <p>职责：
   *
   * <ol>
   *   <li>按严重度倒序排列结果
   *   <li>记录评估规则数指标
   *   <li>分发动作（P1-1 规则与消息通知联动）
   *   <li>写入评估结果缓存（P1-7）
   * </ol>
   *
   * @param state 评估状态
   * @param context 评估上下文
   * @return 最终的规则结果列表
   */
  private List<RuleResultVO> finalizeResults(EvaluationState state, RuleContextVO context) {
    // 按严重度倒序
    state.triggered.sort(Comparator.comparingInt(RuleResultVO::getSeverityWeight).reversed());
    // 记录本次评估遍历的规则数（用于规则规模监控）
    if (metrics != null) {
      metrics.recordEvaluatedRules(state.evaluatedCount);
    }
    // P1-1 规则与消息通知联动：评估完成后分发动作
    if (actionDispatcher != null && !state.triggered.isEmpty()) {
      actionDispatcher.dispatchActions(state.triggered, context);
    }
    // P1-7：评估结果写入缓存
    if (evaluationResultCache != null) {
      evaluationResultCache.put(context, state.triggered);
    }
    return state.triggered;
  }

  /**
   * 解析规则对应的灰度候选定义
   *
   * <p>当 {@link #canaryEvaluator} 已注入时，委托给 {@link CanaryEvaluator#resolveCanaryDefinition}。
   * 未注入时，返回 null（向后兼容）。
   *
   * @param rule 规则
   * @return 灰度定义；不满足条件返回 null
   * @since 26.09.01
   */
  private RuleDefinitionDTO resolveCanaryDefinition(Rule rule) {
    if (canaryEvaluator != null) {
      return canaryEvaluator.resolveCanaryDefinition(rule);
    }
    // 未注入 CanaryEvaluator 时，返回 null（向后兼容）
    return null;
  }

  /**
   * P0-2 动态事实采集：评估前注入外部数据源事实
   *
   * <p>当 {@link #factProviderRegistry} 非 null 且已注册 provider 时：
   *
   * <ol>
   *   <li>调用 {@link FactProviderRegistry#collectAllFacts} 获取外部数据源事实
   *   <li>合并到 facts 中，构建新的 {@link RuleContextVO}（保留原 scenario/source/traceId/tenantId/environment）
   * </ol>
   *
   * <p>降级策略：
   *
   * <ul>
   *   <li>注册表为空：返回原 context，不影响评估
   *   <li>事实数据为空：返回原 context
   *   <li>抛出 {@link FactCollectionException}（fallbackOnError=false）：异常向上传播中断评估
   * </ul>
   *
   * @param context 原始上下文
   * @return 包含外部事实的新上下文；无需注入时返回原 context
   * @since 26.09.01
   */
  private RuleContextVO injectFactsIfNeeded(RuleContextVO context) {
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
    RuleContextVO enriched =
        RuleContextVO.of(
            mergedFacts,
            context.getScenario(),
            context.getSource(),
            context.getTraceId(),
            context.getTenantId(),
            context.getEnvironment());
    if (log.isDebugEnabled()) {
      log.debug(
          "[LiteRule-Fact] 外部事实已注入: {} 条，合并后 facts 共 {} 条",
          externalFacts.size(),
          mergedFacts.size());
    }
    return enriched;
  }

  /**
   * P3-1 规则+模型融合：评估前注入模型输出
   *
   * <p>当 {@link #modelInputRegistry} 非 null 且已注册 provider 时：
   *
   * <ol>
   *   <li>调用 {@link ModelInputRegistry#collectAllModelOutputs} 获取模型输出 （key 带 "model." 前缀，如
   *       "model.score"）
   *   <li>将扁平 key 转换为嵌套结构 {@code {"model": {"score": ..., ...}}}， 以兼容 LiteExpr 表达式 {@code
   *       model.score} 的属性访问语法
   *   <li>合并到 facts 中，构建新的 {@link RuleContextVO}（保留原 scenario/source/traceId/tenantId/environment）
   * </ol>
   *
   * <p>降级策略：
   *
   * <ul>
   *   <li>注册表为空：返回原 context，不影响评估
   *   <li>模型输出为空：返回原 context（规则中引用 model.xxx 的表达式将返回 false）
   *   <li>抛出 {@link ModelInvocationException}（fallbackOnError=false）：异常向上传播中断评估
   * </ul>
   *
   * @param context 原始上下文
   * @return 包含模型输出的新上下文；无需注入时返回原 context
   * @since 26.09.01
   */
  private RuleContextVO injectModelOutputsIfNeeded(RuleContextVO context) {
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
    // 扁平 key（"model.score"）转换为嵌套结构（{"model": {"score": ...}}）
    Map<String, Object> nestedModel = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : modelOutputs.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(ModelInputRegistry.MODEL_KEY_PREFIX)) {
        nestedModel.put(
            key.substring(ModelInputRegistry.MODEL_KEY_PREFIX.length()), entry.getValue());
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
    RuleContextVO enriched =
        RuleContextVO.of(
            mergedFacts,
            context.getScenario(),
            context.getSource(),
            context.getTraceId(),
            context.getTenantId(),
            context.getEnvironment());
    if (log.isDebugEnabled()) {
      log.debug("[LiteRule-Model] 模型输出已注入: fields={}", nestedModel.keySet());
    }
    return enriched;
  }

  /**
   * 评估并返回最高严重度的规则结果
   *
   * <p>等价于 {@code evaluate(context).get(0)}，仅在需要 Top-1 结果时使用， 避免调用方手动排序取第一个元素。
   *
   * @param context 规则上下文
   * @return 最高严重度的规则结果；无触发时返回 null
   */
  @Override
  public RuleResultVO topResult(RuleContextVO context) {
    List<RuleResultVO> all = evaluate(context);
    return all.isEmpty() ? null : all.get(0);
  }

  /**
   * 仿真评估（dry-run）：返回全部规则结果（含未触发），不记录统计
   *
   * <p>与 {@link #evaluate} 的区别：
   *
   * <ul>
   *   <li>返回全部规则结果（含 triggered=false 的未触发结果）
   *   <li>不记录执行统计、监控指标和执行轨迹
   *   <li>不执行熔断、灰度逻辑
   *   <li>同样遵循租户隔离和环境隔离
   *   <li>同样设置 MDC traceId，确保仿真日志可追踪
   * </ul>
   *
   * <p>适用于规则调试、预检和仿真测试场景。
   *
   * @param context 规则上下文
   * @return 全部匹配规则的结果列表（含未触发）
   */
  @Override
  public List<RuleResultVO> dryRun(RuleContextVO context) {
    return dryRun(context, null, null);
  }

  /**
   * 仿真评估（dry-run，支持短路返回优化）
   *
   * <p>当 {@code limit} 和 {@code minSeverity} 均不为 null 时，
   * 已命中（triggered=true）且严重度不低于 {@code minSeverity} 的结果数量达到 {@code limit} 时立即停止评估。
   * 适用于大规则量场景（>500 条）下仅需查看高严重度命中结果的场景。
   *
   * @param context 规则上下文
   * @param limit 返回结果数量上限（null 表示不限制）
   * @param minSeverity 最低严重度阈值（null 表示不限制）
   * @return 仿真结果列表
   * @since 26.09.01
   */
  public List<RuleResultVO> dryRun(RuleContextVO context, Integer limit, RuleSeverity minSeverity) {
    String traceId = resolveTraceId(context);
    return withMdcTraceId(traceId, () -> doDryRun(context, limit, minSeverity));
  }

  private List<RuleResultVO> doDryRun(RuleContextVO context, Integer limit, RuleSeverity minSeverity) {
    // P0-2 动态事实采集 + P3-1 模型融合：dry-run 同样注入（并行优化）
    context = injectDataInParallel(context);
    List<RuleResultVO> all = new ArrayList<>();
    String contextTenantId = context.getTenantId();
    String contextEnvironment = context.getEnvironment();
    // 短路计数：已命中且满足严重度要求的规则数量
    int qualifiedCount = 0;
    boolean enableShortCircuit = (limit != null && limit > 0) && (minSeverity != null);
    for (Rule rule : ruleRegistry.getRules()) {
      // 短路返回：已收集足够的合格结果
      if (enableShortCircuit && qualifiedCount >= limit) {
        break;
      }
      // 租户隔离（1.5.0）：dry-run 同样仅评估与上下文租户匹配的规则
      if (!Objects.equals(rule.getTenantId(), contextTenantId)) {
        continue;
      }
      // 环境隔离（1.6.0，P1-5）：dry-run 同样遵循环境隔离
      if (!environmentMatches(rule, contextEnvironment)) {
        continue;
      }
      try {
        RuleResultVO result = rule.evaluate(context);
        if (result == null) {
          result = RuleResultVO.notTriggered(rule.getCode());
        }
        all.add(result);
        // 短路计数递增
        if (enableShortCircuit
            && result.isTriggered()
            && result.getSeverity() != null
            && result.getSeverity().getWeight() >= minSeverity.getWeight()) {
          qualifiedCount++;
        }
      } catch (Exception e) {
        all.add(
            RuleResultVO.builder()
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
   *
   * <ol>
   *   <li>{@link RuleContextVO#getTraceId()} — 调用方显式传入的 traceId
   *   <li>当前线程 MDC 中的 traceId — 继承上游链路（如 Web 请求过滤器设置的）
   *   <li>自动生成新 UUID — 确保评估期间日志始终有 traceId
   * </ol>
   *
   * @param context 规则上下文
   * @return 有效 traceId（非 null、非空）
   * @since 26.09.01
   */
  /**
   * 在 MDC 与 {@link RequestContext} 中设置 traceId 执行_supplier，执行完毕后恢复原有上下文状态。
   *
   * <p>替代已删除的 {@code TraceContext.withContext()} 方法。 traceId 双写 {@link RequestContext} 与
   * MDC，保证统一上下文与日志链路一致。
   *
   * @param traceId 要设置的 traceId（null 时不设置，仅执行 supplier）
   * @param supplier 要执行的操作
   * @param <T> 返回类型
   * @return supplier 的返回值
   */
  private <T> T withMdcTraceId(String traceId, Supplier<T> supplier) {
    String previous = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    String previousContext = RequestContext.getTraceId();
    try {
      if (traceId != null) {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, traceId);
        RequestContext.setTraceId(traceId);
      }
      return supplier.get();
    } finally {
      if (previous != null) {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, previous);
      } else {
        MDC.remove(HeaderConstants.MDC_TRACE_ID_KEY);
      }
      if (previousContext != null) {
        RequestContext.setTraceId(previousContext);
      } else {
        RequestContext.remove(RequestContext.KEY_TRACE_ID);
      }
    }
  }

  private String resolveTraceId(RuleContextVO context) {
    String traceId = context.getTraceId();
    if (traceId != null && !traceId.isBlank()) {
      return traceId;
    }
    String contextTraceId = RequestContext.getTraceId();
    if (contextTraceId != null && !contextTraceId.isBlank()) {
      return contextTraceId;
    }
    String mdcTraceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    return mdcTraceId != null ? mdcTraceId : IdGenerator.nextIdStr();
  }

  /**
   * 获取当前已注册的全部规则（只读副本）
   *
   * @return 不可修改的规则列表
   */
  @Override
  public List<Rule> getRules() {
    return List.copyOf(ruleRegistry.getRules());
  }

  /**
   * 获取引擎执行统计快照
   *
   * <p>包含全局统计（总评估次数、总触发次数、总异常次数、总耗时） 和按规则编码的明细统计。统计数据为实时快照，调用后继续累积。
   *
   * @return 引擎统计快照
   */
  @Override
  public RuleEngineStatsVO getStats() {
    return statistics.snapshot(ruleRegistry.size(), metrics != null ? metrics.getLastEvaluatedRules() : 0);
  }

  /** 重置统计 */
  public void resetStats() {
    statistics.reset();
  }

  /**
   * 设置是否启用统计
   *
   * @param statsEnabled 是否启用
   * @since 26.09.01
   */
  public void setStatsEnabled(boolean statsEnabled) {
    statistics.setStatsEnabled(statsEnabled);
  }

  /**
   * 获取是否启用统计
   *
   * @return 是否启用
   * @since 26.09.01
   */
  public boolean isStatsEnabled() {
    return statistics.isStatsEnabled();
  }

  /**
   * 将引擎作为统计记录器暴露给编排层使用
   *
   * @return StatsRecorderVO 实例
   * @since 26.09.01
   */
  public StatsRecorderVO asStatsRecorder() {
    return this;
  }

  /**
   * 设置轨迹记录器
   *
   * @param traceRecorder 轨迹记录器；null 表示禁用 Trace
   * @since 26.09.01
   */
  public void setTraceRecorder(TraceRecorder traceRecorder) {
    this.traceRecorder = traceRecorder;
  }

  /**
   * 获取轨迹记录器
   *
   * @return 轨迹记录器；未配置返回 null
   * @since 26.09.01
   */
  public TraceRecorder getTraceRecorder() {
    return traceRecorder;
  }

  /**
   * 设置超时执行器
   *
   * @param timeoutExecutor 超时执行器；null 表示禁用超时控制
   * @since 26.09.01
   */
  public void setTimeoutExecutor(RuleTimeoutExecutor timeoutExecutor) {
    this.timeoutExecutor = timeoutExecutor;
  }

  /**
   * 获取超时执行器
   *
   * @return 超时执行器；未配置返回 null
   * @since 26.09.01
   */
  public RuleTimeoutExecutor getTimeoutExecutor() {
    return timeoutExecutor;
  }

  /**
   * 设置熔断器
   *
   * @param circuitBreaker 熔断器；null 表示禁用熔断
   * @since 26.09.01
   */
  public void setCircuitBreaker(RuleCircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
    ruleRegistry.setCircuitBreaker(circuitBreaker);
  }

  /**
   * 获取熔断器
   *
   * @return 熔断器；未配置返回 null
   * @since 26.09.01
   */
  public RuleCircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }

  /**
   * 设置监控指标
   *
   * @param metrics 监控指标；null 表示禁用
   * @since 26.09.01
   */
  public void setMetrics(RuleMetrics metrics) {
    this.metrics = metrics;
    statistics.setMetrics(metrics);
    ruleRegistry.setMetrics(metrics);
  }

  /**
   * 获取监控指标
   *
   * @return 监控指标；未配置返回 null
   * @since 26.09.01
   */
  public RuleMetrics getMetrics() {
    return metrics;
  }

  /**
   * 设置灰度路由器
   *
   * @param canaryRouter 灰度路由器；null 表示禁用灰度
   * @since 26.09.01
   */
  public void setCanaryRouter(RuleCanaryRouter canaryRouter) {
    this.canaryRouter = canaryRouter;
  }

  /**
   * 获取灰度路由器
   *
   * @return 灰度路由器；未配置返回 null
   * @since 26.09.01
   */
  public RuleCanaryRouter getCanaryRouter() {
    return canaryRouter;
  }

  /**
   * 获取规则索引器
   *
   * @return 规则索引器实例
   * @since 26.09.01
   */
  public RuleIndexer getRuleIndexer() {
    return ruleRegistry.getRuleIndexer();
  }

  /**
   * 设置事实注入服务（P1-1：从引擎核心拆出事实/模型注入职责）
   *
   * <p>注入后，引擎在评估前调用 {@link FactInjectionService#injectDataInParallel} 注入事实/模型数据。
   *
   * @param factInjectionService 事实注入服务
   * @since 1.4.0
   */
  public void setFactInjectionService(FactInjectionService factInjectionService) {
    this.factInjectionService = factInjectionService;
  }

  /**
   * 获取事实注入服务
   *
   * @return 事实注入服务；未配置返回 null
   * @since 1.4.0
   */
  public FactInjectionService getFactInjectionService() {
    return factInjectionService;
  }

  /**
   * 设置灰度评估器（P1-1：从引擎核心拆出灰度路由职责）
   *
   * <p>注入后，引擎在评估时调用 {@link CanaryEvaluator#resolveCanaryDefinition} 解析灰度定义。
   *
   * @param canaryEvaluator 灰度评估器
   * @since 1.4.0
   */
  public void setCanaryEvaluator(CanaryEvaluator canaryEvaluator) {
    this.canaryEvaluator = canaryEvaluator;
    if (canaryEvaluator != null) {
      this.canaryEnabled = canaryEvaluator.isCanaryEnabled();
    }
  }

  /**
   * 获取灰度评估器
   *
   * @return 灰度评估器；未配置返回 null
   * @since 1.4.0
   */
  public CanaryEvaluator getCanaryEvaluator() {
    return canaryEvaluator;
  }

  /**
   * 设置评估结果缓存（P1-7）
   *
   * <p>注入后，evaluate 方法会先查缓存，命中则直接返回； 未命中则执行评估后写入缓存。规则注册/注销/热加载时自动清除缓存。
   *
   * @param cache 评估结果缓存实例
   * @since 26.09.01
   */
  public void setEvaluationResultCache(EvaluationResultCache cache) {
    this.evaluationResultCache = cache;
    ruleRegistry.setEvaluationResultCache(cache);
  }

  /**
   * 获取评估结果缓存
   *
   * @return 评估结果缓存实例；未配置返回 null
   * @since 26.09.01
   */
  public EvaluationResultCache getEvaluationResultCache() {
    return evaluationResultCache;
  }

  /**
   * 设置是否启用灰度路由
   *
   * @param canaryEnabled 是否启用
   * @since 26.09.01
   */
  public void setCanaryEnabled(boolean canaryEnabled) {
    this.canaryEnabled = canaryEnabled;
  }

  /**
   * 获取是否启用灰度路由
   *
   * @return 是否启用
   * @since 26.09.01
   */
  public boolean isCanaryEnabled() {
    return canaryEnabled;
  }

  /**
   * 设置模型输入注册表（P3-1 规则+模型融合）
   *
   * <p>注入后，引擎在 {@link #evaluate} 前会调用注册表获取模型输出， 合并到 {@link RuleContextVO} 的 facts 中。null
   * 表示禁用模型融合（向后兼容）。
   *
   * @param modelInputRegistry 模型输入注册表；null 表示禁用
   * @since 26.09.01
   */
  public void setModelInputRegistry(ModelInputRegistry modelInputRegistry) {
    this.modelInputRegistry = modelInputRegistry;
    if (modelInputRegistry != null) {
      log.info(
          "[LiteRule-Model] 模型输入注册表已注入 (providers={}, timeoutMs={}, fallbackOnError={})",
          modelInputRegistry.size(),
          modelInputRegistry.getTimeoutMs(),
          modelInputRegistry.isFallbackOnError());
    }
  }

  /**
   * 获取模型输入注册表（P3-1）
   *
   * @return 模型输入注册表；未配置返回 null
   * @since 26.09.01
   */
  public ModelInputRegistry getModelInputRegistry() {
    return modelInputRegistry;
  }

  /**
   * 设置事实数据提供者注册表（P0-2 动态事实采集管道）
   *
   * <p>注入后，引擎在 {@link #evaluate} 前会调用注册表动态采集事实数据， 合并到 {@link RuleContextVO} 的 facts 中。null
   * 表示禁用事实采集（向后兼容）。
   *
   * @param factProviderRegistry 事实数据提供者注册表；null 表示禁用
   * @since 26.09.01
   */
  public void setFactProviderRegistry(FactProviderRegistry factProviderRegistry) {
    this.factProviderRegistry = factProviderRegistry;
    if (factProviderRegistry != null) {
      log.info(
          "[LiteRule-Fact] 事实数据提供者注册表已注入 (providers={}, timeoutMs={}, fallbackOnError={})",
          factProviderRegistry.size(),
          factProviderRegistry.getTimeoutMs(),
          factProviderRegistry.isFallbackOnError());
    }
  }

  /**
   * 获取事实数据提供者注册表（P0-2）
   *
   * @return 事实数据提供者注册表；未配置返回 null
   * @since 26.09.01
   */
  public FactProviderRegistry getFactProviderRegistry() {
    return factProviderRegistry;
  }

  /**
   * 设置规则动作分发器（P1-1 规则与消息通知联动）
   *
   * <p>注入后，引擎在 {@link #evaluate} 完成后会调用分发器， 将触发结果传递给所有已注册的 {@link
   * com.njydsz.literule.server.spi.RuleActionHandler}。 null 表示禁用动作分发（向后兼容）。
   *
   * @param actionDispatcher 动作分发器；null 表示禁用
   * @since 26.09.01
   */
  public void setActionDispatcher(RuleActionDispatcher actionDispatcher) {
    this.actionDispatcher = actionDispatcher;
    if (actionDispatcher != null) {
      log.info("[LiteRule-Action] 规则动作分发器已注入 (handlers={})", actionDispatcher.size());
    }
  }

  /**
   * 获取规则动作分发器（P1-1）
   *
   * @return 动作分发器；未配置返回 null
   * @since 26.09.01
   */
  public RuleActionDispatcher getActionDispatcher() {
    return actionDispatcher;
  }

  /**
   * 设置并行规则评估器（P2-2）
   *
   * <p>设置后，当候选规则数 ≥ {@link #parallelThreshold} 时， 引擎自动切换为并行评估模式。
   *
   * @param parallelEvaluator 并行评估器；null 表示始终串行
   * @since 26.09.01
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
 * @since 26.09.01
 */
public void setParallelThreshold(int threshold) {
this.parallelThreshold = Math.max(1, threshold);
}

/**
 * 设置索引绕过阈值（P1-6：硬编码阈值配置化）
 *
 * <p>当规则数 &lt; 此值时，不启用索引（全量遍历）；
 * 规则数 ≥ 此值时，启用索引加速候选筛选。
 *
 * @param indexBypassThreshold 索引绕过阈值
 * @since 1.4.0
 */
public void setIndexBypassThreshold(int indexBypassThreshold) {
ruleRegistry.setIndexBypassThreshold(indexBypassThreshold);
}

/**
 * 获取索引绕过阈值
 *
 * @return 索引绕过阈值
 * @since 1.4.0
 */
public int getIndexBypassThreshold() {
return ruleRegistry.getIndexBypassThreshold();
}

/**
 * 设置慢规则告警阈值（P2-4）
   *
   * @param thresholdMs 单规则评估耗时阈值（毫秒）；≤ 0 表示关闭慢规则检测
   * @since 26.09.01
   */
  public void setSlowRuleThresholdMs(long thresholdMs) {
    statistics.setSlowRuleThresholdMs(thresholdMs);
    if (thresholdMs > 0) {
      log.info("[LiteRule-Performance] 慢规则告警已启用 (threshold={}ms)", thresholdMs);
    }
  }

  /**
   * 判断是否应使用并行评估（P2-2）
   *
   * <p>同时满足以下条件时返回 true：
   *
   * <ul>
   *   <li>并行评估器已注入（{@code parallelEvaluator != null}）
   *   <li>候选规则数 ≥ {@link #parallelThreshold}
   * </ul>
   *
   * @param candidateRules 候选规则列表
   * @return true 表示应使用并行评估
   * @since 26.09.01
   */
  private boolean shouldUseParallelEvaluation(List<Rule> candidateRules) {
    return parallelEvaluator != null
        && candidateRules.size() >= parallelThreshold;
  }

  /**
   * 单规则评估结果封装（P2-T3：串行/并行路径共享）
   *
   * <p>封装单规则评估的完整结果，供串行路径和并行路径统一使用， 消除两路径间灰度路由 + 评估执行 + 统计/监控/熔断/轨迹记录的重复代码。
   *
   * @since 26.09.01
   */
  private static class RuleEvaluationOutcome {
    final RuleResultVO result;
    final Exception caughtException;
    final long elapsedMs;
    final boolean isTriggered;
    final boolean isError;

    RuleEvaluationOutcome(RuleResultVO result, Exception caughtException, long elapsedMs) {
      this.result = result;
      this.caughtException = caughtException;
      this.elapsedMs = elapsedMs;
      this.isTriggered = result != null && result.isTriggered();
      this.isError =
          caughtException != null
              || (result != null
                  && result.getDescription() != null
                  && result.getDescription().startsWith("评估超时"));
    }
  }

  /**
   * 执行单规则评估并记录统计/监控/熔断/轨迹（P2-T3：串行/并行路径共享核心逻辑）
   *
   * <p>封装以下共享逻辑：
   *
   * <ol>
   *   <li>灰度路由解析与候选版本评估
   *   <li>主版本评估（可选超时控制）
   *   <li>统计记录（{@link #record}）
   *   <li>熔断器结果记录
   *   <li>监控指标记录
   *   <li>异常告警日志
   *   <li>异步执行轨迹记录
   * </ol>
   *
   * <p>串行路径额外处理灰度路由，并行路径额外处理 熔断器预检查和 MDC 传播，这些由各自路径自行实现。
   *
   * @param rule 规则
   * @param context 规则上下文
   * @param scenario 业务场景
   * @param logTag 日志标签（如 "[LiteRule]" 或 "[LiteRule-Parallel]"）
   * @return 评估结果封装
   * @since 26.09.01
   */
  private RuleEvaluationOutcome executeAndRecordRuleEvaluation(
      Rule rule, RuleContextVO context, String scenario, String logTag) {
    long start = System.nanoTime();
    RuleResultVO result = null;
    Exception caughtException = null;
    boolean routedToCanary = false;

    // 灰度路由：仅对带 canaryRatio 的表达式规则生效
    RuleDefinitionDTO canaryDef = resolveCanaryDefinition(rule);
    if (canaryDef != null && canaryEvaluator != null) {
      boolean goCanary = canaryEvaluator.shouldRouteToCanary(canaryDef, context);
      canaryEvaluator.recordBucket(rule.getCode(), goCanary);
      if (goCanary) {
        routedToCanary = true;
        Rule canaryRule = canaryEvaluator.buildCanaryRule(canaryDef);
        try {
          result = evaluateWithOptionalTimeout(canaryRule, context);
        } catch (Exception e) {
          caughtException = e;
        }
        if (result != null) {
          canaryEvaluator.markCanary(result);
        }
        if (log.isDebugEnabled()) {
          log.debug("{} 规则 {} 命中灰度桶，评估候选版本", logTag, rule.getCode());
        }
      }
    }

    // 未路由到灰度桶：评估主版本
    if (!routedToCanary) {
      try {
        result = evaluateWithOptionalTimeout(rule, context);
      } catch (Exception e) {
        caughtException = e;
      }
    }

    long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
    RuleEvaluationOutcome outcome = new RuleEvaluationOutcome(result, caughtException, elapsed);

    // 统计记录（含慢规则检测与告警）
    statistics.record(rule.getCode(), outcome.isTriggered, outcome.isError, elapsed);

    // 熔断器记录结果
    if (circuitBreaker != null) {
      circuitBreaker.recordResult(rule.getCode(), !outcome.isError);
    }

    // 监控指标记录
    if (metrics != null) {
      try {
        metrics.recordEvaluation(
            rule.getCode(),
            scenario,
            outcome.isTriggered,
            result != null ? result.getSeverity() : null,
            outcome.isError,
            elapsed);
      } catch (Exception me) {
        log.debug("{} 指标记录失败: {}", logTag, me.getMessage());
      }
    }

    if (outcome.isError && caughtException != null) {
      log.warn("{} 规则 {} 评估异常: {}", logTag, rule.getCode(), caughtException.getMessage());
    }

    // 异步记录 Trace（即使异常也记录，便于排查）
    if (traceRecorder != null && traceRecorder.isEnabled()) {
      try {
        RuleExecutionTraceVO trace = traceBuilder.buildTrace(context, rule, result, elapsed, caughtException);
        traceRecorder.record(trace);
      } catch (Exception te) {
        log.debug("{} Trace 记录失败: {}", logTag, te.getMessage());
      }
    }

    return outcome;
  }

  /**
   * 带可选超时控制的规则评估（P2-T3 提取）
   *
   * @param rule 规则
   * @param context 规则上下文
   * @return 评估结果
   * @throws Exception 评估异常
   * @since 26.09.01
   */
  private RuleResultVO evaluateWithOptionalTimeout(Rule rule, RuleContextVO context) throws Exception {
    if (timeoutExecutor != null) {
      return timeoutExecutor.evaluateWithTimeout(rule, context, 0);
    }
    return rule.evaluate(context);
  }

  /**
   * 并行评估候选规则（P2-2）
   *
   * <p>将候选规则委托给 {@link ParallelRuleEvaluator#evaluateParallel}， 按互斥组分组并行评估。每个工作线程通过 {@link
   * #withMdcTraceId} 传播 MDC traceId，确保并行评估期间日志可追踪。
   *
   * <p>互斥组短路由 {@link
   * ParallelRuleEvaluator} 内部处理。
   *
   * @param candidateRules 候选规则列表
   * @param context 规则上下文
   * @param scenario 业务场景
   * @return 触发的规则结果列表（按严重度倒序）
   * @since 26.09.01
   */
  private List<RuleResultVO> evaluateInParallel(
      List<Rule> candidateRules, RuleContextVO context, String scenario) {
    String traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    if (log.isDebugEnabled()) {
      log.debug(
          "[LiteRule-Parallel] 并行评估: rules={}, threshold={}",
          candidateRules.size(),
          parallelThreshold);
    }
    List<RuleResultVO> results =
        parallelEvaluator.evaluateParallel(
            candidateRules,
            context,
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
   * <p>封装单规则评估的完整逻辑：MDC 传播 → 熔断检查 → 灰度路由 → 超时控制 → 统计/监控/轨迹记录。返回已触发的结果，未触发或被熔断时返回 null。
   *
   * <p>与串行路径的差异：
   *
   * <ul>
   *   <li>不含互斥组跟踪（由 ParallelRuleEvaluator 处理）
   *   <li>含 MDC traceId 传播（工作线程需要显式设置）
   * </ul>
   *
   * @param rule 规则
   * @param context 规则上下文
   * @param scenario 业务场景
   * @param traceId MDC traceId（用于工作线程传播）
   * @return 已触发的 RuleResultVO；未触发/被熔断返回 null
   * @since 26.09.01
   */
  private RuleResultVO evaluateSingleRule(
      Rule rule, RuleContextVO context, String scenario, String traceId) {
    return withMdcTraceId(
        traceId,
        () -> {
          // 熔断预检查
          if (circuitBreaker != null && !circuitBreaker.allowEvaluate(rule.getCode())) {
            log.debug("[LiteRule-Parallel] 规则 {} 已被熔断，跳过评估", rule.getCode());
            return null;
          }
          // 委托共享评估+记录逻辑（P2-T3：消除串行/并行路径重复代码）
          RuleEvaluationOutcome outcome =
              executeAndRecordRuleEvaluation(rule, context, scenario, "[LiteRule-Parallel]");
          return outcome.isTriggered ? outcome.result : null;
        });
  }


  /**
   * 优雅关闭：释放 TraceRecorder、超时执行器、并行评估器、注入线程池与注册表资源
   *
   * @since 26.09.01
   */
  @PreDestroy
  public void destroy() {
    if (traceRecorder instanceof AsyncTraceRecorder asyncRecorder) {
      asyncRecorder.shutdown(AWAIT_RECORDER_SECONDS);
      log.info("[LiteRule] 异步 Trace 记录器已关闭");
    }
    if (timeoutExecutor != null) {
      timeoutExecutor.shutdown();
    }
    if (parallelEvaluator != null) {
      parallelEvaluator.shutdown();
    }
    // 关闭并行注入线程池（P0-2）
    if (!injectionExecutor.isShutdown()) {
      injectionExecutor.shutdown();
      try {
        if (!injectionExecutor.awaitTermination(AWAIT_INJECTION_SECONDS, TimeUnit.SECONDS)) {
          injectionExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        injectionExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    // 销毁事实注入服务（P1-1：委托给 FactInjectionService）
    if (factInjectionService != null) {
      factInjectionService.destroy();
    }
  }

  /**
   * 判断规则是否应在当前场景下评估
   *
   * <p>过滤规则：
   *
   * <ul>
   *   <li>scenario 为 null 或 "DEFAULT" 时，评估全部规则（向后兼容）
   *   <li>rule.getScope() 为 null 或 "ALL" 时，适用于全部场景
   *   <li>否则仅当 rule.getScope() 与 scenario 匹配时评估
   * </ul>
   *
   * @param rule 规则
   * @param scenario 当前场景
   * @return 是否应评估
   * @since 26.09.01
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
   *
   * <ul>
   *   <li>rule.environment 为 null/空 或 {@link RuleEnvironment#DEFAULT "default"} 时， 匹配任何上下文环境（向后兼容）
   *   <li>rule.environment 非 "default" 时，必须与 contextEnvironment 完全匹配
   * </ul>
   *
   * @param rule 规则
   * @param contextEnvironment 上下文环境标识
   * @return true=匹配；false=不匹配
   * @since 26.09.01
   */
  private boolean environmentMatches(Rule rule, String contextEnvironment) {
    String ruleEnv = rule.getEnvironment();
    if (ruleEnv == null || ruleEnv.isBlank() || RuleEnvironment.DEFAULT.equals(ruleEnv)) {
      return true;
    }
    return ruleEnv.equals(contextEnvironment);
  }

  /**
   * 记录统计（实现 {@link StatsRecorderVO}）
   *
   * <p>委托给 {@link RuleStatistics} 组件，保持向后兼容。
   *
   * @param ruleCode 规则编码
   * @param triggered 是否触发
   * @param error 是否异常
   * @param elapsedMs 耗时
   */
  @Override
  public void record(String ruleCode, boolean triggered, boolean error, long elapsedMs) {
    statistics.record(ruleCode, triggered, error, elapsedMs);
  }
}
