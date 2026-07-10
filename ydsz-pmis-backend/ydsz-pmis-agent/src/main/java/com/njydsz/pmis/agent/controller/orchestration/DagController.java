package com.njydsz.pmis.agent.controller.orchestration;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.entity.orchestration.DagDefinitionDO;
import com.njydsz.pmis.agent.entity.orchestration.DagInstanceDO;
import com.njydsz.pmis.agent.entity.orchestration.DagNodeInstanceDO;
import com.njydsz.pmis.agent.orchestration.dag.DagDefinition;
import com.njydsz.pmis.agent.orchestration.dag.DagExecutionResult;
import com.njydsz.pmis.agent.service.orchestration.DagService;
import com.njydsz.pmis.agent.service.agent.ValidationResult;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * DAG 编排引擎 Controller（P3-2 落地）。
 *
 * <p>对标 LangGraph Serve / Dify Workflow API / Coze Bot 工作流 API。
 * 提供 DAG 定义 CRUD、执行、历史查询接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Slf4j
@Tag(name = "DAG 编排引擎")
@RestController
@RequestMapping("/agent/dag")
@Validated
public class DagController {

    /** DAG 编排引擎服务 */
    private final DagService dagService;

    public DagController(DagService dagService) {
        this.dagService = dagService;
    }

    /**
     * 创建 DAG 定义。
     *
     * @param dag DAG 定义结构
     * @return 落库后的 DAG 定义
     */
    @Operation(summary = "创建 DAG 定义")
    @Idempotent(key = "dag:create-definition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<DagDefinitionDO> createDefinition(@Valid @RequestBody DagDefinition dag) {
        return Result.ok(dagService.createDefinition(dag));
    }

    /**
     * 查询 DAG 定义详情。
     *
     * @param id DAG 定义 ID
     * @return DAG 定义详情
     */
    @Operation(summary = "DAG 定义详情")
    @GetMapping("/{id}")
    public Result<DagDefinitionDO> getDefinition(@PathVariable String id) {
        return Result.ok(dagService.getDefinition(id));
    }

    /**
     * 分页查询 DAG 定义。
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param tenantId 租户 ID（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询 DAG 定义")
    @GetMapping("/page")
    public Result<PageResult<DagDefinitionDO>> pageDefinitions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String tenantId) {
        return Result.ok(dagService.pageDefinitions(page, size, tenantId));
    }

    /**
     * 执行 DAG。
     *
     * @param definitionId DAG 定义 ID
     * @param req          执行请求（含全局输入参数，可空）
     * @return DAG 执行结果
     */
    @Operation(summary = "执行 DAG")
    @Idempotent(key = "dag:execute", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/execute")
    public Result<DagExecutionResult> execute(
            @PathVariable("id") @NotBlank String definitionId,
            @RequestBody(required = false) ExecuteRequest req) {
        Map<String, Object> inputs = req != null ? req.getInputs() : null;
        return Result.ok(dagService.execute(definitionId, inputs));
    }

    /**
     * 查询 DAG 执行历史。
     *
     * @param definitionId DAG 定义 ID
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 分页结果
     */
    @Operation(summary = "DAG 执行历史")
    @GetMapping("/{id}/instances")
    public Result<PageResult<DagInstanceDO>> pageInstances(
            @PathVariable("id") @NotBlank String definitionId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.ok(dagService.pageInstances(definitionId, page, size));
    }

    /**
     * 查询 DAG 执行实例详情。
     *
     * @param instanceId 实例 ID
     * @return 实例详情
     */
    @Operation(summary = "DAG 实例详情")
    @GetMapping("/instance/{instanceId}")
    public Result<DagInstanceDO> getInstance(@PathVariable String instanceId) {
        return Result.ok(dagService.getInstance(instanceId));
    }

    /**
     * 查询节点执行明细。
     *
     * @param instanceId 实例 ID
     * @return 节点执行明细列表
     */
    @Operation(summary = "节点执行明细")
    @GetMapping("/instance/{instanceId}/nodes")
    public Result<List<DagNodeInstanceDO>> listNodeInstances(@PathVariable String instanceId) {
        return Result.ok(dagService.listNodeInstances(instanceId));
    }

    /**
     * 执行请求 DTO。
     */
    @Data
    public static class ExecuteRequest {
        /** 全局输入参数 */
        private Map<String, Object> inputs;
    }

    // ==================== P1-7: 新增 CRUD + 验证接口 ====================

    /**
     * 更新 DAG 定义。
     *
     * @param id  DAG 定义 ID
     * @param dag 更新后的 DAG 定义结构
     * @return 更新后的 DAG 定义
     */
    @Operation(summary = "更新 DAG 定义")
    @Idempotent(key = "dag:update-definition", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<DagDefinitionDO> updateDefinition(
            @PathVariable String id,
            @Valid @RequestBody DagDefinition dag) {
        return Result.ok(dagService.updateDefinition(id, dag));
    }

    /**
     * 删除 DAG 定义（软删除）。
     *
     * @param id DAG 定义 ID
     * @return 空结果
     */
    @Operation(summary = "删除 DAG 定义")
    @Idempotent(key = "dag:delete-definition", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDefinition(@PathVariable String id) {
        dagService.deleteDefinition(id);
        return Result.ok();
    }

    /**
     * 启用/禁用 DAG 定义。
     *
     * @param id      DAG 定义 ID
     * @param enabled 是否启用
     * @return 更新后的 DAG 定义
     */
    @Operation(summary = "启用/禁用 DAG 定义")
    @Idempotent(key = "dag:toggle-enabled", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/toggle")
    public Result<DagDefinitionDO> toggleEnabled(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        return Result.ok(dagService.toggleEnabled(id, enabled));
    }

    /**
     * 验证 DAG 定义结构（不执行）。
     *
     * @param dag DAG 定义结构
     * @return 验证结果
     */
    @Operation(summary = "验证 DAG 定义结构")
    @Idempotent(key = "dag:validate-definition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/validate")
    public Result<ValidationResult> validateDefinition(@Valid @RequestBody DagDefinition dag) {
        return Result.ok(dagService.validateDefinition(dag));
    }

    /**
     * 调试运行 DAG（不持久化结果）。
     *
     * @param req 调试请求（含 DAG 定义与全局输入参数）
     * @return DAG 执行结果
     */
    @Operation(summary = "调试运行 DAG")
    @Idempotent(key = "dag:debug-execute", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/debug")
    public Result<DagExecutionResult> debugExecute(
            @Valid @RequestBody DebugRequest req) {
        return Result.ok(dagService.executeDirect(req.getDag(), req.getInputs()));
    }

    /**
     * 调试运行请求 DTO。
     */
    @Data
    public static class DebugRequest {
        /** DAG 定义 */
        private DagDefinition dag;
        /** 全局输入参数 */
        private Map<String, Object> inputs;
    }
}
