package com.remisoft.workflow.web.controller.instance;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.workflow.WorkflowFacade;
import com.remisoft.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 任务批量操作 Controller（从原 FlowTaskController 拆分而来）
 *
 * <p>承担审批人对多个任务/实例的批量操作能力，对标钉钉 / 飞书审批中心"批量审批"模块。
 * 核心能力包括：
 * <ul>
 *   <li><b>批量通过</b>：{@code POST /task/batchPass} — 对多个任务逐一通过</li>
 *   <li><b>批量驳回</b>：{@code POST /task/batchReject} — 对多个任务逐一驳回，任一失败整批回滚</li>
 *   <li><b>批量转办</b>：{@code POST /task/batchTransfer} — 对多个任务逐一转办，任一失败整批回滚</li>
 *   <li><b>批量催办</b>：{@code POST /instance/batchUrge} — 对多个实例逐一催办，单个失败不影响其他</li>
 *   <li><b>一键通过</b>：{@code POST /task/passAll} — 查询当前用户全部待办（上限 100 条）并逐一通过</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限码；批量操作额外校验
 * 「操作人 == 任务办理人」防越权。
 *
 * <p><b>幂等：</b>所有批量操作通过 {@link Idempotent} 注解 5s 防重，防止前端重复提交导致批量重复执行。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传、权限校验，所有业务逻辑下沉到
 * {@link FlowTaskService}（批量子 Service）与 {@link WorkflowFacade}（门面编排）。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowTaskService 任务服务门面
 * @see WorkflowFacade 工作流门面（业务编排）
 * @see FlowTaskController 核心任务操作（单任务）
 */
@Slf4j
@RestController
@Tag(name = "workflow-task-batch", description = "工作流任务批量操作接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskBatchController {

    /** 任务服务（批量子 Service） */
    private final FlowTaskService taskService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;

    // ============== 批量操作 ==============

    /**
     * P2-26: 批量审批 — 对多个任务逐一通过
     *
     * <p>P0-1 修复：操作人 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param taskIds 任务 ID 列表
     * @param comment 审批意见（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:batchPass:lock", ttlSeconds = 5)
    @PostMapping("/task/batchPass")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.APPROVE, content = "'batchPass'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchPass(@RequestParam List<String> taskIds,
                                  @RequestParam(required = false) String comment) {
        workflowFacade.batchPassTasks(taskIds, AuthContext.getUserId(), comment);
        return BaseResponse.success();
    }

    /**
     * P1-4: 批量驳回 — 对多个任务逐一执行 reject，任一失败整批回滚。
     *
     * @param taskIds        任务 ID 列表
     * @param comment        审批意见
     * @param targetNodeCode 退回目标节点编码（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:batchReject:lock", ttlSeconds = 5)
    @PostMapping("/task/batchReject")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.REJECT, content = "'batchReject'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchReject(@RequestParam List<String> taskIds,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false) String targetNodeCode) {
        taskService.batchReject(taskIds, AuthContext.getUserId(), comment, targetNodeCode);
        return BaseResponse.success();
    }

    /**
     * P1-4: 批量转办 — 对多个任务逐一执行 transfer，任一失败整批回滚。
     *
     * @param taskIds        任务 ID 列表
     * @param comment        转办说明
     * @param targetUserId   目标人 ID
     * @param targetUserName 目标人姓名
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:batchTransfer:lock", ttlSeconds = 5)
    @PostMapping("/task/batchTransfer")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.GRANT, content = "'batchTransfer'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchTransfer(@RequestParam List<String> taskIds,
                                      @RequestParam(required = false) String comment,
                                      @RequestParam String targetUserId,
                                      @RequestParam(required = false) String targetUserName) {
        taskService.batchTransfer(taskIds, AuthContext.getUserId(), comment,
                targetUserId, targetUserName);
        return BaseResponse.success();
    }

    /**
     * P1-4: 批量催办 — 对多个实例逐一执行 urge，单个失败不影响其他。
     *
     * @param instanceIds 实例 ID 列表
     * @param comment     催办说明
     * @return 统一响应结果，包含成功催办的实例数量
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:batchUrge:lock", ttlSeconds = 5)
    @PostMapping("/instance/batchUrge")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchUrge'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Integer> batchUrge(@RequestParam List<String> instanceIds,
                                     @RequestParam(required = false) String comment) {
        return BaseResponse.success(taskService.batchUrge(instanceIds, AuthContext.getUserId(), comment));
    }

    /**
     * GAP-P0-4: 一键通过所有待办 — 查询当前用户全部待办（上限 100 条）并逐一通过。
     *
     * <p>对标钉钉/飞书审批中心"一键通过"按钮。
     *
     * @param comment 审批意见（可选）
     * @return 统一响应结果，包含实际通过的任务数量
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:passAll:lock", ttlSeconds = 5)
    @PostMapping("/task/passAll")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.APPROVE, content = "'passAll'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Integer> passAll(@RequestParam(required = false) String comment) {
        return BaseResponse.success(workflowFacade.passAllTodoTasks(AuthContext.getUserId(), comment));
    }
}
