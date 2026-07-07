package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowAiDraftCommentDTO;
import com.njydsz.pmis.workflow.dto.FlowAiRecommendApproversDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowAiAssistService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import com.njydsz.pmis.workflow.service.FlowTodoCountPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务操作 Controller
 *
 * <p>任务详情 / 签收 / 通过 / 驳回 / 转办 / 委派 / 加签 / 跳转 / 批量审批 /
 * 待办已办查询 / 减签 / 已阅 / 沟通 / 暂存 / 追加处理人 / 待办数推送 /
 * AI 推荐 / AI 起草意见 / 节点耗时与超期统计
 * （P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-task", description = "工作流任务操作接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskController {

    /** 任务服务（P2-31/32/33 耗时统计/超期统计/多维筛选） */
    private final FlowTaskService taskService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** P1-1: 历史任务 mapper（驳回候选目标节点） */
    private final FlowHisTaskMapper hisTaskMapper;
    /** P1-7: WebSocket 待办数实时推送服务 */
    private final FlowTodoCountPushService todoCountPushService;
    /** P2-1: 智能审批辅助服务（推荐审批人 / 起草意见） */
    private final FlowAiAssistService aiAssistService;

    // ============== 任务操作 ==============

    /**
     * P2-20: 任务详情查询
     *
     * @param taskId 任务 ID
     * @return 统一响应结果，包含任务详情
     */
    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> taskDetail(@PathVariable @Min(1) Long taskId) {
        return Result.ok(workflowFacade.getTaskDetail(taskId));
    }

    /**
     * 签收任务
     *
     * <p>P0-1 修复：用户 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @PostMapping("/task/claim")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> claim(@RequestParam Long taskId) {
        workflowFacade.claimTask(taskId, SecurityContext.getUserId());
        return Result.ok();
    }

    /**
     * 通过任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/pass")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> pass(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.completeTask(dto);
        return Result.ok();
    }

    /**
     * 驳回任务
     *
     * @param dto 任务操作参数（可含 targetNodeCode 指定驳回目标；不填则按流程默认）
     * @return 统一响应结果
     */
    @PostMapping("/task/reject")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> reject(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.rejectTask(dto);
        return Result.ok();
    }

    /**
     * P1-1: 查询任务所属实例经过的历史节点（驳回候选目标）
     *
     * <p>前端在打开"驳回"弹窗前调用本接口，渲染"驳回到"下拉列表。
     *
     * @param taskId 任务 ID
     * @return 该任务所属实例经过的历史节点列表（按首次完成时间正序）
     */
    @GetMapping("/task/{taskId}/rejectable-nodes")
    public Result<List<Map<String, Object>>> rejectableNodes(@PathVariable @Min(1) Long taskId) {
        FlowRunTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return Result.ok(List.of());
        }
        List<Map<String, Object>> nodes = hisTaskMapper.listPassedNodes(task.getInstanceId());
        return Result.ok(nodes);
    }

    /**
     * 转办任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/transfer")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> transfer(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.transferTask(dto);
        return Result.ok();
    }

    /**
     * 委派任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/delegate")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> delegate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.delegateTask(dto);
        return Result.ok();
    }

    /**
     * 前加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignBefore")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> countersignBefore(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.countersignBeforeTask(dto);
        return Result.ok();
    }

    /**
     * 后加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignAfter")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> countersignAfter(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.countersignAfterTask(dto);
        return Result.ok();
    }

    /**
     * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignParallel")
    @Operation(summary = "并加签（与原审批人并行审批）")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> countersignParallel(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.countersignParallelTask(dto);
        return Result.ok();
    }

    /**
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     * @return 统一响应结果
     */
    @PostMapping("/task/jump")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> jump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.jumpTask(dto);
        return Result.ok();
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
    @PostMapping("/task/freeJump")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_FREE_JUMP)
    public Result<Void> freeJump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        dto.setAction("JUMP");
        workflowFacade.jumpTask(dto);
        return Result.ok();
    }

    /**
     * P2-26: 批量审批 — 对多个任务逐一通过
     *
     * <p>P0-1 修复：操作人 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param taskIds 任务 ID 列表
     * @param comment 审批意见（可选）
     * @return 统一响应结果
     */
    @PostMapping("/task/batchPass")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> batchPass(@RequestParam List<Long> taskIds,
                                  @RequestParam(required = false) String comment) {
        workflowFacade.batchPassTasks(taskIds, SecurityContext.getUserId(), comment);
        return Result.ok();
    }

    /**
     * GAP-P0-4: 一键通过所有待办 — 查询当前用户全部待办（上限 100 条）并逐一通过。
     *
     * <p>对标钉钉/飞书审批中心"一键通过"按钮。
     *
     * @param comment 审批意见（可选）
     * @return 统一响应结果，包含实际通过的任务数量
     */
    @PostMapping("/task/passAll")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Integer> passAll(@RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.passAllTodoTasks(SecurityContext.getUserId(), comment));
    }

    /**
     * 待办任务查询
     *
     * <p>P0-1 修复：用户 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 统一响应结果，包含待办任务列表
     */
    @GetMapping("/task/todo")
    public Result<List<Map<String, Object>>> todo(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(workflowFacade.listTodoTasks(SecurityContext.getUserId(), page, size));
    }

    /**
     * 已办任务查询
     *
     * <p>P0-1 修复：用户 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 统一响应结果，包含已办任务列表
     */
    @GetMapping("/task/done")
    public Result<List<Map<String, Object>>> done(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(workflowFacade.listDoneTasks(SecurityContext.getUserId(), page, size));
    }

    /**
     * P2-32: 查询超期任务
     *
     * @param assigneeId 办理人 ID（可选，为空时查全部）
     * @param tenantId   租户 ID（可选）
     * @return 统一响应结果，包含超期任务列表
     */
    @GetMapping("/task/overdue")
    public Result<List<FlowRunTaskDO>> overdue(@RequestParam(required = false) String assigneeId,
                                         @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listOverdue(assigneeId, tid));
    }

    /**
     * P2-36: 标记任务超时（管理员手动标记）
     *
     * @param taskId 任务 ID
     * @param reason 超时原因（可选）
     * @return 统一响应结果
     */
    @PostMapping("/task/{taskId}/timeout")
    public Result<Void> timeoutTask(@PathVariable @Min(1) Long taskId,
                                    @RequestParam(required = false) String reason) {
        taskService.timeoutTask(taskId, reason);
        return Result.ok();
    }

    /**
     * P2-33: 已办多维筛选分页查询
     *
     * @param assigneeId   办理人 ID（可选）
     * @param businessType 业务类型（可选）
     * @param flowCode     流程编码（可选）
     * @param startTime    完成时间下界（可选）
     * @param endTime      完成时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @return 统一响应结果，包含分页已办列表
     */
    @GetMapping("/task/done/search")
    public Result<PageResult<FlowRunTaskDO>> doneSearch(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listDoneByAssigneePageMulti(assigneeId, businessType,
                flowCode, startTime, endTime, tid, pageNo, pageSize));
    }

    // ============== GAP-P1: 减签 / GAP-P2: 已阅 / 沟通 / 暂存 / 追加处理人 ==============

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId）
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignRemove")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> countersignRemove(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        taskService.countersignRemove(dto);
        return Result.ok();
    }

    /**
     * GAP-P2: 已阅 — 标记任务已阅
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @PostMapping("/task/{taskId}/read")
    public Result<Void> markRead(@PathVariable @Min(1) Long taskId) {
        Long userId = SecurityContext.getUserId();
        taskService.markRead(taskId, userId);
        return Result.ok();
    }

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @PostMapping("/task/communicate")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> communicate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        taskService.communicate(dto);
        return Result.ok();
    }

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @PostMapping("/task/saveDraft")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> saveDraft(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.saveDraft(dto);
        return Result.ok();
    }

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     * @return 统一响应结果
     */
    @PostMapping("/task/addApprover")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Void> addApprover(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(SecurityContext.getUserId());
        dto.setUserName(SecurityContext.getUsername());
        workflowFacade.addApprover(dto);
        return Result.ok();
    }

    // ============== P1-7: WebSocket 待办数实时推送 ==============

    /**
     * P1-7: 查询当前用户的待办数（HTTP 拉模式，作为 WebSocket 推送的兜底）
     *
     * @return 包含 todoCount、userId、timestamp 的响应
     */
    @GetMapping("/todo/count")
    public Result<Map<String, Object>> myTodoCount() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return Result.ok(Map.of("userId", 0, "todoCount", 0));
        }
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        // P0-1 修复：移除 countOverdue 死代码（结果被覆盖），直接用 listTodoByUser 计算待办数
        var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        long count = tasks == null ? 0 : tasks.size();
        return Result.ok(Map.of(
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
    @PostMapping("/todo/push-mine")
    public Result<Boolean> pushMyTodoCount() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return Result.ok(false);
        }
        todoCountPushService.pushTodoCount(userId);
        return Result.ok(true);
    }

    // ============== P2-1: 智能审批辅助 ==============

    /**
     * P2-1: 推荐审批人
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowAiRecommendApproversDTO} 强类型 DTO + JSR-303 校验。
     *
     * @param dto 推荐参数（taskId / context）
     * @return Top N 推荐审批人列表
     */
    @PostMapping("/ai/recommend-approvers")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<List<Map<String, Object>>> recommendApprovers(
            @Valid @RequestBody FlowAiRecommendApproversDTO dto) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("taskId", dto.getTaskId());
        if (dto.getContext() != null && !dto.getContext().isBlank()) {
            ctx.put("context", dto.getContext());
        }
        List<Map<String, Object>> candidates = List.of();
        int topN = 3;
        List<Map<String, Object>> top = aiAssistService.recommendApprovers(ctx, candidates, topN);
        return Result.ok(top);
    }

    /**
     * P2-1: 起草审批意见
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowAiDraftCommentDTO} 强类型 DTO + JSR-303 校验。
     *
     * @param dto 起草参数（taskId / approveAction / hint）
     * @return 起草意见结果
     */
    @PostMapping("/ai/draft-comment")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Map<String, Object>> draftComment(@Valid @RequestBody FlowAiDraftCommentDTO dto) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskId", dto.getTaskId());
        params.put("action", dto.getApproveAction());
        if (dto.getHint() != null && !dto.getHint().isBlank()) {
            params.put("hint", dto.getHint());
        }
        Map<String, Object> result = aiAssistService.draftComment(params);
        return Result.ok(result);
    }

    /**
     * P2-1: 检查 AI Agent 服务是否可用
     */
    @GetMapping("/ai/status")
    public Result<Map<String, Object>> aiStatus() {
        return Result.ok(Map.of(
                "available", aiAssistService.isAiAvailable(),
                "agents", List.of("APPROVER_RECOMMEND", "COMMENT_DRAFT")
        ));
    }

    // ============== P2-31/32/33: 审计运营统计 ==============

    /**
     * P2-31: 按节点统计平均耗时
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可选）
     * @return 统一响应结果，包含每个节点的平均耗时统计
     */
    @GetMapping("/stats/nodeDuration")
    public Result<List<Map<String, Object>>> nodeDurationStats(
            @RequestParam String flowCode,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.nodeDurationStats(flowCode, tid));
    }

    /**
     * P0-3: 超期任务列表（stats/overdue 别名，前端兼容）
     *
     * @param assigneeId 办理人 ID（可空）
     * @return 超期任务列表
     */
    @GetMapping("/stats/overdue")
    public Result<List<FlowRunTaskDO>> statsOverdue(
            @RequestParam(required = false) String assigneeId) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listOverdue(assigneeId, tenantId));
    }
}
