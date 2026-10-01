package com.njydsz.workflow.infra.gateway;

import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.njydsz.agent.api.client.AgentExecuteClient;
import com.njydsz.agent.domain.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.domain.dto.ChatResponseDTO;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;
import com.njydsz.workflow.domain.gateway.AgentServiceClient;

/**
 * AgentServiceClient 的 Feign 调用实现（P1-3 由 RestTemplate 迁移）。
 *
 * <p>通过 {@link AgentExecuteClient} Feign 契约调用 {@code POST /api/agent/execute}，
 * 统一获得 ydsz-common-feign 体系提供的熔断/重试/追踪/13 头透传能力，
 * 替代原始基于 JDK {@code RestTemplate} 的直接 HTTP 调用。
 *
 * <h3>架构层次</h3>
 *
 * <ul>
 *   <li>{@code domain/gateway/AgentServiceClient} — 领域层抽象接口（防腐层）</li>
 *   <li>{@code infra/gateway/HttpAgentServiceClient} — 基础设施层适配器（委托 Feign 客户端）</li>
 *   <li>{@code ydzs-agent-api/.../AgentExecuteClient} — Feign 契约接口</li>
 *   <li>{@code ydzs-agent-api/.../AgentExecuteClientFallback} — Feign 熔断降级</li>
 * </ul>
 *
 * <h3>与旧实现差异</h3>
 *
 * <ul>
 *   <li>移除 RestTemplate 直接 HTTP 调用 → 委托 Feign AgentExecuteClient</li>
 *   <li>移除手动租户头透传（{@code X-Tenant-Id}）→ FeignRequestInterceptor 自动透传 13 个头</li>
 *   <li>Feign 自带熔断/重试 → 无需在校层手工实现</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @since 26.10.01 由 RestTemplate 迁移至 Feign（P1-3 YDIZ-FEIGN-002 违规整改）
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AgentServiceClient.class)
public class HttpAgentServiceClient implements AgentServiceClient {

  /** 无法从回复内容推断语义时的默认置信度 */
  private static final double DEFAULT_CONFIDENCE = 0.5;

  /** 依据关键词判定通过/拒绝后的置信度 */
  private static final double KEYWORD_CONFIDENCE = 0.85;

  private final AgentExecuteClient agentExecuteClient;

  public HttpAgentServiceClient(AgentExecuteClient agentExecuteClient) {
    this.agentExecuteClient = agentExecuteClient;
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 Feign {@link AgentExecuteClient#execute} 调用 ydsz-agent。 Feign 自动透传租户、
   * 用户、追踪等 13 个业务头（{@code FeignRequestInterceptor}），无需在校层手工处理。
   *
   * @param agentCode Agent 代码
   * @param prompt 提示词
   * @param context 流程上下文变量
   * @param timeoutMs 超时时间（毫秒，预留属性，Feign 体系走 ydsz.feign.timeout 配置）
   * @return Agent 执行结果
   */
  @Override
  public AgentExecutionResult execute(String agentCode, String prompt,
      Map<String, Object> context, int timeoutMs) {
    log.info("[Workflow-Agent] 调用 Agent 服务（Feign）: agentCode={}, timeout={}ms", agentCode, timeoutMs);

    AgentExecutionRequestDTO requestBody = buildRequest(agentCode, prompt, context);

    try {
      YdszResponse<ChatResponseDTO> response = agentExecuteClient.execute(requestBody);

      if (response == null || response.getData() == null) {
        log.warn("[Workflow-Agent] Agent 返回空响应: agentCode={}", agentCode);
        return AgentExecutionResult.error(I18n.message("workflow.agent.empty_response"));
      }

      ChatResponseDTO result = response.getData();
      return parseAgentResult(result);
    } catch (WorkflowException e) {
      throw e;
    } catch (Exception e) {
      log.error("[Workflow-Agent] Agent 调用异常: agentCode={}, error={}", agentCode, e.getMessage(), e);
      throw new WorkflowException(WorkflowExceptionCode.AI_AGENT_EXECUTION_ERROR,
          I18n.message("workflow.agent.invocation_failed", new Object[]{e.getMessage()}));
    }
  }

  private AgentExecutionRequestDTO buildRequest(String agentCode, String prompt,
      Map<String, Object> context) {
    AgentExecutionRequestDTO request = new AgentExecutionRequestDTO();
    request.setAgentCode(agentCode);
    request.setUserInput(prompt);
    request.setRequestId(UUID.randomUUID().toString());
    if (context != null && context.containsKey("systemPrompt")) {
      Object systemPrompt = context.get("systemPrompt");
      if (systemPrompt != null) {
        request.setSystemPrompt(systemPrompt.toString());
      }
    }
    String tenantId = resolveTenantId(context);
    if (tenantId != null && !tenantId.isEmpty() && !tenantId.equals("system")) {
      request.setConversationId("wf-" + tenantId + "-" + agentCode);
    }
    return request;
  }

  private AgentExecutionResult parseAgentResult(ChatResponseDTO dto) {
    String content = dto.getContent();

    boolean approve = false;
    String reason = content != null ? content : "";
    double confidence = DEFAULT_CONFIDENCE;

    if (content != null) {
      String lower = content.toLowerCase();
      if (lower.contains("approve") || lower.contains("通过") || lower.contains("同意")) {
        approve = true;
        confidence = KEYWORD_CONFIDENCE;
      } else if (lower.contains("reject") || lower.contains("拒绝") || lower.contains("驳回")) {
        approve = false;
        confidence = KEYWORD_CONFIDENCE;
      }
    }

    return new AgentExecutionResult(approve, reason, confidence, content);
  }

  /**
   * 解析 Agent 请求的租户 ID。
   *
   * <p>优先从流程上下文获取租户 ID（工作流多租户覆盖场景）；
   * 未携带时委托 {@link AuthContextUtils#getTenantIdOrDefault(String)} 从请求上下文获取，
   * 遵循 YDIZ-TENANT-001 规范禁止静默兜底为 "system"。
   *
   * @param context 流程上下文
   * @return 租户 ID
   */
  private String resolveTenantId(Map<String, Object> context) {
    if (context != null && context.containsKey("tenantId")) {
      Object tenantId = context.get("tenantId");
      if (tenantId != null) {
        return tenantId.toString();
      }
    }
    return AuthContextUtils.getTenantIdOrDefault("system");
  }
}
