package com.njydsz.pmis.agent.web.controller.agent;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.server.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationResult;
import com.njydsz.pmis.agent.server.service.agent.AgentOrchestrationService;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多智能体编排 Controller（AgentScope 模式）
 *
 * <p>借鉴 AgentScope 多智能体协同设计思想，对外提供统一编排入口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "AI 多智能体编排")
@RestController
@RequestMapping("/agent/orchestration")
@RequiredArgsConstructor
@Validated
public class AgentOrchestrationController {

    /** 多智能体编排服务 */
    private final AgentOrchestrationService service;

    /**
     * 协调多 Agent 编排执行。
     *
     * @param req 编排请求（包含模式、Agent 列表、输入等）
     * @return 编排结果
     */
    @Operation(summary = "协调多 Agent 编排执行")
    @AuthApiPermission(apiCodes = "agent:orchestration:run")
    @Idempotent(key = "agentOrchestration:coordinate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/coordinate")
    public BaseResponse<OrchestrationResult> coordinate(@RequestBody OrchestrationRequest req) {
        return BaseResponse.ok(service.orchestrate(req));
    }
}
