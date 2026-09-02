package com.njydsz.literule.server.core.ParallelRuleEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 规则分组并行评估器（P2-3 高性能优化）
 *
 * <p>将候选规则按互斥组（mutexGroup）分组，组间并行评估、组内串行评估。 对于无互斥组的独立规则，各自独立并行评估。
 *
 * <h3>分组策略</h3>
 *
 * <ul>
 *   <li><b>互斥组规则</b>：同一 mutexGroup 的规则归为同一组，组内按优先级串行评估， 首条命中后同组后续规则跳过（保持互斥语义）
 *   <li><b>独立规则</b>：无 mutexGroup 的规则各自独立成组，可完全并行评估
 * </ul>
 *
 * <h3>并行执行模型</h3>
 *
 * <pre>
 *   ┌─────────┐  ┌─────────┐  ┌─────────────────┐
 *   │ Group A  │  │ Group B  │  │ Independent R3  │
 *   │ R1→R2   │  │ R4→R5   │  │                 │
 *   └────┬────┘  └────┬────┘  └────────┬────────┘
 *        │            │                │
 *        └────────────┴────────────────┘
 *                     │
 *              CompletableFuture.allOf
 *                     │
 *              合并 + 严重度排序
 * </pre>
 *
 * <h3>性能预期</h3>
 *
 * <p>在规则数 100+ 且互斥组较少的场景下，并行评估可将端到端耗时降低 40%~70% （取决于规则评估耗时的均匀性和 CPU 核数）。
 *
 * <h3>线程安全</h3>
 *
 * <p>使用 {@link CompletableFuture} + 固定线程池实现并行评估。 线程池在 {@link #shutdown()} 时优雅关闭。
 *
 * <h3>使用示例</h3>
 *
 * <pre>
 * ParallelRuleEvaluator evaluator = new ParallelRuleEvaluator(4);
 *
 * // 引擎评估时调用
 * List&lt;RuleResultVO&gt; results = evaluator.evaluateParallel(candidateRules, context,
 *         rule -> evaluateSingleRule(rule, context));
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class ParallelRuleEvaluator {

    /** 顺序执行阈值（规则数不超过该值时直接串行执行） */
  private static final int SEQUENTIAL_THRESHOLD = 3;

  /** 线程池终止等待秒数 */
  private static final int AWAIT_TERMINATION_SECONDS = 5;

  /** 线程池任务队列容量 */
  private static final int QUEUE_CAPACITY = 1024;

  /** 默认线程池大小 */
  private static final int DEFAULT_POOL_SIZE =
      Math.max(2, Runtime.getRuntime().availableProcessors());

  /** 线程池 */
  private final Executor executor;

  /** 是否使用内部线程池（外部传入时为 false，不负责关闭） */
  private final boolean internalExecutor;

  /** 统计：并行评估次数 */
  private final AtomicLong parallelEvalCount = new AtomicLong(0);

  /** 统计：分组数累计 */
  private final AtomicLong totalGroups = new AtomicLong(0);

  /** 使用默认线程池大小创建评估器 */
  public ParallelRuleEvaluator() {
    this(DEFAULT_POOL_SIZE);
  }

  /**
   * 指定线程池大小创建评估器
   *
   * @param poolSize 线程池大小
   */
  public ParallelRuleEvaluator(int poolSize) {
    int size = Math.max(1, poolSize);
    this.executor = createExecutor(size);
    this.internalExecutor = true;
    log.info("[ParallelEval] 规则并行评估器已初始化（poolSize={}）", size);
  }

  /**
   * 使用外部线程池创建评估器（common-thread 注入入口）
   *
   * @param executor 外部线程池（调用方负责关闭）
   */
  public ParallelRuleEvaluator(Executor executor) {
    this.executor = Objects.requireNonNull(executor, "executor 不能为 null");
    this.internalExecutor = false;
    log.info("[ParallelEval] 规则并行评估器已初始化（external executor）");
  }

  /**
   * 并行评估规则
   *
   * <p>分组策略：
   *
   * <ol>
   *   <li>将候选规则按 mutexGroup 分组
   *   <li>同一组的规则串行评估，首条命中后同组跳过
   *   <li>不同组并行评估
   *   <li>合并所有组的结果，按严重度倒序排列
   * </ol>
   *
   * @param candidateRules 候选规则列表
   * @param context 规则上下文
   * @param evaluator 单规则评估函数
   * @return 触发的规则结果列表（按严重度倒序）
   */
  public List<RuleResultVO> evaluateParallel(
      List<Rule> candidateRules, RuleContextVO context, RuleEvaluator evaluator) {
    if (candidateRules == null || candidateRules.isEmpty()) {
      return Collections.emptyList();
    }

    // 规则数较少时直接串行评估，避免线程切换开销
    if (candidateRules.size() <= SEQUENTIAL_THRESHOLD) {
      return evaluateSequential(candidateRules, context, evaluator);
    }

    parallelEvalCount.incrementAndGet();

    // 按互斥组分组
    Map<String, List<Rule>> groups = groupByMutex(candidateRules);
    totalGroups.addAndGet(groups.size());

    if (log.isDebugEnabled()) {
      log.debug("[ParallelEval] 并行评估: rules={}, groups={}", candidateRules.size(), groups.size());
    }

    // 每组一个 CompletableFuture，组内串行评估
    List<CompletableFuture<List<RuleResultVO>>> futures = new ArrayList<>(groups.size());
    for (List<Rule> groupRules : groups.values()) {
      CompletableFuture<List<RuleResultVO>> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return evaluateGroup(groupRules, context, evaluator);
                } catch (Exception e) {
                  log.warn("[ParallelEval] 分组评估异常: {}", e.getMessage());
                  return Collections.<RuleResultVO>emptyList();
                }
              },
              executor);
      futures.add(future);
    }

    // 等待全部完成
    CompletableFuture<Void> allDone =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    try {
      allDone.join();
    } catch (Exception e) {
      log.warn("[ParallelEval] 并行评估等待异常: {}", e.getMessage());
    }

    // 合并结果
    List<RuleResultVO> allResults = new ArrayList<>(16);