package com.njydsz.pmis.workflow.controller.instance;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.instance.FlowInstanceVariablesDTO;
import com.njydsz.pmis.workflow.dto.instance.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.instance.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.service.instance.FlowInstanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例 Controller
 *
 * <p>流程实例的启动 / 查询 / 控制 / 变量读写 / 表单渲染
 * （P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-instance", description = "工作流流程实例接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowInstanceController {

    /** 流程实例服务（P2-23/P2-24 分页查询与变量读写） */
    private final FlowInstanceService instanceService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;

    /**
     * 启动流程实例
     *
     * @param dto 流程启动参数
     * @return 统一响应结果，包含流程实例 ID
     */
    @Idempotent(key = "flow-instance:start-process", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/start")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<String> startProcess(@Valid @RequestBody FlowStartProcessDTO dto) {
        return Result.ok(workflowFacade.startProcess(dto));
    }

    /**
     * P2-6: 批量启动流程实例。
     *
     * <p>对标钉钉/飞书"批量发起审批"能力：一次性提交多个流程实例，每个实例独立事务，
     * 单个失败不影响其他实例的发起。适用于"批量立项"、"批量报销"等场景。
     *
     * <p>行为约定：
     * <ul>
     *   <li>每个 {@link FlowStartProcessDTO} 独立事务，失败记录到 failedItems</li>
     *   <li>限制单次批量最大 100 条</li>
     *   <li>幂等性由 {@link #startProcess} 内部保证（同 businessType+businessId 已有 RUNNING 实例时返回原 ID）</li>
     * </ul>
     *
     * @param dtos 流程启动参数列表
     * @return 统一响应结果，包含 successCount / failedCount / instanceIds / failedItems
     */
    @PostMapping("/instance/batch-start")
    @Operation(summary = "批量启动流程实例")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<Map<String, Object>> batchStartInstances(
            @Valid @RequestBody List<FlowStartProcessDTO> dtos) {
        return Result.ok(instanceService.batchStartInstances(dtos));
    }

    /**
     * 按业务类型与业务 ID 查询流程实例
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @return 统一响应结果，包含流程实例视图
     */
    @GetMapping("/instance/byBusiness")
    public Result<FlowInstanceViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return Result.ok(workflowFacade.getByBusiness(businessType, businessId));
    }

    /**
     * 终止流程实例
     *
     * @param id     流程实例 ID
     * @param reason 终止原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "flow-instance:terminate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/terminate")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFacade.terminateProcess(id, reason);
        return Result.ok();
    }

    /**
     * 挂起流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flow-instance:suspend", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/suspend")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> suspend(@PathVariable String id) {
        workflowFacade.suspendProcess(id);
        return Result.ok();
    }

    /**
     * 激活流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flow-instance:activate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/activate")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> activate(@PathVariable String id) {
        workflowFacade.activateProcess(id);
        return Result.ok();
    }

    /**
     * 撤回流程（仅发起人可撤回，仅运行中可撤回）
     *
     * <p>P0-1 修复：发起人 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * <p>P1-1 扩展：支持 targetNodeCode 参数，撤回到指定历史节点；为空时撤回到开始节点下游第一节点。
     *
     * @param id              流程实例 ID
     * @param targetNodeCode  目标节点编码（可选，为空时撤回到开始节点下游第一节点）
     * @return 统一响应结果，包含是否撤回成功
     */
    @Idempotent(key = "flow-instance:recall", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/recall")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<Boolean> recall(@PathVariable String id,
                                  @RequestParam(required = false) String targetNodeCode) {
        return Result.ok(instanceService.recall(id, SecurityContext.getUserId(), targetNodeCode));
    }

    /**
     * P1-1: 查询可撤回的历史节点列表。
     *
     * <p>返回当前实例已办过的历史节点（排除当前待办节点），供前端展示"撤回到"选择列表。
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含可撤回节点列表
     */
    @GetMapping("/instance/{id}/recallable-nodes")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<List<Map<String, Object>>> listRecallableNodes(@PathVariable String id) {
        return Result.ok(instanceService.listRecallableNodes(id, SecurityContext.getUserId()));
    }

    /**
     * P2-3: 回滚已完成的流程实例（撤销）
     *
     * <p>对标钉钉/飞书的"撤销审批"能力。仅 COMPLETED 状态、回滚时间窗口内（默认 7 天）、
     * 发起人或拥有 workflow:instance:rollback 权限的管理员可执行。
     *
     * <p>P0-1 修复：操作人 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param id              流程实例 ID
     * @param reason          回滚原因
     * @param maxRollbackDays 允许回滚的最大天数（可选，默认 7）
     * @return 统一响应结果，包含是否回滚成功
     */
    @Idempotent(key = "flow-instance:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/rollback")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_ROLLBACK)
    public Result<Boolean> rollback(@PathVariable String id,
                                    @RequestParam String reason,
                                    @RequestParam(required = false, defaultValue = "7") int maxRollbackDays) {
        return Result.ok(instanceService.rollback(id, SecurityContext.getUserId(), reason, maxRollbackDays));
    }

    /**
     * P2-2 (GAP-10): 驳回后快速重审 — 基于被驳回的原实例重新提交
     *
     * <p>仅发起人或拥有 workflow:instance:resubmit 权限的管理员可操作。
     *
     * <p>P1-8: 支持 redoMode 参数：
     * <ul>
     *   <li>RESTART（默认）：仅 REJECTED 实例可重做，在原实例上重置状态并从开始节点重新推进；</li>
     *   <li>NEW_INSTANCE：任意终态（COMPLETED/REJECTED/TERMINATED/ROLLED_BACK）均可重做，
     *       创建全新实例，复用原实例的 flowCode/businessType/businessId/initiator，合并变量。</li>
     * </ul>
     */
    @Idempotent(key = "flow-instance:resubmit", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/resubmit")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_RESUBMIT)
    public Result<String> resubmit(@PathVariable String id,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false, defaultValue = "RESTART") String redoMode,
                                    @RequestBody(required = false) java.util.Map<String, Object> variables) {
        return Result.ok(workflowFacade.resubmitProcess(id, SecurityContext.getUserId(),
                variables, comment, redoMode));
    }

    /**
     * 审计轨迹查询
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含审计轨迹列表
     */
    @GetMapping("/instance/{id}/auditTrail")
    public Result<List<Map<String, Object>>> auditTrail(@PathVariable String id) {
        return Result.ok(workflowFacade.listAuditTrail(id));
    }

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含时间线列表
     */
    @GetMapping("/instance/{id}/timeline")
    public Result<List<Map<String, Object>>> timeline(@PathVariable String id) {
        return Result.ok(workflowFacade.getTimeline(id));
    }

    /**
     * P2-22: 流程图查询（高亮当前节点）
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含 definition / nodes / skips，nodes 中每个节点带 active 标记
     */
    @GetMapping("/instance/{id}/diagram")
    public Result<Map<String, Object>> diagram(@PathVariable String id) {
        return Result.ok(workflowFacade.getDiagram(id));
    }

    /**
     * P2-4: 流程回放步骤序列
     *
     * <p>按时间顺序合并历史任务 + 审计日志 + 当前待办为统一步骤序列，驱动前端
     * {@code FlowDiagramReplay} 组件依次高亮节点。
     *
     * @param id 流程实例 ID
     * @return 步骤列表（按 timestamp 升序）
     */
    @GetMapping("/instance/{id}/replay")
    public Result<List<Map<String, Object>>> replay(@PathVariable String id) {
        return Result.ok(workflowFacade.getReplaySteps(id));
    }

    /**
     * P2-23: 实例多维分页查询
     *
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果，包含分页实例列表
     */
    @GetMapping("/instance/page")
    public Result<PageResult<FlowInstanceDO>> instancePage(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String initiatorId,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(instanceService.page(businessType, initiatorId, flowStatus,
                startTime, endTime, tid, pageNo, pageSize));
    }

    /**
     * P0-1: 我发起的流程实例分页查询（登录用户视图）
     *
     * <p>对标钉钉/飞书/企微审批中心"我发起的"Tab。按当前登录用户 ID 过滤，
     * 仅返回当前用户发起的流程实例。
     *
     * <p>前端传入的 flowCode / flowName 参数与 {@link FlowInstanceService#page}
     * 的入参无直接对应（flowCode 不等于 businessType），本端点忽略这两个参数，
     * 仅使用 status / startTime / endTime / pageNum / pageSize。
     *
     * @param flowCode  流程编码（可选，当前不参与过滤，保留以兼容前端入参）
     * @param flowName  流程名称（可选，当前不参与过滤，保留以兼容前端入参）
     * @param status    流程状态（可选，对应 flowStatus）
     * @param startTime 开始时间下界（可选）
     * @param endTime   开始时间上界（可选）
     * @param pageNum   页码（默认 1）
     * @param pageSize  每页大小（默认 20，最大 100）
     * @return 统一响应结果，包含分页实例列表
     */
    @GetMapping("/instance/my")
    public Result<PageResult<FlowInstanceDO>> instanceMy(
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.ok(instanceService.page(null, SecurityContext.getUserId(), status,
                startTime, endTime, SecurityContext.getTenantIdOrDefault("1"),
                pageNum, pageSize));
    }

    /**
     * GAP-P0-1: 全部流程实例查询（管理员视图）
     *
     * <p>对标钉钉/飞书/企微审批中心"全部"Tab。需要 {@code workflow:monitor:view} 权限。
     * 与 {@code /instance/page} 的区别：本端点语义为"管理员看全部"，强制不按 initiatorId 过滤，
     * 返回精简 Map 结构（避免泄露定义内部字段）。
     *
     * <p>P0-2 修复：返回类型由 {@code List<Map>} 改为 {@code PageResult<Map>}，
     * 保留 total / page / size，避免前端假分页。
     *
     * @param page         页码
     * @param size         每页大小
     * @param businessType 业务类型（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @return 统一响应结果，包含分页实例 Map 列表
     */
    @GetMapping("/instance/all")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<PageResult<Map<String, Object>>> instanceAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.ok(workflowFacade.listAllInstances(businessType, flowStatus,
                startTime, endTime, page, size));
    }

    /**
     * P2-24: 读取流程变量
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含变量 Map
     */
    @GetMapping("/instance/{id}/variables")
    public Result<Map<String, Object>> getVariables(@PathVariable String id) {
        return Result.ok(instanceService.getVariables(id));
    }

    /**
     * P2-24: 批量写入流程变量
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowInstanceVariablesDTO} 强类型 DTO。
     *
     * @param id  流程实例 ID
     * @param dto 变量 DTO
     * @return 统一响应结果
     */
    @Idempotent(key = "flow-instance:set-variables", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/variables")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> setVariables(@PathVariable String id,
                                     @Valid @RequestBody FlowInstanceVariablesDTO dto) {
        instanceService.setVariables(id, dto.getVariables());
        return Result.ok();
    }

    /**
     * 催办
     *
     * <p>P0-1 修复：操作人 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param id      流程实例 ID
     * @param comment 催办备注（可选）
     * @return 统一响应结果，包含被催办人列表
     */
    @Idempotent(key = "flow-instance:urge", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/urge")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_VIEW)
    public Result<List<String>> urge(@PathVariable String id,
                                 @RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.urgeTask(id, SecurityContext.getUserId(), comment));
    }

    /**
     * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
     *
     * <p>nodeCode 不传时退化为实例级催办。
     */
    @Idempotent(key = "flow-instance:urge-by-node", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/{id}/urge/node")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_VIEW)
    public Result<List<String>> urgeByNode(@PathVariable String id,
                                           @RequestParam(required = false) String nodeCode,
                                           @RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.urgeNodeTask(id, nodeCode, SecurityContext.getUserId(), comment));
    }

    /**
     * GAP-V2-02: 获取表单渲染数据 — 审批人打开待办时获取字段权限
     *
     * @param instanceId 流程实例 ID
     * @param taskId     任务 ID（可选，为空取当前节点）
     * @return 渲染数据（nodeCode / formFieldsConfig / variables）
     */
    @GetMapping("/instance/{instanceId}/form-render")
    public Result<Map<String, Object>> getFormRenderData(
            @PathVariable String instanceId,
            @RequestParam(required = false) String taskId) {
        return Result.ok(instanceService.getFormRenderData(instanceId, taskId));
    }
}
