package com.njydsz.workflow.web.controller.instance;

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

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 任务辅助操作与待办推送 Controller（从原 FlowTaskController 拆分而来）
 *
 * <p>承担审批中心的辅助操作能力与待办数实时推送，对标钉钉 / 飞书审批中心的
 * "减签 / 已阅 / 沟通 / 暂存 / 追加处理人 / 取回 / 挂起 / 激活"以及 WebSocket 待办数推送。
 * 核心能力包括：
 * <ul>
 *   <li><b>减签</b>：{@code POST /task/countersignRemove} — 从会签任务中移除指定审批人（GAP-P1）</li>
 *   <li><b>已阅</b>：{@code POST /task/{taskId}/read} — 标记任务已阅（GAP-P2）</li>
 *   <li><b>沟通</b>：{@code POST /task/communicate} — 在任务下添加沟通评论（GAP-P2）</li>
 *   <li><b>暂存</b>：{@code POST /task/saveDraft} — 审批人保存审批意见草稿（GAP-P0）</li>
 *   <li><b>追加处理人</b>：{@code POST /task/addApprover} — 在已有会签任务中追加审批人（GAP-P0）</li>
 *   <li><b>取回</b>：{@code POST /task/{hisTaskId}/retract} — 审批人已审后取回（P1-3）</li>
 *   <li><b>挂起</b>：{@code POST /task/{taskId}/suspend} — 任务级挂起（P2-1）</li>
 *   <li><b>激活</b>：{@code POST /task/{taskId}/activate} — 任务级激活（P2-1）</li>
 *   <li><b>待办数查询</b>：{@code GET /todo/count} — HTTP 拉模式待办数（P1-7，WebSocket 兜底）</li>
 *   <li><b>待办数推送</b>：{@code POST /todo/pushMine} — 手动触发推送当前用户待办数到 WebSocket（P1-7）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限码；辅助操作额外校验
 * 「操作人 == 任务办理人」防越权。
 *
 * <p><b>限流 / 幂等：</b>高频辅助操作通过 {@link RateLimit} 限流；
 * 幂等操作（取回 / 暂存 / 推送）通过 {@link Idempotent} 注解 5s 防重。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传、权限校验，所有业务逻辑下沉到
 * {@link FlowTaskService}（辅助子 Service）、{@link WorkflowFacade}（门面编排）
 * 与 {@link FlowTodoCountPushService}（WebSocket 推送）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowTaskService 任务服务门面
 * @see WorkflowFacade 工作流门面（业务编排）
 * @see FlowTodoCountPushService 待办数推送服务
 * @see FlowTaskController 核心任务操作（单任务）
 */
@Slf4j
@RestController
@Tag(name = "workflow-task-aux", description = "工作流任务辅助操作与待办推送接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskAuxController {

    /** 任务服务（辅助子 Service） */
    private final FlowTaskService taskService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** P1-7: WebSocket 待办数实时推送服务 */
    private final FlowTodoCountPushService todoCountPushService;

    // ============== GAP-P1: 减签 / GAP-P2: 已阅 / 沟通 / 暂存 / 追加处理人 ==============

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:countersignRemove:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.countersignRemove", threshold = 50)
    @PostMapping("/task/countersignRemove")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'countersignRemove'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignRemove(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        taskService.countersignRemove(dto);
        return BaseResponse.success();
    }

    /**
     * GAP-P2: 已阅 — 标记任务已阅
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:markRead:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.markRead", threshold = 50)
    @PostMapping("/task/{taskId}/read")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'markRead'")
    public BaseResponse<Void> markRead(@PathVariable String taskId) {
        String userId = AuthContext.getUserId();
        taskService.markRead(taskId, userId);
        return BaseResponse.success();
    }

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:communicate:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.communicate", threshold = 50)
    @PostMapping("/task/communicate")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'communicate'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> communicate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        taskService.communicate(dto);
        return BaseResponse.success();
    }

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:saveDraft:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.saveDraft", threshold = 50)
    @PostMapping("/task/saveDraft")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'saveDraft'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> saveDraft(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.saveDraft(dto);
        return BaseResponse.success();
    }

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:addApprover:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.addApprover", threshold = 50)
    @PostMapping("/task/addApprover")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.APPROVE, content = "'addApprover'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> addApprover(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.addApprover(dto);
        return BaseResponse.success();
    }

    /**
     * P1-3: 取回审批 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
     *
     * <p>对标钉钉/飞书"取回"。仅审批人本人可操作，且下一节点待办必须未处理。
     *
     * @param hisTaskId 历史任务 ID（ydsz_flow_his_task.id）
     * @param comment   取回说明（可选）
     * @return 统一响应结果，包含新创建的待办任务 ID
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:retract:lock", ttlSeconds = 5)
    @PostMapping("/task/{hisTaskId}/retract")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'retract'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<String> retract(@PathVariable String hisTaskId,
                                  @RequestParam(required = false) String comment) {
        return BaseResponse.success(taskService.retract(hisTaskId, AuthContext.getUserId(), comment));
    }

    /**
     * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
     *
     * <p>对标钉钉/飞书"任务挂起"。挂起期间不计超时，激活后回到 PENDING 需重新签收。
     * 与实例级挂起（{@code /instance/suspend}）的区别：仅挂起指定任务，其它任务不受影响。
     *
     * @param taskId 任务 ID
     * @param reason 挂起原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:suspendTask:lock", ttlSeconds = 5)
    @PostMapping("/task/{taskId}/suspend")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.DISABLE, content = "'suspendTask'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> suspendTask(@PathVariable String taskId,
                                    @RequestParam(required = false) String reason) {
        workflowFacade.suspendTask(taskId, AuthContext.getUserId(), reason);
        return BaseResponse.success();
    }

    /**
     * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:activateTask:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.activateTask", threshold = 50)
    @PostMapping("/task/{taskId}/activate")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.ENABLE, content = "'activateTask'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> activateTask(@PathVariable String taskId) {
        workflowFacade.activateTask(taskId, AuthContext.getUserId());
        return BaseResponse.success();
    }

    // ============== P1-7: WebSocket 待办数实时推送 ==============

    /**
     * P1-7: 查询当前用户的待办数（HTTP 拉模式，作为 WebSocket 推送的兜底）
     *
     * @return 包含 todoCount、userId、timestamp 的响应
     */
    @GetMapping("/todo/count")
    public BaseResponse<Map<String, Object>> myTodoCount() {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.success(Map.of("userId", 0, "todoCount", 0));
        }
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        // P0-1 修复：移除 countOverdue 死代码（结果被覆盖），直接用 listTodoByUser 计算待办数
        var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        long count = tasks == null ? 0 : tasks.size();
        return BaseResponse.success(Map.of(
                "userId", userId,
                "todoCount", count,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * P1-7: 手动触发推送当前用户待办数到 WebSocket（前端重连后调一次同步）
     *
     * @return 是否成功
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:pushMyTodoCount:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowtask.pushMyTodoCount", threshold = 50)
    @PostMapping("/todo/pushMine")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'pushMyTodoCount'")
    public BaseResponse<Boolean> pushMyTodoCount() {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.success(false);
        }
        todoCountPushService.pushTodoCount(userId);
        return BaseResponse.success(true);
    }
}
