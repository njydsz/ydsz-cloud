package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.dto.AgentTraceDetailDTO;
import com.njydsz.agent.domain.dto.AgentTraceListDTO;
import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.trace.TraceMeta;
import com.njydsz.agent.domain.trace.TraceRecorder.TraceStep;
import com.njydsz.agent.server.debug.AgentDebuggerService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 调试 REST API Controller。
 *
 * <p>提供 Agent 执行链路（Trace）的查询和重放接口，是开发调试和问题排查的关键工具：
 *
 * <ul>
 *   <li>{@code GET /agent/debug/traces} - 列出最近执行链路
 *   <li>{@code GET /agent/debug/trace/{traceId}} - 查询链路详情（含步骤摘要）
 *   <li>{@code POST /agent/debug/trace/{traceId}/replay} - 重放指定链路（从历史输入重新执行）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>链路列表查询：返回最近 N 条链路元数据（traceId / conversationId / agentId / status / 耗时）
 *   <li>链路详情查询：返回完整执行步骤的可读摘要（步骤编号 / 步骤类型 / 步骤内容）
 *   <li>链路重放：从历史链路中提取原始输入（conversationId + userInput + agentId）重新执行 Agent
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>重放接口加 {@link Idempotent} 防重（5s TTL）
 *   <li>重放接口加 {@link RateLimit} 限流（50 QPS）
 *   <li>重放操作加 {@link Audit} 异步落库审计日志（含 traceId 便于追溯）
 *   <li>查询接口不写操作，仅依赖链路存储层的访问控制
 * </ul>
 *
 * <h3>使用建议</h3>
 *
 * 调试链路存储在内存（{@code InMemoryTraceRecorder}），重启后丢失； 生产环境建议接入持久化存储（如 ClickHouse / ES），并配置合理的保留周期。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/debug")
public class DebugController {

  /** Agent 调试服务（封装链路查询 + 重放能力） */
  private final AgentDebuggerService agentDebuggerService;

  public DebugController(AgentDebuggerService agentDebuggerService) {
    this.agentDebuggerService = agentDebuggerService;
  }

  /**
   * 列出最近执行链路。
   *
   * <p>按 trace 开始时间倒序返回最近 {@code limit} 条链路元数据； 步骤数（{@code stepCount}）由链路存储层在元数据查询时批量填充，
   * 避免逐条查询步骤列表的 N+1 开销。
   *
   * @param limit 最大数量（默认 20，建议不超过 100）
   * @return 统一响应结果，data 为 {@link AgentTraceListDTO} 列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DEBUG_VIEW)
  @Audit(
      module = "调试管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'listTraces'")
  @GetMapping("/traces")
  public YdszResponse<List<AgentTraceListDTO>> listTraces(
      @RequestParam(defaultValue = "20") int limit) {
    List<TraceMeta> metas = agentDebuggerService.listTraceMetas(limit);
    List<AgentTraceListDTO> dtos =
        metas.stream()
            .map(
                meta ->
                    new AgentTraceListDTO(
                        meta.traceId(),
                        meta.conversationId(),
                        meta.agentId(),
                        meta.status(),
                        meta.startedAt(),
                        meta.totalDurationMs(),
                        meta.stepCount()))
            .collect(Collectors.toList());
    return YdszResponse.success(dtos);
  }

  /**
   * 查询链路详情。
   *
   * <p>返回指定 traceId 的完整执行步骤摘要（{@code [stepIndex] stepType: content} 多行格式）， 供前端"链路详情"面板渲染。
   *
   * @param traceId 链路 ID
   * @return 统一响应结果，data 为 {@link AgentTraceDetailDTO}（含 traceId / agentType / 步骤摘要 plan）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DEBUG_VIEW)
  @Audit(
      module = "调试管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'getTrace: ' + #traceId")
  @GetMapping("/trace/{traceId}")
  public YdszResponse<AgentTraceDetailDTO> getTrace(@PathVariable String traceId) {
    TraceMeta meta = agentDebuggerService.getTraceMeta(traceId);
    String agentType = meta != null ? meta.agentId() : "UNKNOWN";
    List<TraceStep> steps = agentDebuggerService.getTrace(traceId);
    String plan =
        steps.stream()
            .map(
                step ->
                    "["
                        + step.getStepIndex()
                        + "] "
                        + step.getStepType()
                        + ": "
                        + step.getContent())
            .collect(Collectors.joining("\n"));
    return YdszResponse.success(new AgentTraceDetailDTO(traceId, agentType, plan));
  }

  /**
   * 重放指定链路。
   *
   * <p>从链路元数据中提取原始 conversationId 和 agentId，从第一个步骤中提取 userInput， 调用 {@link
   * AgentDebuggerService#replay} 重新执行 Agent，返回新的响应内容。
   *
   * <p>典型场景：复现历史对话 / 调试失败原因 / A/B 对比新旧版本的执行差异。
   *
   * <p>错误情况：
   *
   * <ul>
   *   <li>链路不存在 → 返回 {@code error(TRACE_NOT_FOUND)}
   *   <li>链路无步骤记录 → 返回 {@code error(TRACE_EMPTY)}
   * </ul>
   *
   * @param traceId 链路 ID
   * @return 统一响应结果，data 为重放后的 Agent 响应内容（字符串）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DEBUG_REPLAY)
  @Audit(
      module = "调试管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'replayTrace: ' + #traceId")
  @Idempotent(key = "ydsz:agent:DebugController:replayTrace:lock", ttlSeconds = 5)
  @RateLimit(resource = "agent.debug.replayTrace", threshold = 50)
  @PostMapping("/trace/{traceId}/replay")
  public YdszResponse<String> replayTrace(@PathVariable String traceId) {
    log.info("[Debug-API] 重放链路: traceId={}", traceId);
    TraceMeta meta = agentDebuggerService.getTraceMeta(traceId);
    if (meta == null) {
      return YdszResponse.error(AgentExceptionCode.TRACE_NOT_FOUND, "链路不存在或不支持重放: " + traceId);
    }
    List<TraceStep> steps = agentDebuggerService.getTrace(traceId);
    if (steps.isEmpty()) {
      return YdszResponse.error(AgentExceptionCode.TRACE_EMPTY, "链路无步骤记录，无法提取重放输入: " + traceId);
    }
    // 从链路第一个步骤提取原始 userInput，从元数据提取 conversationId / agentType
    String userInput = steps.get(0).getContent();
    String conversationId = meta.conversationId();
    String agentType = meta.agentId();
    log.info(
        "[Debug-API] 重放参数: convId={}, agentType={}, userInputLen={}",
        conversationId,
        agentType,
        userInput != null ? userInput.length() : 0);
    ChatResponse response = agentDebuggerService.replay(conversationId, userInput, agentType);
    return YdszResponse.success(response != null ? response.getContent() : "");
  }
}
