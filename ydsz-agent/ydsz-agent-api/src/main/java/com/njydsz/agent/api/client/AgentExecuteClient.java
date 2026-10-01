package com.njydsz.agent.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.agent.api.fallback.AgentExecuteClientFallback;
import com.njydsz.agent.domain.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.domain.dto.ChatResponseDTO;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;

/**
 * Agent 执行 Feign 客户端契约。
 *
 * <p>封装对 {@code POST /api/agent/execute} 的远程调用，统一通过 ydsz-common-feign 体系获得
 * 熔断/重试/追踪/13 头透传等能力，替代 workflow 模块通过 RestTemplate 直接 HTTP 调用。
 *
 * <p>接口签名与 {@code AgentController.execute} 保持一致；返回类型使用 {@link YdszResponse}
 * 包装以利用 {@code ResponseUnwrapDecoder} 自动解包。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@FeignClient(
    name = FeignClientConstants.SERVICE_AGENT,
    contextId = "agentExecuteClient",
    fallbackFactory = AgentExecuteClientFallback.class)
public interface AgentExecuteClient {

  /**
   * 同步执行 Agent（等待完整响应后返回）。
   *
   * @param request 执行请求体（agentCode / userInput / context 等）
   * @return {@link YdszResponse} 包装的 {@link ChatResponseDTO}，不会为 {@code null}
   */
  @PostMapping("/agent/execute")
  YdszResponse<ChatResponseDTO> execute(@RequestBody AgentExecutionRequestDTO request);
}
