package com.njydsz.agent.web.controller;

import java.util.Map;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.agent.AgentDag;
import com.njydsz.agent.domain.agent.DagCheckpoint;
import com.njydsz.agent.domain.dto.DagExecutionDTO;
import com.njydsz.agent.domain.gateway.DagCheckpointStore;
import com.njydsz.agent.server.agent.DagDslParser;
import com.njydsz.agent.server.agent.DagOrchestrationExecutor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * Agent DAG 编排 REST API Controller。
 *
 * <p>提供 YAML DSL 驱动的多 Agent 编排执行能力，允许业务方通过声明式 YAML 定义多个 Agent 之间的协作关系（串行/并行/条件分支），由编排引擎统一调度执行。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #execute} - 执行 DAG 编排：解析 DSL → 构建 DAG → 编排执行
 *   <li>{@link #validate} - 验证 DSL：仅解析不执行，返回校验结果（节点数 / DAG 名称 / 错误信息）
 * </ul>
 *
 * <h3>DSL 示例</h3>
 *
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
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/dag")
public class DagController {

  /**
   * DAG 执行幂等锁 TTL（秒）。
   *
   * <p>P0 修复：必须不小于 DAG 编排总超时（300s），否则锁先于执行释放，相同请求可重复提交导致 DAG 重复执行、LLM 成本翻倍。
   */
  private static final int EXECUTE_IDEMPOTENT_TTL_SECONDS = 300;

  /** DAG DSL 解析器（YAML → AgentDag） */
  private final DagDslParser dslParser;

  /** DAG 编排执行器（拓扑排序 + 节点派发） */
  private final DagOrchestrationExecutor dagExecutor;

  /** 检查点存储（可选依赖，Redis 不可用时降级） */
  private final DagCheckpointStore checkpointStore;

  public DagController(
      DagDslParser dslParser,
      DagOrchestrationExecutor dagExecutor,
      ObjectProvider<DagCheckpointStore> checkpointStoreProvider) {
    this.dslParser = dslParser;
    this.dagExecutor = dagExecutor;
    // 检查点存储为可选依赖：Redis 不可用时降级为无续跑能力，编排本身仍可执行
    this.checkpointStore = checkpointStoreProvider.getIfAvailable();
  }

  /**
   * 执行 DAG 编排。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>由 {@link DagDslParser#parse} 将 YAML DSL 解析为 {@link AgentDag} 对象
   *   <li>由 {@link DagOrchestrationExecutor#execute} 拓扑排序后逐节点派发执行
   *   <li>返回编排执行结果（含各节点状态、最终输出等）
   * </ol>
   *
   * <p>注意：执行过程可能耗时较长，调用方需评估超时设置；本接口默认同步等待所有节点完成。
   *
   * @param request DAG 执行请求（含 dsl YAML / userInput）
   * @return 统一响应结果，data 为 {@link DagOrchestrationExecutor.DagExecutionResult} （含执行状态 / 各节点结果 /
   *     最终输出）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'execute'")
  @Idempotent(key = "ydsz:agent:DagController:execute:lock", ttlSeconds = EXECUTE_IDEMPOTENT_TTL_SECONDS)
  @RateLimit(resource = "agent.dag.execute", threshold = 50)
  @PostMapping("/execute")
  public YdszResponse<DagOrchestrationExecutor.DagExecutionResult> execute(
      @Valid @RequestBody DagExecutionDTO request) {
    log.info("[DAG-API] 收到编排请求: userInput={}", request.getUserInput());

    // 1. 解析 DSL（YAML → AgentDag 对象）
    AgentDag dag = dslParser.parse(request.getDsl());
    // 2. 编排执行（拓扑排序 + 逐节点派发；含 resumeExecutionId 则从检查点续跑）
    DagOrchestrationExecutor.DagExecutionResult result =
        dagExecutor.execute(dag, request.getUserInput(), request.getResumeExecutionId());

    return YdszResponse.success(result);
  }

  /**
   * 查询执行检查点。
   *
   * <p>返回指定执行 ID 的快照状态（已完成节点、失败节点、快照时间），供前端判断是否可以续跑。 检查点不存在时返回 success + found=false。
   *
   * @param executionId 执行 ID
   * @return 检查点状态
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_EXECUTE)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'query checkpoint'")
  @GetMapping("/checkpoint/{executionId}")
  public YdszResponse<DagCheckpoint> getCheckpoint(@PathVariable String executionId) {
    if (checkpointStore == null) {
      return YdszResponse.success(null);
    }
    return YdszResponse.success(checkpointStore.load(executionId).orElse(null));
  }

  /**
   * 验证 DSL（不实际执行）。
   *
   * <p>仅解析 DSL 并返回校验结果（valid / dagName / nodeCount / 错误信息）， 供前端 DAG 编辑器在保存前做实时校验。注意：解析失败时仍返回
   * success + valid=false， 由前端根据 valid 字段判断是否展示错误。
   *
   * @param request DAG 请求体（仅 dsl 字段被使用）
   * @return 统一响应结果，data 为 {@code {valid, dagName, nodeCount, error?}} Map
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_DAG_VALIDATE)
  @Audit(
      module = "DAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'validate'")
  @Idempotent(key = "ydsz:agent:DagController:write:lock", ttlSeconds = 5)
  @PostMapping("/validate")
  public YdszResponse<Map<String, Object>> validate(@Valid @RequestBody DagExecutionDTO request) {
    try {
      AgentDag dag = dslParser.parse(request.getDsl());
      return YdszResponse.success(
          Map.of(
              "valid", true,
              "dagName", dag.getName(),
              "nodeCount", dag.getNodes().size()));
    } catch (Exception e) {
      log.error("[DAG-API] DSL 解析失败, dsl={}, err={}", request.getDsl(), e.getMessage(), e);
      // 解析失败时仍返回 success，由 valid 字段标识
      return YdszResponse.success(Map.of("valid", false, "error", e.getMessage()));
    }
  }
}
