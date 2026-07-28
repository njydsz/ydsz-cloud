package com.njydsz.agent.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.agent.api.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.api.fallback.AgentExecuteClientFallback;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;

/**
 * Agent 执行 Feign 客户端（供跨服务调用）。
 *
 * <p>提供 Agent 远程执行能力，支持同步执行模式。
 * 典型场景：工作流审批节点调用 Agent 进行智能分析、
 * 定时任务调用 Agent 生成报告等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.AGENT, contextId = "agentExecuteClient",
        fallbackFactory = AgentExecuteClientFallback.class)

/**
 * AgentExecuteClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.agent.api.feign}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentExecuteClient {

    /**
     * 执行 Agent（同步模式）。
     *
     * @param agentType Agent 类型编码
     * @param request   执行请求（包含输入消息、上下文、工具参数等）
     * @return 执行响应（包含 Agent 输出内容、token 用量等）
     */
    @PostMapping(FeignClientConstants.AGENT_PATH_EXECUTE)
    BaseResponse<ChatResponseDTO> execute(@RequestParam("agentType") String agentType,
                                          @RequestBody AgentExecutionRequestDTO request);
}
