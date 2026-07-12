package com.njydsz.pmis.agent.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.agent.api.fallback.AgentClientFallbackFactory;

import com.njydsz.pmis.common.core.response.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * P2-1: Agent Feign 客户端（工作流模块调用 AI Agent 服务）
 *
 * <p>工作流引擎通过本接口调用 agent 模块的 executeInMemory 接口，
 * 实现"推荐审批人 / 起草意见"等智能审批能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.AGENT,
        path = "/agent",
        fallbackFactory = AgentClientFallbackFactory.class
)
public interface AgentClient {

    /**
     * P2-1: 同步执行 Agent（不落库，仅返回结果）
     *
     * <p>请求体字段：
     * <ul>
     *   <li>agentType: String 必填，如 APPROVER_RECOMMEND / COMMENT_DRAFT</li>
     *   <li>bizType: String 必填，FLOW_TASK / FLOW_INSTANCE</li>
     *   <li>bizId: Long 必填</li>
     *   <li>bizRef: String 可选</li>
     *   <li>params: Map 业务参数</li>
     * </ul>
     *
     * @param body 请求体
     * @return Agent 执行结果，data.payload 包含结构化输出
     */
    @PostMapping("/internal/execute")
    BaseResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body);
}
