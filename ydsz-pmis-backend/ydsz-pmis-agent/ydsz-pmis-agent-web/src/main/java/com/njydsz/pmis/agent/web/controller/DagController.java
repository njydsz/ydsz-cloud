package com.njydsz.pmis.agent.web.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.agent.api.dto.DagExecutionDTO;
import com.njydsz.pmis.agent.domain.agent.AgentDag;
import com.njydsz.pmis.agent.server.agent.DagDslParser;
import com.njydsz.pmis.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.pmis.common.core.response.BaseResponse;

/**
 * Agent DAG 编排 REST API
 *
 * <p>提供 YAML DSL 驱动的多 Agent 编排执行接口。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@RestController
@RequestMapping("/agent/dag")
public class DagController {

    private static final Logger log = LoggerFactory.getLogger(DagController.class);

    private final DagDslParser dslParser;
    private final DagOrchestrationExecutor dagExecutor;

    public DagController(DagDslParser dslParser, DagOrchestrationExecutor dagExecutor) {
        this.dslParser = dslParser;
        this.dagExecutor = dagExecutor;
    }

    /**
     * 执行 DAG 编排
     */
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
