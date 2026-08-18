package com.njydsz.literule.server.debug;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 调试会话（F1 断点调试器）
 *
 * <p>绑定单条规则编码，管理一次调试执行的状态机与线程挂起/恢复：
 *
 * <pre>
 *   RUNNING --(断点命中)--> PAUSED --(RESUME)--> RUNNING
 *      ^                        |--(STEP_*)--> STEPPING --(下一断点)--> PAUSED
 *      |                        |--(TERMINATE)--> TERMINATED
 *      +----------------- FINISHED（规则评估结束）
 * </pre>
 *
 * <p><b>挂起机制</b>：断点命中时调用 {@link #pause(BreakpointHit)}， 当前求值线程阻塞在
 * {@link CountDownLatch#await()}；调试客户端下发指令后 {@link #resume(DebugCommand)} 放行线程。
 * 每次挂起重建 latch，支持多次挂起/恢复（单步调试）。
 *
 * <p><b>线程安全</b>：命中列表使用 {@link CopyOnWriteArrayList}，状态与指令使用 volatile / AtomicReference。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class DebugSession {

  /** 会话状态 */
  public enum State {
    /** 全速执行中 */
    RUNNING,
    /** 已挂起（等待调试指令） */
    PAUSED,
    /** 单步执行中（放行本次断点，下一断点再挂起） */
    STEPPING,
    /** 已终止（不再挂起） */
    TERMINATED,
    /** 已完成（规则评估结束） */
    FINISHED
  }

  /** 会话 ID */
  @Getter private final String sessionId;

  /** 绑定的规则编码 */
  @Getter private final String ruleCode;

  /** 会话状态 */
  @Getter private volatile State state = State.RUNNING;

  /** 历史命中列表 */
  private final List<BreakpointHit> hits = new CopyOnWriteArrayList<>();

  /** 当前挂起的断点命中 */
  private volatile BreakpointHit currentHit;

  /** 挂起闩锁（每次挂起重建） */
  private volatile CountDownLatch latch = new CountDownLatch(1);

  /** 待处理指令 */
  private final AtomicReference<DebugCommand> pendingCommand = new AtomicReference<>(DebugCommand.RESUME);

  /**
   * 构造调试会话
   *
   * @param sessionId 会话 ID
   * @param ruleCode 绑定的规则编码
   */
  public DebugSession(String sessionId, String ruleCode) {
    this.sessionId = sessionId;
    this.ruleCode = ruleCode;
  }

  /**
   * 断点命中，挂起当前求值线程
   *
   * <p>仅 RUNNING / STEPPING 状态挂起；TERMINATED / FINISHED 直接放行。 挂起后阻塞等待
   * {@link #resume(DebugCommand)}，根据指令决定后续状态。
   *
   * @param hit 断点命中事件
   * @return true=已挂起并等待恢复；false=会话已终止/完成，直接放行
   */
  public boolean pause(BreakpointHit hit) {
    State s = state;
    if (s != State.RUNNING && s != State.STEPPING) {
      return false;
    }
    this.currentHit = hit;
    this.hits.add(hit);
    this.state = State.PAUSED;
    this.pendingCommand.set(DebugCommand.RESUME);
    log.info(
        "[LiteRule-Debug] 断点命中挂起: session={}, ruleCode={}, nodeType={}, expr={}",
        sessionId,
        hit.getRuleCode(),
        hit.getNodeType(),
        hit.getExpression());
    try {
      // 阻塞当前求值线程，等待调试指令
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    DebugCommand command = pendingCommand.get();
    switch (command) {
      case STEP_OVER, STEP_INTO, STEP_OUT -> {
        this.state = State.STEPPING;
        log.debug("[LiteRule-Debug] 会话 {} 单步放行", sessionId);
      }
      case TERMINATE -> {
        this.state = State.TERMINATED;
        log.info("[LiteRule-Debug] 会话 {} 已终止", sessionId);
      }
      default -> {
        this.state = State.RUNNING;
        log.debug("[LiteRule-Debug] 会话 {} 恢复执行", sessionId);
      }
    }
    return true;
  }

  /**
   * 下发调试指令，放行挂起的求值线程
   *
   * @param command 调试指令（RESUME / STEP_* / TERMINATE）
   */
  public void resume(DebugCommand command) {
    if (command == null) {
      command = DebugCommand.RESUME;
    }
    this.pendingCommand.set(command);
    CountDownLatch l = this.latch;
    if (l.getCount() > 0) {
      l.countDown();
    }
  }

  /**
   * 标记会话完成（规则评估正常结束）
   *
   * <p>若存在尚未放行的挂起线程（异常路径），自动放行为 TERMINATE，避免线程永久阻塞。
   */
  public void finish() {
    if (state == State.TERMINATED || state == State.FINISHED) {
      return;
    }
    CountDownLatch l = this.latch;
    if (l.getCount() > 0) {
      l.countDown();
    }
    this.state = State.FINISHED;
    log.info("[LiteRule-Debug] 会话 {} 完成（命中 {} 次）", sessionId, hits.size());
  }

  /**
   * 获取历史命中列表
   *
   * @return 只读快照
   */
  public List<BreakpointHit> getHits() {
    return List.copyOf(hits);
  }

  /**
   * 获取当前挂起的断点命中
   *
   * @return 当前命中；未挂起时为 null
   */
  public BreakpointHit getCurrentHit() {
    return currentHit;
  }

  /**
   * 会话是否仍可挂起
   *
   * @return true=RUNNING 或 STEPPING
   */
  public boolean isActive() {
    State s = state;
    return s == State.RUNNING || s == State.STEPPING;
  }
}
