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
 * Agent DAG 编排 REST API Controller。
 *
 * <p>提供 YAML DSL 驱动的多 Agent 编排执行能力，允许业务方通过声明式 YAML
 * 定义多个 Agent 之间的协作关系（串行/并行/条件分支），由编排引擎统一调度执行。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #execute} - 执行 DAG 编排：解析 DSL → 构建 DAG → 编排执行</li>
 *   <li>{@link #validate} - 验证 DSL：仅解析不执行，返回校验结果（节点数 / DAG 名称 / 错误信息）</li>
 * </ul>
 *
 * <h3>DSL 示例</h3>
 * <pre>{@code
 * name: order-analysis
 * nodes:
 *   - id: fetch-order
 *     agent: order-fetcher
 *   - id: analyze-order
 *     agent: order-analyzer
 *     dependsOn: [fetch-order]
 * }</pre>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/agent/dag")
public class DagController {

    private static final Logger log = LoggerFactory.getLogger(DagController.class);

    /** DAG DSL 解析器（YAML → AgentDag） */
    private final DagDslParser dslParser;
    /** DAG 编排执行器（拓扑排序 + 节点派发） */
    private final DagOrchestrationExecutor dagExecutor;

    public DagController(DagDslParser dslParser, DagOrchestrationExecutor dagExecutor) {
        this.dslParser = dslParser;
        this.dagExecutor = dagExecutor;
    }

    /**
     * 执行 DAG 编排。
     *
     * <p>处理流程：
     * <ol>
     *   <li>由 {@link DagDslParser#parse} 将 YAML DSL 解析为 {@link AgentDag} 对象</li>
     *   <li>由 {@link DagOrchestrationExecutor#execute} 拓扑排序后逐节点派发执行</li>
     *   <li>返回编排执行结果（含各节点状态、最终输出等）</li>
     * </ol>
     *
     * <p>注意：执行过程可能耗时较长，调用方需评估超时设置；本接口默认同步等待所有节点完成。
     *
     * @param request DAG 执行请求（含 dsl YAML / userInput）
     * @return 统一响应结果，data 为 {@link DagOrchestrationExecutor.DagExecutionResult}
     *         （含执行状态 / 各节点结果 / 最终输出）
     */
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'execute'")
    @Idempotent(key = "ydsz:agent:DagController:execute:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.dag.execute", threshold = 50)
    @PostMapping("/execute")
    public BaseResponse<DagOrchestrationExecutor.DagExecutionResult> execute(
            @Valid @RequestBody DagExecutionDTO request) {
        log.info("[DAG-API] 收到编排请求: userInput={}", request.getUserInput());

        // 1. 解析 DSL（YAML → AgentDag 对象）
        AgentDag dag = dslParser.parse(request.getDsl());
        // 2. 编排执行（拓扑排序 + 逐节点派发）
        DagOrchestrationExecutor.DagExecutionResult result =
                dagExecutor.execute(dag, request.getUserInput());

        return BaseResponse.success(result);
    }

    /**
     * 验证 DSL（不实际执行）。
     *
     * <p>仅解析 DSL 并返回校验结果（valid / dagName / nodeCount / 错误信息），
     * 供前端 DAG 编辑器在保存前做实时校验。注意：解析失败时仍返回 success + valid=false，
     * 由前端根据 valid 字段判断是否展示错误。
     *
     * @param request DAG 请求体（仅 dsl 字段被使用）
     * @return 统一响应结果，data 为 {@code {valid, dagName, nodeCount, error?}} Map
     */
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'validate'")
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
            // 解析失败时仍返回 success，由 valid 字段标识
            return BaseResponse.success(Map.of(
                    "valid", false,
                    "error", e.getMessage()));
        }
    }
}
