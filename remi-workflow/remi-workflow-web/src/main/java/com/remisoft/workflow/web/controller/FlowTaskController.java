package com.remisoft.workflow.web.controller.instance;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContextUtils;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import com.remisoft.workflow.WorkflowFacade;
import com.remisoft.workflow.domain.dto.FlowTaskOperateDTO;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.infra.mapper.FlowHisTaskMapper;
import com.remisoft.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 核心任务操作 Controller（单任务办理入口）
 *
 * <p>流程任务的 HTTP 入口，对标钉钉 / 飞书审批中心接口。承担审批人对单个任务的
 * 核心办理动作：查看 / 签收 / 通过 / 驳回 / 转办 / 委派 / 加签 / 跳转。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>任务详情</b>：{@code GET /task/{taskId}}（含历史轨迹 / 表单权限）</li>
 *   <li><b>办理动作</b>：{@code POST /task/claim}（签收） / {@code pass}（通过） /
 *       {@code reject}（驳回） / {@code transfer}（转办） / {@code delegate}（委派）</li>
 *   <li><b>会签</b>：{@code POST /task/countersignBefore}（前加签） /
 *       {@code countersignAfter}（后加签） / {@code countersignParallel}（并加签）</li>
 *   <li><b>跳转</b>：{@code POST /task/jump}（管理员强制跳转） /
 *       {@code freeJump}（办理人自由流跳转）</li>
 *   <li><b>驳回候选节点</b>：{@code GET /task/{taskId}/rejectableNodes}</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_TASK_VIEW} / {@code WORKFLOW_TASK_OPERATE} /
 * {@code WORKFLOW_INSTANCE_CONTROL} / {@code WORKFLOW_TASK_FREE_JUMP} 等权限码；
 * 办理动作额外校验「操作人 == 任务办理人」防越权。
 *
 * <p><b>限流：</b>通过 / 驳回 / 转办 / 加签等高频操作通过 {@link RateLimit} 限流，
 * 防止恶意刷接口；幂等操作通过 {@link Idempotent} 注解 5s 防重。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传、权限校验、VO 转换，所有业务逻辑下沉到
 * {@link FlowTaskService}（门面模式），底层由 4 个子 Service（Query/Complete/Sign/Batch）协作。
 *
 * <p><b>拆分说明：</b>原 FlowTaskController 承担全部 34 个任务相关接口，已按职责拆分为 4 个 Controller：
 * <ul>
 *   <li>本类 — 核心单任务办理操作（12 个接口）</li>
 *   <li>{@link FlowTaskBatchController} — 批量操作（批量通过 / 驳回 / 转办 / 催办 / 一键通过）</li>
 *   <li>{@link FlowTaskQueryController} — 查询与统计（待办 / 已办 / 超期 / 节点耗时 / 超期统计）</li>
 *   <li>{@link FlowTaskAuxController} — 辅助操作与待办推送（减签 / 已阅 / 沟通 / 暂存 / 取回 / 挂起 / 激活 / 待办数推送）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowTaskService 任务服务门面
 * @see WorkflowFacade 工作流门面（业务编排）
 * @see FlowTaskOperateDTO 任务操作 DTO
 * @see FlowRunTask 运行时任务实体
 * @see FlowTaskBatchController 批量操作 Controller
 * @see FlowTaskQueryController 查询与统计 Controller
 * @see FlowTaskAuxController 辅助操作与待办推送 Controller
 */
@Slf4j
@RestController
@Tag(name = "workflow-task", description = "工作流任务操作接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskController {

    /** 任务服务（P2-31/32/33 耗时统计/超期统计/多维筛选） */
    private final FlowTaskService taskService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** P1-1: 历史任务 mapper（驳回候选目标节点） */
    private final FlowHisTaskMapper hisTaskMapper;

    // ============== 任务操作 ==============

    /**
     * P2-20: 任务详情查询
     *
     * @param taskId 任务 ID
     * @return 统一响应结果，包含任务详情
     */
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_VIEW)
    @GetMapping("/task/{taskId}")
    public BaseResponse<Map<String, Object>> taskDetail(@PathVariable String taskId) {
        return BaseResponse.success(workflowFacade.getTaskDetail(taskId));
    }

    /**
     * 签收任务
     *
     * <p>P0-1 修复：用户 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:claim:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.claim", threshold = 50)
    @PostMapping("/task/claim")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'claim'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> claim(@RequestParam String taskId) {
        workflowFacade.claimTask(taskId, AuthContextUtils.getUserId());
        return BaseResponse.success();
    }

    /**
     * 通过任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:pass:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.pass", threshold = 50)
    @PostMapping("/task/pass")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.APPROVE, content = "'pass'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> pass(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.completeTask(dto);
        return BaseResponse.success();
    }

    /**
     * 驳回任务
     *
     * @param dto 任务操作参数（可含 targetNodeCode 指定驳回目标；不填则按流程默认）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:reject:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.reject", threshold = 50)
    @PostMapping("/task/reject")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.REJECT, content = "'reject'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> reject(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.rejectTask(dto);
        return BaseResponse.success();
    }

    /**
     * P1-1: 查询任务所属实例经过的历史节点（驳回候选目标）
     *
     * <p>前端在打开"驳回"弹窗前调用本接口，渲染"驳回到"下拉列表。
     *
     * @param taskId 任务 ID
     * @return 该任务所属实例经过的历史节点列表（按首次完成时间正序）
     */
    @GetMapping("/task/{taskId}/rejectableNodes")
    public BaseResponse<List<Map<String, Object>>> rejectableNodes(@PathVariable String taskId) {
        FlowRunTask task = taskService.getById(taskId);
        if (task == null) {
            return BaseResponse.success(List.of());
        }
        List<Map<String, Object>> nodes = hisTaskMapper.listPassedNodes(task.getInstanceId());
        return BaseResponse.success(nodes);
    }

    /**
     * 转办任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:transfer:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.transfer", threshold = 50)
    @PostMapping("/task/transfer")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.GRANT, content = "'transfer'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> transfer(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.transferTask(dto);
        return BaseResponse.success();
    }

    /**
     * 委派任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:delegate:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.delegate", threshold = 50)
    @PostMapping("/task/delegate")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.GRANT, content = "'delegate'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> delegate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.delegateTask(dto);
        return BaseResponse.success();
    }

    /**
     * 前加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:countersignBefore:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.countersignBefore", threshold = 50)
    @PostMapping("/task/countersignBefore")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'countersignBefore'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignBefore(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.countersignBeforeTask(dto);
        return BaseResponse.success();
    }

    /**
     * 后加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:countersignAfter:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.countersignAfter", threshold = 50)
    @PostMapping("/task/countersignAfter")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'countersignAfter'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignAfter(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.countersignAfterTask(dto);
        return BaseResponse.success();
    }

    /**
     * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:countersignParallel:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.countersignParallel", threshold = 50)
    @PostMapping("/task/countersignParallel")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'countersignParallel'")
    @Operation(summary = "并加签（与原审批人并行审批）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignParallel(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.countersignParallelTask(dto);
        return BaseResponse.success();
    }

    /**
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:jump:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.jump", threshold = 50)
    @PostMapping("/task/jump")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'jump'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> jump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        workflowFacade.jumpTask(dto);
        return BaseResponse.success();
    }

    /**
     * GAP-P2-9: 自由流跳转 — 当前办理人运行时动态指定下一节点 + 办理人
     *
     * <p>对标钉钉/飞书"自由流"：与 {@code /task/jump}（管理员强制跳转）的区别：
     * <ul>
     *   <li>权限码：{@code WORKFLOW_TASK_FREE_JUMP}（普通办理人可用，非管理员专属）</li>
     *   <li>白名单校验：目标节点必须 {@code ext.freeJump=true} 才允许跳转</li>
     *   <li>显式办理人：{@code dto.targetAssignees} 非空时覆盖目标节点默认办理人</li>
     * </ul>
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode + action=JUMP，可选 targetAssignees）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:workflow:FlowTaskController:freeJump:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.freeJump", threshold = 50)
    @PostMapping("/task/freeJump")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'freeJump'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_FREE_JUMP)
    public BaseResponse<Void> freeJump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContextUtils.getUserId());
        dto.setUserName(AuthContextUtils.getUsername());
        dto.setAction("JUMP");
        workflowFacade.jumpTask(dto);
        return BaseResponse.success();
    }
}
