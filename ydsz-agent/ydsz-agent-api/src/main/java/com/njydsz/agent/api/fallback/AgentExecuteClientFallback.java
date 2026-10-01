package com.njydsz.agent.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.agent.api.client.AgentExecuteClient;
import com.njydsz.agent.domain.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.domain.dto.ChatResponseDTO;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.locales.util.I18n;

/**
 * {@link AgentExecuteClient} 熔断降级工厂。
 *
 * <p>Agent 智能引擎不可用时触发，返回 {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，
 * 由工作流上层捕获并按「审批请求异常」分支处理。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class AgentExecuteClientFallback implements FallbackFactory<AgentExecuteClient> {

  @Override
  public AgentExecuteClient create(Throwable cause) {
    log.warn("[AgentExecuteClient] 降级触发: {}", cause == null ? "?" : cause.getMessage());
    String unavailableMsg = I18n.message("agent.service.unavailable");
    return new AgentExecuteClient() {
      @Override
      public YdszResponse<ChatResponseDTO> execute(AgentExecutionRequestDTO request) {
        String agentCode = request == null ? null : request.getAgentCode();
        log.warn(
            "[AgentExecuteClient] execute 降级: agentCode={}, reason={}",
            agentCode, unavailableMsg);
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, unavailableMsg);
      }
    };
  }
}
