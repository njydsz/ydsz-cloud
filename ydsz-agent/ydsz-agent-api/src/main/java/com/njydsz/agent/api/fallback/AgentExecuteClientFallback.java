package com.njydsz.agent.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.agent.api.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.api.feign.AgentExecuteClient;
import com.njydsz.common.core.response.BaseResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link AgentExecuteClient} 的 FallbackFactory。
 *
 * <p>AI Agent 服务不可用时降级返回 null，仅记录 WARN 日志，
 * 保证调用方主流程不受影响（Agent 执行是辅助决策，不应阻断业务）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentExecuteClientFallback implements FallbackFactory<AgentExecuteClient> {

    @Override
    public AgentExecuteClient create(Throwable cause) {
        log.warn("[AgentExecuteClient] 降级触发: {}", cause.getMessage());
        return new AgentExecuteClient() {
            @Override
            public BaseResponse<ChatResponseDTO> execute(String agentType,
                                                          AgentExecutionRequestDTO request) {
                log.warn("[AgentExecuteClient] execute 降级: agentType={}, reason=AI Agent 服务不可用",
                        agentType);
                return BaseResponse.success(null);
            }
        };
    }
}
