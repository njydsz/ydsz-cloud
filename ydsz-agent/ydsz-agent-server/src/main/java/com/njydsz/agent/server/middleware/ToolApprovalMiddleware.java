package com.njydsz.agent.server.middleware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * <p><b>非阻塞语义</b>：本中间件不挂起等待人工决策。审批通过后由调用方携带
 * {@code approvalId} 重新发起该工具调用（补偿路径见 {@code HumanApprovalController}），
 * 这是有意为之——在 SSE 请求线程上阻塞等待人工操作会长期占用容器线程，
 * 与 {@code SseExecutor} 的超时清理策略冲突。
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

  /** 敏感工具名单（空表示不拦截任何工具） */
  private final Set<String> sensitiveTools;

  /** 审批服务（未装配时降级为不拦截） */
  private final ObjectProvider<HumanApprovalService> approvalServiceProvider;

  /**
   * 构造工具审批门中间件。
   *
   * @param sensitiveToolsConfig 敏感工具名单（逗号分隔，未配置时为空串表示不拦截）
   * @param approvalServiceProvider 审批服务（可选）
   */
  public ToolApprovalMiddleware(
      @Value("${ydsz.agent.approval.sensitive-tools:}") String sensitiveToolsConfig,
      ObjectProvider<HumanApprovalService> approvalServiceProvider) {
    this.sensitiveTools = parseSensitiveTools(sensitiveToolsConfig);
    this.approvalServiceProvider = approvalServiceProvider;
    if (!sensitiveTools.isEmpty()) {
      log.info("[ToolApproval] 工具审批门已启用: sensitiveTools={}", sensitiveTools);
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
    List<ToolCall> pending = filterSensitive(toolCalls);
    if (pending.isEmpty()) {
      context.setToolResults(proceed.execute());
      return;
    }
    // 放行非敏感调用，命中名单的调用以「待审批」观察结果回填
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
   * @param toolCalls 本批次工具调用
   * @return 需要人工审批的调用列表
   */
  private List<ToolCall> filterSensitive(List<ToolCall> toolCalls) {
    List<ToolCall> pending = new ArrayList<>(PENDING_CAPACITY);
    for (ToolCall toolCall : toolCalls) {
      if (toolCall.getName() != null && sensitiveTools.contains(toolCall.getName())) {
        pending.add(toolCall);
      }
    }
    return pending;
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
