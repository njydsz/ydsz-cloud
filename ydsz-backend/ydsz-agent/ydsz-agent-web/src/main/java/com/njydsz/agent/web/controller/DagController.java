package com.njydsz.agent.web.controller;

import java.util.Map;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.api.dto.DagExecutionDTO;
import com.njydsz.agent.domain.agent.AgentDag;
import com.njydsz.agent.server.agent.DagDslParser;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * Agent DAG 编排 REST API
 *
 * <p>提供 YAML DSL 驱动的多 Agent 编排执行接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/agent/dag")
public class DagController {

    private static final Logger log = LoggerFactory.getLogger(DagController.class);

    /** DAG DSL 解析器 */
    private final DagDslParser dslParser;
    /** DAG 编排执行器 */
    private final DagOrchestrationExecutor dagExecutor;

    public DagController(DagDslParser dslParser, DagOrchestrationExecutor dagExecutor) {
        this.dslParser = dslParser;
        this.dagExecutor = dagExecutor;
    }

    /**
     * 执行 DAG 编排
     */
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'execute'")
    @Idempotent(key = "ydsz:agent:DagController:execute:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.dag.execute", threshold = 50)
    @PostMapping("/execute")
    public BaseResponse<DagOrchestrationExecutor.DagExecutionResult> execute(
            @Valid @RequestBody DagExecutionDTO request) {
        log.info("[DAG-API] 收到编排请求: userInput={}", request.getUserInput());

        AgentDag dag = dslParser.parse(request.getDsl());
        DagOrchestrationExecutor.DagExecutionResult result =
                dagExecutor.execute(dag, request.getUserInput());

        return BaseResponse.success(result);
    }

    /**
     * 验证 DSL（不执行）
     */
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @Idempotent(key = "ydsz:agent:DagController:write:lock", ttlSeconds = 5)
    @PostMapping("/validate")
    public BaseResponse<Map<String, Object>> validate(@RequestBody DagExecutionDTO request) {
        try {
            AgentDag dag = dslParser.parse(request.getDsl());
            return BaseResponse.success(Map.of(
                    "valid", true,
                    "dagName", dag.getName(),
                    "nodeCount", dag.getNodes().size()));
        } catch (Exception e) {
            return BaseResponse.success(Map.of(
                    "valid", false,
                    "error", e.getMessage()));
        }
    }
}
