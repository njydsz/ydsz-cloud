package com.njydsz.literule.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.literule.server.debug.BreakpointHit;
import com.njydsz.literule.server.debug.DebugCommand;
import com.njydsz.literule.server.debug.DebugSession;
import com.njydsz.literule.server.debug.RuleDebugger;

/**
 * 规则断点调试 Controller（P0-F1 / E2 一站式调试入口）
 *
 * <p>断点调试能力，提供：
 *
 * <ul>
 *   <li><b>断点管理</b>：新增/删除/查询规则级与表达式节点级断点（含条件断点）
 *   <li><b>调试会话</b>：创建绑定规则的调试会话，断点命中时挂起求值线程
 *   <li><b>调试指令</b>：RESUME / STEP_OVER / STEP_INTO / STEP_OUT / TERMINATE
 *   <li><b>命中查看</b>：查询会话历史命中（含事实快照与变量值）
 * </ul>
 *
 * <p>与回放（{@link RuleTraceController}）互补：回放是"事后追溯"，断点是"实时暂停"。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/debug")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则断点调试", description = "规则级/表达式节点级断点、调试会话与单步执行")
public class RuleDebugController {

  /** 规则断点调试器（可选，ydsz.literule.debug.enabled=false 时不可用） */
  private final ObjectProvider<RuleDebugger> debuggerProvider;

  /** 获取调试器；未启用时返回 null */
  private RuleDebugger debugger() {
    return debuggerProvider.getIfAvailable();
  }

  /** 未启用调试时的统一错误响应 */
  private YdszResponse<Object> debugDisabled() {
    return YdszResponse.error("规则断点调试未启用（ydsz.literule.debug.enabled=false）");
  }

  // ==================== 断点管理 ====================

  /** 查询全部断点
   * @return 断点列表
   */
  @GetMapping("/breakpoints")
  public YdszResponse<Object> listBreakpoints() {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    return YdszResponse.success(debugger.listBreakpoints());
  }

  /**
   * 新增断点
   *
   * <p>请求体示例（规则级断点）：
   *
   * <pre>
   * {"ruleCode": "EVM_RED_ALERT"}
   * </pre>
   *
   * <p>请求体示例（表达式节点级 + 条件断点）：
   *
   * <pre>
   * {"ruleCode": "EVM_RED_ALERT", "nodeType": "COMPARISON", "condition": "amount > 1000"}
   * </pre>
   *
   * @param request 断点参数（ruleCode 必填，nodeType/expression/condition 可选）
   * @return 断点 ID
   */
  @Audit(
      module = "规则调试",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'addBreakpoint'")
  @PostMapping("/breakpoints")
  public YdszResponse<Object> addBreakpoint(@RequestBody Map<String, String> request) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    String ruleCode = request.get("ruleCode");
    if (ruleCode == null || ruleCode.isBlank()) {
      return YdszResponse.error("ruleCode 不能为空");
    }
    String nodeType = request.get("nodeType");
    String expression = request.get("expression");
    String condition = request.get("condition");
    String breakpointId = debugger.addBreakpoint(ruleCode, nodeType, expression, condition);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("breakpointId", breakpointId);
    result.put("ruleCode", ruleCode);
    result.put("nodeType", nodeType);
    result.put("condition", condition);
    return YdszResponse.success(result);
  }

  /** 删除断点
   * @param breakpointId 断点唯一标识
   * @return 删除结果（true 表示成功）
   */
  @DeleteMapping("/breakpoints/{breakpointId}")
  public YdszResponse<Object> removeBreakpoint(@PathVariable String breakpointId) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    debugger.removeBreakpoint(breakpointId);
    return YdszResponse.success(true);
  }

  // ==================== 调试会话 ====================

  /**
   * 创建调试会话（绑定规则编码）
   *
   * <p>创建后，该规则评估时命中断点将挂起求值线程， 调试客户端通过 {@code submitCommand} 下发指令放行。
   *
   * @param request 会话参数（ruleCode 必填）
   * @return 会话 ID
   */
  @Audit(
      module = "规则调试",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createSession'")
  @PostMapping("/sessions")
  public YdszResponse<Object> createSession(@RequestBody Map<String, String> request) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    String ruleCode = request.get("ruleCode");
    if (ruleCode == null || ruleCode.isBlank()) {
      return YdszResponse.error("ruleCode 不能为空");
    }
    String sessionId = debugger.createSession(ruleCode);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sessionId", sessionId);
    result.put("ruleCode", ruleCode);
    return YdszResponse.success(result);
  }

  /** 查询会话详情（含历史命中）
   * @param sessionId 调试会话唯一标识
   * @return 会话详情（含断点命中历史）
   */
  @GetMapping("/sessions/{sessionId}")
  public YdszResponse<Object> getSession(@PathVariable String sessionId) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    DebugSession session = debugger.getSession(sessionId);
    if (session == null) {
      return YdszResponse.error("会话不存在: " + sessionId);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sessionId", session.getSessionId());
    result.put("ruleCode", session.getRuleCode());
    result.put("state", session.getState().name());
    result.put("hitCount", session.getHits().size());
    result.put("hits", toHitViews(session.getHits()));
    return YdszResponse.success(result);
  }

  /**
   * 下发调试指令
   *
   * <p>指令枚举：RESUME / STEP_OVER / STEP_INTO / STEP_OUT / TERMINATE
   *
   * @param sessionId 会话 ID
   * @param request 指令参数（command 必填）
   * @return 下发结果
   */
  @Audit(
      module = "规则调试",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'submitCommand'")
  @PostMapping("/sessions/{sessionId}/command")
  public YdszResponse<Object> submitCommand(
      @PathVariable String sessionId, @RequestBody Map<String, String> request) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    String commandStr = request.get("command");
    if (commandStr == null || commandStr.isBlank()) {
      return YdszResponse.error("command 不能为空");
    }
    DebugCommand command;
    try {
      command = DebugCommand.valueOf(commandStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return YdszResponse.error(
          "非法的调试指令: " + commandStr + "，合法值: RESUME / STEP_OVER / STEP_INTO / STEP_OUT / TERMINATE");
    }
    boolean ok = debugger.submitCommand(sessionId, command);
    if (!ok) {
      return YdszResponse.error("会话不存在: " + sessionId);
    }
    return YdszResponse.success(true);
  }

  /** 终止调试会话
   * @param sessionId 调试会话唯一标识
   * @return 终止结果（true 表示成功）
   */
  @DeleteMapping("/sessions/{sessionId}")
  public YdszResponse<Object> terminateSession(@PathVariable String sessionId) {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    debugger.terminateSession(sessionId);
    return YdszResponse.success(true);
  }

  /** 查询全部活跃会话
   * @return 活跃调试会话列表
   */
  @GetMapping("/sessions")
  public YdszResponse<Object> listSessions() {
    RuleDebugger debugger = debugger();
    if (debugger == null) {
      return debugDisabled();
    }
    return YdszResponse.success(
        debugger.listSessions().stream()
            .map(
                s -> {
                  Map<String, Object> view = new LinkedHashMap<>();
                  view.put("sessionId", s.getSessionId());
                  view.put("ruleCode", s.getRuleCode());
                  view.put("state", s.getState().name());
                  view.put("hitCount", s.getHits().size());
                  return view;
                })
            .toList());
  }

  /** 命中列表转视图（截断事实快照避免超大响应） */
  private List<Map<String, Object>> toHitViews(List<BreakpointHit> hits) {
    return hits.stream()
        .map(
            h -> {
              Map<String, Object> view = new LinkedHashMap<>();
              view.put("breakpointId", h.getBreakpointId());
              view.put("ruleCode", h.getRuleCode());
              view.put("nodeType", h.getNodeType());
              view.put("expression", h.getExpression());
              view.put("hitAt", h.getHitAt());
              view.put("variableCount", h.getVariables().size());
              return view;
            })
        .toList();
  }
}

