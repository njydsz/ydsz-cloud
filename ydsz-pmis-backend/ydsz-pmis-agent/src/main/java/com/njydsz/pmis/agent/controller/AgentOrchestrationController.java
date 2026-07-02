package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.agent.service.AgentOrchestrationService;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
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
@RequestMapping("/api/v1/agent/orchestration")
@RequiredArgsConstructor
public class AgentOrchestrationController {

    private final AgentOrchestrationService service;

    @Operation(summary = "协调多 Agent 编排执行")
    @PrePermission("agent:orchestration:run")
    @PostMapping("/coordinate")
    public Result<OrchestrationResult> coordinate(@RequestBody OrchestrationRequest req) {
        return Result.ok(service.orchestrate(req));
    }
}
