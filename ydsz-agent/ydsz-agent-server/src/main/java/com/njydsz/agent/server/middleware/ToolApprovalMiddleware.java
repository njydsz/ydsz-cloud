package com.njydsz.agent.server.middleware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.execution.SessionPausedException;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.server.agent.HumanApprovalService;

/**
 * 工具审批门中间件 — Human-in-the-Loop 审批在工具执行阶段的落点。
 *
 * <p>挂在 {@code onActing} 阶段（优先级 {@link AgentMiddleware#TOOL_AUDIT_PRIORITY}，早于业务中间件）：
 * 本批次工具调用中命中「敏感工具名单」的调用会被拦截——先创建审批请求并把
 * {@code approval_required} 事件推入当前 SSE 流（P0-3 事件化，前端直接渲染审批卡片），
 * 再以结构化「待审批」观察结果回填，使推理循环可继续（不阻塞执行线程）。
 *
 * <p><b>两种工作模式</b>：
 * <ul>
 *   <li><b>非阻塞模式（默认）</b>：命中敏感名单的工具调用以结构化「待审批」观察结果回填，
 *       推理循环继续，审批通过后由调用方携带 {@code approvalId} 重新发起该工具调用。
 *       适用于不想挂起执行线程的场景（SSE 超时清理策略兼容）。</li>
 *   <li><b>暂停模式</b>：{@code ydsz.agent.approval.pause-on-sensitive=true} 时，
 *       命中敏感工具的本批次调用会抛出 {@link SessionPausedException}，
 *       执行器捕获后保存 {@link com.njydsz.agent.domain.execution.ExecutionCheckpoint}
 *       并返回暂停状态；审批通过后调用 {@code AgentFacade.resume(...)} 恢复执行。</li>
 * </ul>
 *
 * <p><b>默认关闭</b>：{@code ydsz.agent.approval.sensitive-tools} 未配置时名单为空，
 * 中间件对任何工具都不拦截（零行为变更），仅作为可插拔的审批能力接入点。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Component
public class ToolApprovalMiddleware implements AgentMiddleware {

  /** 中间件名称 */
  private static final String NAME = "tool-approval";

  /** 审批请求上下文摘要中的工具名键 */
  private static final String CONTEXT_KEY_TOOL = "tool";

  /** 审批请求上下文摘要中的参数键 */
  private static final String CONTEXT_KEY_ARGUMENTS = "arguments";

  /** 待审批工具数量初始容量 */
  private static final int PENDING_CAPACITY = 4;

  /** 已审批工具调用 ID 上下文属性键（恢复执行时跳过重复审批） */
  private static final String BYPASS_CALL_IDS_KEY = "tool-approval.approved-call-ids";

  /** 敏感工具名单（空表示不拦截任何工具） */
  private final Set<String> sensitiveTools;

  /** 审批服务（未装配时降级为不拦截） */
  private final ObjectProvider<HumanApprovalService> approvalServiceProvider;

  /** 是否启用暂停模式（true 时抛 SessionPausedException，false 时返回待审批观察结果） */
  private final boolean pauseOnSensitive;

  /**
   * 构造工具审批门中间件。
   *
   * @param sensitiveToolsConfig 敏感工具名单（逗号分隔，未配置时为空串表示不拦截）
   * @param pauseOnSensitive 命中敏感工具时是否暂停执行（默认 false）
   * @param approvalServiceProvider 审批服务（可选）
   */
  public ToolApprovalMiddleware(
      @Value("${ydsz.agent.approval.sensitive-tools:}") String sensitiveToolsConfig,
      @Value("${ydsz.agent.approval.pause-on-sensitive:false}") boolean pauseOnSensitive,
      ObjectProvider<HumanApprovalService> approvalServiceProvider) {
    this.sensitiveTools = parseSensitiveTools(sensitiveToolsConfig);
    this.pauseOnSensitive = pauseOnSensitive;
    this.approvalServiceProvider = approvalServiceProvider;
    if (!sensitiveTools.isEmpty()) {
      log.info(
          "[ToolApproval] 工具审批门已启用: sensitiveTools={}, pauseOnSensitive={}",
          sensitiveTools,
          pauseOnSensitive);
    }
  }

  @Override
  public void onActing(MiddlewareContext context, ActingProceed proceed) {
    List<ToolCall> toolCalls = context.getToolCalls();
    if (sensitiveTools.isEmpty()
        || toolCalls == null
        || toolCalls.isEmpty()
        || !context.canEmitEvent()) {
      context.setToolResults(proceed.execute());
      return;
    }
    List<ToolCall> pending = filterSensitive(toolCalls, context);
    if (pending.isEmpty()) {
      context.setToolResults(proceed.execute());
      return;
    }

    // 暂停模式：登记审批后抛异常，由执行器保存检查点并结束当前执行
    if (pauseOnSensitive) {
      // 仅对第一个敏感工具创建审批请求（批量敏感场景拆到恢复后再处理）
      ToolCall firstPending = pending.get(0);
      String approvalId = requestApproval(context, firstPending);
      throw new SessionPausedException(approvalId, pending);
    }

    // 非阻塞模式：放行非敏感调用，命中名单的调用以「待审批」观察结果回填
    Map<String, String> results = new HashMap<>(proceed.execute());
    for (ToolCall toolCall : pending) {
      String approvalId = requestApproval(context, toolCall);
      results.put(toolCall.getId(), buildPendingObservation(toolCall, approvalId));
    }
    context.setToolResults(results);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getPriority() {
    return TOOL_AUDIT_PRIORITY;
  }

  /**
   * 筛选命中敏感名单的工具调用。
   *
   * <p>恢复执行时，上下文可能携带已审批调用 ID（{@link #BYPASS_CALL_IDS_KEY}），
   * 这些调用已被人为审批通过，不再重复拦截。
   *
   * @param toolCalls 本批次工具调用
   * @param context 中间件上下文
   * @return 需要人工审批的调用列表
   */
  private List<ToolCall> filterSensitive(List<ToolCall> toolCalls, MiddlewareContext context) {
    Set<String> bypassIds = resolveBypassIds(context);
    List<ToolCall> pending = new ArrayList<>(PENDING_CAPACITY);
    for (ToolCall toolCall : toolCalls) {
      if (bypassIds.contains(toolCall.getId())) {
        continue;
      }
      if (toolCall.getName() != null && sensitiveTools.contains(toolCall.getName())) {
        pending.add(toolCall);
      }
    }
    return pending;
  }

  /**
   * 解析已审批调用 ID 集合（上下文缺失或类型不匹配时返回空集）。
   *
   * @param context 中间件上下文
   * @return 已审批调用 ID 集合
   */
  @SuppressWarnings("unchecked")
  private Set<String> resolveBypassIds(MiddlewareContext context) {
    Object value = context.getAttribute(BYPASS_CALL_IDS_KEY);
    if (value instanceof Set) {
      return (Set<String>) value;
    }
    if (value instanceof List) {
      return new HashSet<>((List<String>) value);
    }
    return Set.of();
  }

  /**
   * 创建审批请求并把审批卡片推入当前流。
   *
   * @param context 中间件上下文
   * @param toolCall 待审批工具调用
   * @return 审批请求 ID；审批服务不可用时返回空串
   */
  private String requestApproval(MiddlewareContext context, ToolCall toolCall) {
    HumanApprovalService approvalService = approvalServiceProvider.getIfAvailable();
    if (approvalService == null) {
      log.warn("[ToolApproval] 审批服务未装配，工具 {} 按待审批处理但无法登记", toolCall.getName());
      return "";
    }
    Map<String, Object> approvalContext = new HashMap<>(PENDING_CAPACITY);
    approvalContext.put(CONTEXT_KEY_TOOL, toolCall.getName());
    approvalContext.put(CONTEXT_KEY_ARGUMENTS, toolCall.getArguments());
    // requestApproval 内部推送 approval_required 事件（携带 replyId = approvalId）
    return approvalService.requestApproval(
        context.getConversationId(),
        context.getTraceId(),
        "工具调用需人工审批: " + toolCall.getName(),
        approvalContext,
        context.getEventConsumer());
  }

  /**
   * 构建待审批观察结果（回填给模型，避免其无限重试同一调用）。
   *
   * @param toolCall 工具调用
   * @param approvalId 审批请求 ID
   * @return 观察文本
   */
  private String buildPendingObservation(ToolCall toolCall, String approvalId) {
    return "{\"status\":\"pending_approval\",\"tool\":\""
        + toolCall.getName()
        + "\",\"approvalId\":\""
        + approvalId
        + "\",\"message\":\"该工具调用需人工审批，已提交审批请求，请在审批通过后重新发起\"}";
  }

  /**
   * 解析敏感工具名单配置。
   *
   * @param config 逗号分隔的工具名（可空）
   * @return 去重后的工具名集合（空表示不拦截）
   */
  private static Set<String> parseSensitiveTools(String config) {
    if (config == null || config.isBlank()) {
      return Set.of();
    }
    Set<String> tools = new LinkedHashSet<>();
    for (String part : config.split(",")) {
      String tool = part.trim();
      if (!tool.isEmpty()) {
        tools.add(tool);
      }
    }
    return tools;
  }
}
