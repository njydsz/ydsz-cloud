package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowInstanceVariablesDTO;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
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
@RequestMapping("/api/v1/workflow/engine")
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
    @PostMapping("/instance/start")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<String> startProcess(@Valid @RequestBody FlowStartProcessDTO dto) {
        return Result.ok(workflowFacade.startProcess(dto));
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
     * @param id 流程实例 ID
     * @return 统一响应结果，包含是否撤回成功
     */
    @PostMapping("/instance/{id}/recall")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_START)
    public Result<Boolean> recall(@PathVariable String id) {
        return Result.ok(workflowFacade.recallProcess(id, SecurityContext.getUserId()));
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
    @PostMapping("/instance/{id}/rollback")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_ROLLBACK)
    public Result<Boolean> rollback(@PathVariable String id,
                                    @RequestParam String reason,
                                    @RequestParam(required = false, defaultValue = "7") int maxRollbackDays) {
        Long instanceId = Long.parseLong(id);
        return Result.ok(instanceService.rollback(instanceId, SecurityContext.getUserId(), reason, maxRollbackDays));
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
            @RequestParam(required = false) Long initiatorId,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(instanceService.page(businessType, initiatorId, flowStatus,
                startTime, endTime, tid, pageNo, pageSize));
    }

    /**
     * GAP-P0-1: 全部流程实例查询（管理员视图）
     *
     * <p>对标钉钉/飞书/企微审批中心"全部"Tab。需要 {@code workflow:monitor:view} 权限。
     * 与 {@code /instance/page} 的区别：本端点语义为"管理员看全部"，强制不按 initiatorId 过滤，
     * 返回精简 Map 结构（避免泄露定义内部字段）。
     *
     * @param page         页码
     * @param size         每页大小
     * @param businessType 业务类型（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @return 统一响应结果，包含实例 Map 列表
     */
    @GetMapping("/instance/all")
    @PrePermission(PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public Result<List<Map<String, Object>>> instanceAll(
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
    public Result<Map<String, Object>> getVariables(@PathVariable @Min(1) Long id) {
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
    @PostMapping("/instance/{id}/variables")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> setVariables(@PathVariable @Min(1) Long id,
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
    @PostMapping("/instance/{id}/urge")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_VIEW)
    public Result<List<String>> urge(@PathVariable @Min(1) Long id,
                                 @RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.urgeTask(id, SecurityContext.getUserId(), comment));
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
            @PathVariable @Min(1) Long instanceId,
            @RequestParam(required = false) Long taskId) {
        return Result.ok(instanceService.getFormRenderData(instanceId, taskId));
    }
}
