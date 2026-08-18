package com.njydsz.literule.server.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.expression.ExpressionEngine;

/**
 * 规则断点调试器（F1 断点调试器）
 *
 * <p>对标 URule Pro / QLExpress4 的断点调试能力，提供规则级与表达式节点级断点：
 *
 * <ul>
 *   <li><b>断点管理</b>：按规则编码分组维护断点（新增/删除/启停/查询）
 *   <li><b>会话管理</b>：绑定单条规则创建调试会话，支持挂起/单步/恢复/终止
 *   <li><b>静态访问</b>：{@link #get()} 供 {@code ExpressionRule} / {@code TreeInterpreter}
 *       在求值热路径上以零侵入方式检查断点（未配置时为 null，无任何开销）
 * </ul>
 *
 * <p><b>执行模型</b>：
 *
 * <pre>
 *   ExpressionRule.evaluate（规则级断点）
 *       └─ TreeInterpreter 节点求值（表达式节点级断点：COMPARISON/LOGICAL/VARIABLE/FUNCTION_CALL）
 *             └─ RuleDebugger.checkXxx → DebugSession.pause（挂起求值线程）
 *                   └─ 调试客户端 resume(STEP/RESUME/TERMINATE) → 放行
 * </pre>
 *
 * <p><b>线程安全</b>：断点与会话均使用 {@link ConcurrentHashMap}；当前评估规则编码通过 ThreadLocal
 * 传递（并行评估场景每线程独立，互不干扰）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleDebugger {

  /** 全局单例（供热路径零侵入访问；Spring 容器销毁时置空） */
  private static volatile RuleDebugger INSTANCE;

  /** 断点索引：ruleCode -> 断点列表 */
  private final Map<String, List<Breakpoint>> breakpoints = new ConcurrentHashMap<>();

  /** 会话索引：sessionId -> 会话 */
  private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();

  /** 会话 ID 生成器 */
  private final AtomicLong sessionSeq = new AtomicLong(0);

  /** 断点 ID 生成器 */
  private final AtomicLong breakpointSeq = new AtomicLong(0);

  /** 表达式求值器（用于条件断点评估） */
  private final ExpressionEngine evaluator;

  /** 当前评估规则编码（ThreadLocal，并行评估安全） */
  private static final ThreadLocal<String> CURRENT_RULE_CODE = new ThreadLocal<>();

  /**
   * 构造断点调试器
   *
   * @param evaluator 表达式求值器（用于条件断点评估）
   */
  public RuleDebugger(ExpressionEngine evaluator) {
    this.evaluator = evaluator;
  }

  /** 注册全局单例（Spring Bean 初始化后调用） */
  @PostConstruct
  public void init() {
    INSTANCE = this;
    log.info("[LiteRule-Debug] 规则断点调试器已启用");
  }

  /** 注销全局单例（Spring 容器销毁时调用） */
  @PreDestroy
  public void destroy() {
    if (INSTANCE == this) {
      INSTANCE = null;
    }
  }

  /**
   * 获取全局断点调试器（热路径零侵入访问）
   *
   * @return 调试器实例；未配置时返回 null（调用方应做 null 判断）
   */
  public static RuleDebugger get() {
    return INSTANCE;
  }

  // ==================== 当前评估规则上下文 ====================

  /** 进入规则评估（设置 ThreadLocal 当前规则编码） */
  public static void enterRule(String ruleCode) {
    CURRENT_RULE_CODE.set(ruleCode);
  }

  /** 退出规则评估（清理 ThreadLocal） */
  public static void exitRule() {
    CURRENT_RULE_CODE.remove();
  }

  /**
   * 获取当前评估的规则编码
   *
   * @return 当前规则编码；非调试评估时为 null
   */
  public static String currentRuleCode() {
    return CURRENT_RULE_CODE.get();
  }

  // ==================== 断点管理 ====================

  /**
   * 新增断点（自动生成断点 ID）
   *
   * @param ruleCode 规则编码
   * @param nodeType 表达式节点类型（规则级断点传 null）
   * @param expression 表达式文本（可选）
   * @param condition 条件断点表达式（可选）
   * @return 断点 ID
   */
  public String addBreakpoint(
      String ruleCode, String nodeType, String expression, String condition) {
    Breakpoint bp =
        Breakpoint.builder()
            .id(nextBreakpointId())
            .ruleCode(ruleCode)
            .nodeType(nodeType)
            .expression(expression)
            .condition(condition)
            .enabled(true)
            .build();
    breakpoints.computeIfAbsent(ruleCode, k -> new ArrayList<>()).add(bp);
    log.info(
        "[LiteRule-Debug] 新增断点: id={}, ruleCode={}, nodeType={}, expr={}",
        bp.getId(),
        ruleCode,
        nodeType,
        expression);
    return bp.getId();
  }

  /**
   * 新增规则级断点（规则评估开始前挂起）
   *
   * @param ruleCode 规则编码
   * @return 断点 ID
   */
  public String addRuleBreakpoint(String ruleCode) {
    return addBreakpoint(ruleCode, null, null, null);
  }

  /**
   * 新增表达式节点级断点
   *
   * @param ruleCode 规则编码
   * @param nodeType 节点类型（COMPARISON/LOGICAL/ARITHMETIC/VARIABLE/FUNCTION_CALL/TERNARY）
   * @return 断点 ID
   */
  public String addNodeBreakpoint(String ruleCode, String nodeType) {
    return addBreakpoint(ruleCode, nodeType, null, null);
  }

  /**
   * 删除断点
   *
   * @param breakpointId 断点 ID
   */
  public void removeBreakpoint(String breakpointId) {
    for (List<Breakpoint> list : breakpoints.values()) {
      list.removeIf(bp -> bp.getId().equals(breakpointId));
    }
    log.info("[LiteRule-Debug] 删除断点: id={}", breakpointId);
  }

  /** 删除指定规则的全部断点 */
  public void removeBreakpointsByRule(String ruleCode) {
    breakpoints.remove(ruleCode);
  }

  /** 清空全部断点 */
  public void clearBreakpoints() {
    breakpoints.clear();
  }

  /**
   * 启停断点
   *
   * @param breakpointId 断点 ID
   * @param enabled 是否启用
   */
  public void toggleBreakpoint(String breakpointId, boolean enabled) {
    for (List<Breakpoint> list : breakpoints.values()) {
      for (Breakpoint bp : list) {
        if (bp.getId().equals(breakpointId)) {
          bp.setEnabled(enabled);
          return;
        }
      }
    }
  }

  /**
   * 查询全部断点
   *
   * @return 只读快照
   */
  public List<Breakpoint> listBreakpoints() {
    List<Breakpoint> result = new ArrayList<>();
    breakpoints.values().forEach(result::addAll);
    return List.copyOf(result);
  }

  // ==================== 会话管理 ====================

  /**
   * 创建调试会话（绑定规则编码）
   *
   * @param ruleCode 规则编码
   * @return 会话 ID
   */
  public String createSession(String ruleCode) {
    String sessionId = "dbg-" + System.currentTimeMillis() + "-" + sessionSeq.incrementAndGet();
    DebugSession session = new DebugSession(sessionId, ruleCode);
    sessions.put(sessionId, session);
    log.info("[LiteRule-Debug] 创建调试会话: session={}, ruleCode={}", sessionId, ruleCode);
    return sessionId;
  }

  /**
   * 获取调试会话
   *
   * @param sessionId 会话 ID
   * @return 会话；不存在返回 null
   */
  public DebugSession getSession(String sessionId) {
    return sessions.get(sessionId);
  }

  /**
   * 下发调试指令
   *
   * @param sessionId 会话 ID
   * @param command 调试指令
   * @return true=指令已下发；false=会话不存在
   */
  public boolean submitCommand(String sessionId, DebugCommand command) {
    DebugSession session = sessions.get(sessionId);
    if (session == null) {
      return false;
    }
    session.resume(command);
    return true;
  }

  /**
   * 终止调试会话
   *
   * @param sessionId 会话 ID
   */
  public void terminateSession(String sessionId) {
    DebugSession session = sessions.get(sessionId);
    if (session != null) {
      session.resume(DebugCommand.TERMINATE);
      sessions.remove(sessionId);
      log.info("[LiteRule-Debug] 调试会话已终止: {}", sessionId);
    }
  }

  /**
   * 查询全部活跃会话
   *
   * @return 只读快照
   */
  public List<DebugSession> listSessions() {
    return List.copyOf(sessions.values());
  }

  // ==================== 断点检查（热路径） ====================

  /**
   * 规则级断点检查（ExpressionRule.evaluate 入口调用）
   *
   * <p>命中且存在活跃会话时挂起求值线程，等待调试指令。
   *
   * @param ruleCode 规则编码
   * @param context 规则上下文
   * @return 命中断点；未命中或无可调试会话返回 null
   */
  public BreakpointHit checkRuleBreakpoint(String ruleCode, RuleContext context) {
    DebugSession session = findActiveSession(ruleCode);
    if (session == null) {
      return null;
    }
    List<Breakpoint> ruleBps = breakpoints.get(ruleCode);
    if (ruleBps == null || ruleBps.isEmpty()) {
      return null;
    }
    Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
    for (Breakpoint bp : ruleBps) {
      if (!bp.isEnabled() || !bp.isRuleLevel()) {
        continue;
      }
      if (!matchesHitLimit(bp)) {
        continue;
      }
      if (bp.getCondition() != null && !evaluateCondition(bp.getCondition(), context)) {
        continue;
      }
      bp.setHitCount(bp.getHitCount() + 1);
      BreakpointHit hit =
          BreakpointHit.ruleHit(session.getSessionId(), bp.getId(), ruleCode, facts);
      session.pause(hit);
      return hit;
    }
    return null;
  }

  /**
   * 表达式节点级断点检查（TreeInterpreter 节点求值前调用）
   *
   * @param ruleCode 规则编码
   * @param nodeType 节点类型（COMPARISON/LOGICAL/ARITHMETIC/VARIABLE/FUNCTION_CALL/TERNARY）
   * @param expression 节点表达式文本
   * @param facts 当前变量上下文
   * @return 命中断点；未命中返回 null
   */
  public BreakpointHit checkExpressionBreakpoint(
      String ruleCode, String nodeType, String expression, Map<String, Object> facts) {
    if (ruleCode == null) {
      return null;
    }
    DebugSession session = findActiveSession(ruleCode);
    if (session == null) {
      return null;
    }
    List<Breakpoint> ruleBps = breakpoints.get(ruleCode);
    if (ruleBps == null || ruleBps.isEmpty()) {
      return null;
    }
    for (Breakpoint bp : ruleBps) {
      if (!bp.isEnabled() || bp.isRuleLevel()) {
        continue;
      }
      // 节点类型匹配（未指定 nodeType 视为匹配全部）
      if (bp.getNodeType() != null && !bp.getNodeType().equals(nodeType)) {
        continue;
      }
      // 表达式文本匹配（可选）
      if (bp.getExpression() != null && !bp.getExpression().equals(expression)) {
        continue;
      }
      if (!matchesHitLimit(bp)) {
        continue;
      }
      if (bp.getCondition() != null && !evaluateCondition(bp.getCondition(), facts)) {
        continue;
      }
      bp.setHitCount(bp.getHitCount() + 1);
      String exprText = expression != null ? expression : nodeType;
      BreakpointHit hit =
          BreakpointHit.nodeHit(
              session.getSessionId(), bp.getId(), ruleCode, nodeType, exprText, facts);
      session.pause(hit);
      return hit;
    }
    return null;
  }

  /** 查找规则编码对应的活跃会话（同一规则同时仅支持一个活跃调试会话） */
  private DebugSession findActiveSession(String ruleCode) {
    for (DebugSession session : sessions.values()) {
      if (session.getRuleCode().equals(ruleCode) && session.isActive()) {
        return session;
      }
    }
    return null;
  }

  /** 命中次数阈值判断 */
  private boolean matchesHitLimit(Breakpoint bp) {
    return bp.getHitLimit() <= 0 || bp.getHitCount() < bp.getHitLimit();
  }

  /** 条件断点评估（RuleContext 版本） */
  private boolean evaluateCondition(String condition, RuleContext context) {
    try {
      return evaluator != null && evaluator.evalBoolean(condition, context);
    } catch (Exception e) {
      log.debug("[LiteRule-Debug] 条件断点求值失败: condition={}, err={}", condition, e.getMessage());
      return false;
    }
  }

  /** 条件断点评估（facts Map 版本） */
  private boolean evaluateCondition(String condition, Map<String, Object> facts) {
    try {
      return evaluator != null && evaluator.evalBoolean(condition, RuleContext.of(facts));
    } catch (Exception e) {
      log.debug("[LiteRule-Debug] 条件断点求值失败: condition={}, err={}", condition, e.getMessage());
      return false;
    }
  }

  private String nextBreakpointId() {
    return "bp-" + System.currentTimeMillis() + "-" + breakpointSeq.incrementAndGet();
  }
}
