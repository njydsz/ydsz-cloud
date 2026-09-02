package com.njydsz.workflow.infra.gateway;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;
import com.njydsz.workflow.domain.gateway.AgentServiceClient;

/**
 * AgentServiceClient 的 HTTP 调用实现。
 *
 * <p>通过 {@link RestTemplate} 调用 ydsz-agent 的 REST API，封装同步执行 Agent 的流程。
 * 作为默认实现注册，可通过 {@code @ConditionalOnMissingBean} 机制由 Feign 实现替换。
 *
 * <h3>架构说明</h3>
 *
 * <ul>
 *   <li>domain/gateway/AgentServiceClient — 领域层抽象接口（防腐层）</li>
 *   <li>infra/gateway/HttpAgentServiceClient — 基础设施层适配器（对标 ydsz-agent REST API）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@ConditionalOnMissingBean(AgentServiceClient.class)
public class HttpAgentServiceClient implements AgentServiceClient {

  /** 无法从回复内容推断语义时的默认置信度 */
  private static final double DEFAULT_CONFIDENCE = 0.5;

  /** 依据关键词判定通过/拒绝后的置信度 */
  private static final double KEYWORD_CONFIDENCE = 0.85;

  /** HTTP 客户端 */
  private final RestTemplate restTemplate;

  /** Agent 服务基础 URL（从配置注入） */
  private final String agentBaseUrl;

  /**
   * 构造器。
   *
   * @param restTemplate HTTP 客户端
   * @param env Spring 环境配置
   */
  public HttpAgentServiceClient(RestTemplate restTemplate, Environment env) {
    this.restTemplate = restTemplate;
    this.agentBaseUrl = env.getProperty("ydsz.agent.base-url", "http://ydsz-agent:8080");
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 HTTP POST 调用 ydsz-agent 的 {@code /api/v1/agent/execute} 接口执行 Agent，
   * 解析响应后返回审批决策结果。
   *
   * @param agentCode Agent 代码
   * @param prompt 提示词
   * @param context 流程上下文变量
   * @param timeoutMs 超时时间（毫秒）
   * @return Agent 执行结果
   */
  @Override
  public AgentExecutionResult execute(String agentCode, String prompt,
      Map<String, Object> context, int timeoutMs) {
    log.info("[Workflow-Agent] 调用 Agent 服务: agentCode={}, timeout={}ms", agentCode, timeoutMs);

    AgentExecuteRequest requestBody = new AgentExecuteRequest(agentCode, prompt, context);

    try {
      RequestEntity<AgentExecuteRequest> request = RequestEntity
          .post(agentBaseUrl + "/api/v1/agent/execute")
          .header("X-Tenant-Id", resolveTenantId(context))
          .body(requestBody);

      var response = restTemplate.exchange(request, AgentExecuteResponse.class);

      if (response.getBody() == null || response.getBody().data() == null) {
        log.warn("[Workflow-Agent] Agent 返回空响应: agentCode={}", agentCode);
        return AgentExecutionResult.error("Agent 返回空响应");
      }

      AgentExecuteResponseDTO result = response.getBody().data();
      return parseAgentResult(result);
    } catch (WorkflowException e) {
      throw e;
    } catch (Exception e) {
      log.error("[Workflow-Agent] Agent 调用异常: agentCode={}, error={}", agentCode, e.getMessage(), e);
      throw new WorkflowException(WorkflowExceptionCode.AI_AGENT_EXECUTION_ERROR,
          "Agent 服务调用失败: " + e.getMessage());
    }
  }

  /**
   * 解析 Agent 返回结果。
   *
   * @param dto Agent 响应 DTO
   * @return 审批决策结果
   */
  private AgentExecutionResult parseAgentResult(AgentExecuteResponseDTO dto) {
    String content = dto.content();

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
   * 从上下文中提取租户 ID。
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
    return "system";
  }

  /**
   * Agent 执行请求体。
   *
   * @param agentCode Agent 代码
   * @param prompt 提示词
   * @param context 上下文
   */
  private record AgentExecuteRequest(String agentCode, String prompt, Map<String, Object> context) {
  }

  /**
   * Agent 执行响应体。
   *
   * @param code 响应码
   * @param message 响应消息
   * @param data 响应数据
   */
  private record AgentExecuteResponse(int code, String message, AgentExecuteResponseDTO data) {
  }

  /**
   * Agent 执行响应 DTO。
   *
   * @param agentCode Agent 代码
   * @param content 执行输出
   * @param model 使用的模型
   */
  private record AgentExecuteResponseDTO(String agentCode, String content, String model) {
  }
}
