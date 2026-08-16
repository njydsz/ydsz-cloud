package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认断点注册表与调试器实现（P2-3 / P0-3 落地）
 *
 * <p>基于 {@link ConcurrentHashMap} 维护规则编码集合，支撑断点的增删查。 1.5.1 起落地真实调试能力：
 *
 * <ul>
 *   <li>{@link #onBeforeEvaluate} 命中断点后通过 {@link CountDownLatch} 阻塞， 等待外部通过 {@link #resume}/ {@link
 *       #stepOver} 下发指令
 *   <li>评估前后上下文快照存入 {@link #snapshots}，供 REST 端点拉取查看
 *   <li>SUSPEND 超时自动放行（避免调试端断线导致规则评估永久挂起）
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>
 *   engine.getBreakpointHook().addBreakpoint("CPI_WARN");
 *   // 规则评估时会在 CPI_WARN 前阻塞，等待调试端调用 resume("CPI_WARN")
 *   engine.getBreakpointHook().removeBreakpoint("CPI_WARN");
 * </pre>
 *
 * <p>线程安全：断点集合与快照列表基于并发容器；阻塞 latch 按规则编码隔离， 同一规则同一时刻仅允许一个评估线程进入 SUSPEND。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class DefaultBreakpointHook implements BreakpointHook {

  /** 默认 SUSPEND 超时时间（秒） */
  private static final long DEFAULT_SUSPEND_TIMEOUT_SECONDS = 60;

  /** 已设置断点的规则编码集合 */
  private final Set<String> breakpoints = ConcurrentHashMap.newKeySet();

  /** 是否全局启用断点调试（关闭后即使集合非空也不触发） */
  private volatile boolean enabled = true;

  /** SUSPEND 最大等待时间（秒），超时自动放行，避免调试端断线死锁 */
  private volatile long suspendTimeoutSeconds;

  /** 调试快照列表（评估前后上下文，最多 200 条） */
  private static final int MAX_SNAPSHOTS = 200;

  private final List<Map<String, Object>> snapshots =
      Collections.synchronizedList(new ArrayList<>());

  /** 每个规则编码的挂起 latch + 待下发指令（CONTINUE / STEP_OVER） */
  private final Map<String, SuspendState> suspendStates = new ConcurrentHashMap<>();

  /** 条件断点表达式（2.0.0）：ruleCode → 条件表达式（满足时才挂起） */
  private final Map<String, String> conditionalBreakpoints = new ConcurrentHashMap<>();

  /** Watch 表达式列表（2.0.0）：在断点挂起时求值并返回给调试端 */
  private final List<String> watchExpressions = Collections.synchronizedList(new ArrayList<>());

  /** 默认构造（SUSPEND 超时 60 秒） */
  public DefaultBreakpointHook() {
    this(DEFAULT_SUSPEND_TIMEOUT_SECONDS);
  }

  /**
   * 构造指定 SUSPEND 超时的断点钩子（P1-3 / P2-3）
   *
   * <p>由 {@code LiteRuleProperties.debug.suspend-timeout-seconds} 配置注入。
   *
   * @param suspendTimeoutSeconds SUSPEND 最大等待时间（秒），必须 > 0
   * @since 1.0.0
   */
  public DefaultBreakpointHook(long suspendTimeoutSeconds) {
    if (suspendTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("suspendTimeoutSeconds 必须 > 0");
    }
    this.suspendTimeoutSeconds = suspendTimeoutSeconds;
  }

  /**
   * 添加断点
   *
   * @param ruleCode 规则编码
   */
  public void addBreakpoint(String ruleCode) {
    if (ruleCode != null && !ruleCode.isBlank()) {
      breakpoints.add(ruleCode);
    }
  }

  /**
   * 添加条件断点（2.0.0）
   *
   * <p>仅当条件表达式求值为 true 时才挂起执行。 条件表达式可访问 facts 中的变量。
   *
   * @param ruleCode 规则编码
   * @param condition 条件表达式（null 或空表示无条件断点）
   * @since 1.0.0
   */
  public void addConditionalBreakpoint(String ruleCode, String condition) {
    if (ruleCode != null && !ruleCode.isBlank()) {
      breakpoints.add(ruleCode);
      if (condition != null && !condition.isBlank()) {
        conditionalBreakpoints.put(ruleCode, condition);
      } else {
        conditionalBreakpoints.remove(ruleCode);
      }
    }
  }

  /**
   * 添加 Watch 表达式（2.0.0）
   *
   * @param expression 表达式
   * @since 1.0.0
   */
  public void addWatch(String expression) {
    if (expression != null && !expression.isBlank()) {
      watchExpressions.add(expression);
    }
  }

  /**
   * 移除 Watch 表达式（2.0.0）
   *
   * @param expression 表达式
   * @since 1.0.0
   */
  public void removeWatch(String expression) {
    watchExpressions.remove(expression);
  }

  /**
   * 获取 Watch 表达式列表（2.0.0）
   *
   * @return 不可修改的 Watch 表达式列表
   * @since 1.0.0
   */
  public List<String> getWatchExpressions() {
    return Collections.unmodifiableList(watchExpressions);
  }

  /**
   * 获取条件断点映射（2.0.0）
   *
   * @return 不可修改的条件断点映射
   * @since 1.0.0
   */
  public Map<String, String> getConditionalBreakpoints() {
    return Collections.unmodifiableMap(conditionalBreakpoints);
  }

  /**
   * 移除断点
   *
   * @param ruleCode 规则编码
   */
  public void removeBreakpoint(String ruleCode) {
    if (ruleCode != null) {
      breakpoints.remove(ruleCode);
      conditionalBreakpoints.remove(ruleCode);
      // 清理可能残留的挂起状态
      SuspendState state = suspendStates.remove(ruleCode);
      if (state != null) {
        state.latch.countDown();
      }
    }
  }

  /** 清空全部断点 */
  public void clearBreakpoints() {
    breakpoints.clear();
    conditionalBreakpoints.clear();
    // 唤醒所有挂起的线程
    for (SuspendState state : suspendStates.values()) {
      state.latch.countDown();
    }
    suspendStates.clear();
  }

  /**
   * 获取已设置断点的规则编码集合（只读视图）
   *
   * @return 不可修改的规则编码集合
   */
  public Set<String> getBreakpoints() {
    return Collections.unmodifiableSet(breakpoints);
  }

  /**
   * 设置断点调试总开关
   *
   * @param enabled 是否启用
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 是否启用断点调试
   *
   * @return 是否启用
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 设置 SUSPEND 超时时间
   *
   * @param seconds 超时秒数（默认 60）
   */
  public void setSuspendTimeoutSeconds(long seconds) {
    this.suspendTimeoutSeconds = seconds;
  }

  @Override
  public boolean hasBreakpoint(String ruleCode) {
    if (!enabled || ruleCode == null) {
      return false;
    }
    return breakpoints.contains(ruleCode);
  }

  /**
   * 评估前回调：命中断点时阻塞等待外部指令
   *
   * <p>命中断点后，引擎线程在此阻塞，直到：
   *
   * <ul>
   *   <li>外部调用 {@link #resume(String)} → 返回 CONTINUE，继续评估当前规则
   *   <li>外部调用 {@link #stepOver(String)} → 返回 STEP_OVER，跳过当前规则
   *   <li>超时（默认 60s）→ 返回 CONTINUE，避免死锁
   * </ul>
   */
  @Override
  public BreakpointAction onBeforeEvaluate(BreakpointContext context) {
    recordSnapshot(context);
    SuspendState state = new SuspendState();
    SuspendState prev = suspendStates.putIfAbsent(context.getRuleCode(), state);
    if (prev != null) {
      // 同一规则已有挂起（理论上不会发生，防御性处理）：直接放行
      return BreakpointAction.CONTINUE;
    }
    try {
      boolean signaled = state.latch.await(suspendTimeoutSeconds, TimeUnit.SECONDS);
      if (!signaled) {
        return BreakpointAction.CONTINUE;
      }
      return state.action.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return BreakpointAction.CONTINUE;
    } finally {
      suspendStates.remove(context.getRuleCode());
    }
  }

  /** 评估后回调：记录快照 */
  @Override
  public void onAfterEvaluate(BreakpointContext context) {
    recordSnapshot(context);
  }

  /**
   * 下发"继续"指令（挂起的规则继续评估）
   *
   * @param ruleCode 规则编码
   * @return true=指令已下发；false=规则未处于挂起状态
   */
  public boolean resume(String ruleCode) {
    SuspendState state = suspendStates.get(ruleCode);
    if (state == null) return false;
    state.action.set(BreakpointAction.CONTINUE);
    state.latch.countDown();
    return true;
  }

  /**
   * 下发"单步跳过"指令（跳过当前挂起的规则）
   *
   * @param ruleCode 规则编码
   * @return true=指令已下发；false=规则未处于挂起状态
   */
  public boolean stepOver(String ruleCode) {
    SuspendState state = suspendStates.get(ruleCode);
    if (state == null) return false;
    state.action.set(BreakpointAction.STEP_OVER);
    state.latch.countDown();
    return true;
  }

  /**
   * 查询当前挂起的规则编码列表
   *
   * @return 挂起规则编码集合
   */
  public Set<String> getSuspendedRules() {
    return Collections.unmodifiableSet(suspendStates.keySet());
  }

  /**
   * 获取调试快照列表
   *
   * @return 快照列表（最多 200 条）
   */
  public List<Map<String, Object>> getSnapshots() {
    synchronized (snapshots) {
      return new ArrayList<>(snapshots);
    }
  }

  /** 清空调试快照 */
  public void clearSnapshots() {
    snapshots.clear();
  }

  /** 记录快照 */
  private void recordSnapshot(BreakpointContext ctx) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("phase", ctx.getPhase());
    snapshot.put("traceId", ctx.getTraceId());
    snapshot.put("ruleCode", ctx.getRuleCode());
    snapshot.put("ruleName", ctx.getRuleName());
    snapshot.put("scenario", ctx.getScenario());
    snapshot.put("facts", ctx.getFacts());
    snapshot.put("timestamp", System.currentTimeMillis());
    if (ctx.getResult() != null) {
      snapshot.put("triggered", ctx.getResult().isTriggered());
      snapshot.put(
          "severity",
          ctx.getResult().getSeverity() != null ? ctx.getResult().getSeverity().getCode() : null);
      snapshot.put("title", ctx.getResult().getTitle());
    }
    snapshot.put("elapsedMs", ctx.getElapsedMs());
    if (ctx.getException() != null) {
      snapshot.put("exception", ctx.getException().getMessage());
    }
    snapshots.add(snapshot);
    while (snapshots.size() > MAX_SNAPSHOTS) {
      snapshots.remove(0);
    }
  }

  /** 挂起状态（latch + 待下发动作） */
  private static class SuspendState {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<BreakpointAction> action =
        new AtomicReference<>(BreakpointAction.CONTINUE);
  }
}
