package com.remisoft.workflow.web.controller.instance;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import com.remisoft.workflow.WorkflowFacade;
import com.remisoft.workflow.domain.dto.FlowInstanceViewDTO;
import com.remisoft.workflow.domain.dto.FlowStartProcessDTO;
import com.remisoft.workflow.server.service.FlowInstanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程实例 Controller — 启动与控制操作
 *
 * <p>流程实例的 HTTP 入口，承担工作流引擎「运行时」的启动与生命周期控制：
 * 启动 / 批量启动 / 业务查询 / 终止 / 挂起 / 激活 / 撤回 / 回滚 / 重审提交。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>启动</b>：{@code POST /instance/start}（单条） /
 *       {@code POST /instance/batchStart}（批量） — 幂等保护 + 审计日志 + 50 QPS 限流</li>
 *   <li><b>业务查询</b>：{@code GET /instance/byBusiness}（按业务类型 + 业务 ID 查询实例视图）</li>
 *   <li><b>控制</b>：{@code POST /instance/{id}/terminate}（终止） /
 *       {@code /suspend}（挂起） / {@code /activate}（激活） /
 *       {@code /recall}（撤回） / {@code /rollback}（回滚） /
 *       {@code /resubmit}（驳回后快速重审）</li>
 *   <li><b>撤回节点</b>：{@code GET /instance/{id}/recallableNodes}（可撤回历史节点列表）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_INSTANCE_START} 等权限码；
 * 启动类接口通过 {@link Audit} 注解写入审计日志（{@link AuditType#OPERATION} + {@link AuditAction#CREATE}）。
 *
 * <p><b>限流：</b>启动类接口通过 {@link RateLimit} 限流（{@code 50 QPS}），
 * 终止 / 撤回等高危操作通过 {@link Idempotent} 5s 防重。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验；所有业务逻辑下沉到
 * {@link FlowInstanceService} 与 {@link WorkflowFacade}。
 *
 * <p><b>拆分说明：</b>本类从原 {@code FlowInstanceController} 拆分而来，仅保留启动与控制操作。
 * 查询与视图类接口见 {@link FlowInstanceQueryController}；
 * 变量 / 表单 / 催办类接口见 {@link FlowInstanceVariableController}。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowInstanceService 流程实例服务
 * @see WorkflowFacade 工作流门面
 * @see FlowStartProcessDTO 启动参数 DTO
 * @see FlowInstanceQueryController 查询与视图接口
 * @see FlowInstanceVariableController 变量 / 表单 / 催办接口
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:startProcess:lock", ttlSeconds = 5)
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'启动流程:' + #dto.flowCode")
    @RateLimit(resource = "workflow.flowinstance.startProcess", threshold = 50)
    @PostMapping("/instance/start")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
    public BaseResponse<String> startProcess(@Valid @RequestBody FlowStartProcessDTO dto) {
        return BaseResponse.success(workflowFacade.startProcess(dto));
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
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'批量启动流程: ' + #dtos.size() + ' 条")
    @PostMapping("/instance/batchStart")
    @Operation(summary = "批量启动流程实例")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
    public BaseResponse<Map<String, Object>> batchStartInstances(
            @Valid @RequestBody List<FlowStartProcessDTO> dtos) {
        return BaseResponse.success(instanceService.batchStartInstances(dtos));
    }

    /**
     * 按业务类型与业务 ID 查询流程实例
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @return 统一响应结果，包含流程实例视图
     */
    @GetMapping("/instance/byBusiness")
    public BaseResponse<FlowInstanceViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return BaseResponse.success(workflowFacade.getByBusiness(businessType, businessId));
    }

    /**
     * 终止流程实例
     *
     * @param id     流程实例 ID
     * @param reason 终止原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowInstanceController:terminate:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/terminate")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'terminate'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFacade.terminateProcess(id, reason);
        return BaseResponse.success();
    }

    /**
     * 挂起流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowInstanceController:suspend:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowinstance.suspend", threshold = 50)
    @PostMapping("/instance/{id}/suspend")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.DISABLE, content = "'suspend'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> suspend(@PathVariable String id) {
        workflowFacade.suspendProcess(id);
        return BaseResponse.success();
    }

    /**
     * 激活流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowInstanceController:activate:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowinstance.activate", threshold = 50)
    @PostMapping("/instance/{id}/activate")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.ENABLE, content = "'activate'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> activate(@PathVariable String id) {
        workflowFacade.activateProcess(id);
        return BaseResponse.success();
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:recall:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/recall")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'recall'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
    public BaseResponse<Boolean> recall(@PathVariable String id,
                                  @RequestParam(required = false) String targetNodeCode) {
        return BaseResponse.success(instanceService.recall(id, AuthContext.getUserId(), targetNodeCode));
    }

    /**
     * P1-1: 查询可撤回的历史节点列表。
     *
     * <p>返回当前实例已办过的历史节点（排除当前待办节点），供前端展示"撤回到"选择列表。
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含可撤回节点列表
     */
    @GetMapping("/instance/{id}/recallableNodes")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
    public BaseResponse<List<Map<String, Object>>> listRecallableNodes(@PathVariable String id) {
        return BaseResponse.success(instanceService.listRecallableNodes(id, AuthContext.getUserId()));
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:rollback:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/rollback")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.RESTORE, content = "'rollback'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_ROLLBACK)
    public BaseResponse<Boolean> rollback(@PathVariable String id,
                                    @RequestParam String reason,
                                    @RequestParam(required = false, defaultValue = "7") int maxRollbackDays) {
        return BaseResponse.success(instanceService.rollback(id, AuthContext.getUserId(), reason, maxRollbackDays));
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:resubmit:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/resubmit")
    @Audit(module = "流程实例", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resubmit'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_RESUBMIT)
    public BaseResponse<String> resubmit(@PathVariable String id,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false, defaultValue = "RESTART") String redoMode,
                                    @RequestBody(required = false) Map<String, Object> variables) {
        return BaseResponse.success(workflowFacade.resubmitProcess(id, AuthContext.getUserId(),
                variables, comment, redoMode));
    }
}
