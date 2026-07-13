package com.njydsz.pmis.workflow.web.controller.instance;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.safe.annotation.RateLimit;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import com.njydsz.pmis.workflow.server.service.FlowTodoCountPushService;
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
 * 节点耗时与超期统计
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
        return BaseResponse.ok(workflowFacade.getTaskDetail(taskId));
    }

    /**
     * 签收任务
     *
     * <p>P0-1 修复：用户 ID 从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:claim", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/claim")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> claim(@RequestParam String taskId) {
        workflowFacade.claimTask(taskId, AuthContext.getUserId());
        return BaseResponse.ok();
    }

    /**
     * 通过任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @RateLimit(key = "flowTask:pass", qps = 10, windowSeconds = 60, message = "审批操作过于频繁，请稍后重试")
    @Idempotent(key = "flowTask:pass", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/pass")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> pass(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.completeTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 驳回任务
     *
     * @param dto 任务操作参数（可含 targetNodeCode 指定驳回目标；不填则按流程默认）
     * @return 统一响应结果
     */
    @RateLimit(key = "flowTask:reject", qps = 10, windowSeconds = 60, message = "审批操作过于频繁，请稍后重试")
    @Idempotent(key = "flowTask:reject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/reject")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> reject(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.rejectTask(dto);
        return BaseResponse.ok();
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
        FlowRunTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return BaseResponse.ok(List.of());
        }
        List<Map<String, Object>> nodes = hisTaskMapper.listPassedNodes(task.getInstanceId());
        return BaseResponse.ok(nodes);
    }

    /**
     * 转办任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:transfer", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/transfer")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> transfer(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.transferTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 委派任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:delegate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/delegate")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> delegate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.delegateTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 前加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:countersignBefore", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/countersignBefore")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignBefore(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.countersignBeforeTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 后加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:countersignAfter", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/countersignAfter")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignAfter(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.countersignAfterTask(dto);
        return BaseResponse.ok();
    }

    /**
     * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:countersignParallel", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/countersignParallel")
    @Operation(summary = "并加签（与原审批人并行审批）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignParallel(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.countersignParallelTask(dto);
        return BaseResponse.ok();
    }

    /**
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:jump", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/jump")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> jump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.jumpTask(dto);
        return BaseResponse.ok();
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
    @Idempotent(key = "flowTask:freeJump", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/freeJump")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_FREE_JUMP)
    public BaseResponse<Void> freeJump(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        dto.setAction("JUMP");
        workflowFacade.jumpTask(dto);
        return BaseResponse.ok();
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
    @Idempotent(key = "flowTask:batchPass", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/batchPass")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchPass(@RequestParam List<String> taskIds,
                                  @RequestParam(required = false) String comment) {
        workflowFacade.batchPassTasks(taskIds, AuthContext.getUserId(), comment);
        return BaseResponse.ok();
    }

    /**
     * P1-4: 批量驳回 — 对多个任务逐一执行 reject，任一失败整批回滚。
     *
     * @param taskIds        任务 ID 列表
     * @param comment        审批意见
     * @param targetNodeCode 退回目标节点编码（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:batchReject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/batchReject")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchReject(@RequestParam List<String> taskIds,
                                    @RequestParam(required = false) String comment,
                                    @RequestParam(required = false) String targetNodeCode) {
        taskService.batchReject(taskIds, AuthContext.getUserId(), comment, targetNodeCode);
        return BaseResponse.ok();
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
    @Idempotent(key = "flowTask:batchTransfer", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/batchTransfer")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchTransfer(@RequestParam List<String> taskIds,
                                      @RequestParam(required = false) String comment,
                                      @RequestParam String targetUserId,
                                      @RequestParam(required = false) String targetUserName) {
        taskService.batchTransfer(taskIds, AuthContext.getUserId(), comment,
                targetUserId, targetUserName);
        return BaseResponse.ok();
    }

    /**
     * P1-4: 批量催办 — 对多个实例逐一执行 urge，单个失败不影响其他。
     *
     * @param instanceIds 实例 ID 列表
     * @param comment     催办说明
     * @return 统一响应结果，包含成功催办的实例数量
     */
    @Idempotent(key = "flowTask:batchUrge", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/instance/batchUrge")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Integer> batchUrge(@RequestParam List<String> instanceIds,
                                     @RequestParam(required = false) String comment) {
        return BaseResponse.ok(taskService.batchUrge(instanceIds, AuthContext.getUserId(), comment));
    }

    /**
     * GAP-P0-4: 一键通过所有待办 — 查询当前用户全部待办（上限 100 条）并逐一通过。
     *
     * <p>对标钉钉/飞书审批中心"一键通过"按钮。
     *
     * @param comment 审批意见（可选）
     * @return 统一响应结果，包含实际通过的任务数量
     */
    @Idempotent(key = "flowTask:passAll", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/passAll")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Integer> passAll(@RequestParam(required = false) String comment) {
        return BaseResponse.ok(workflowFacade.passAllTodoTasks(AuthContext.getUserId(), comment));
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
    public BaseResponse<List<Map<String, Object>>> todo(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return BaseResponse.ok(workflowFacade.listTodoTasks(AuthContext.getUserId(), page, size));
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
    public BaseResponse<List<Map<String, Object>>> done(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return BaseResponse.ok(workflowFacade.listDoneTasks(AuthContext.getUserId(), page, size));
    }

    /**
     * P2-32: 查询超期任务
     *
     * @param assigneeId 办理人 ID（可选，为空时查全部）
     * @param tenantId   租户 ID（可选）
     * @return 统一响应结果，包含超期任务列表
     */
    @GetMapping("/task/overdue")
    public BaseResponse<List<FlowRunTaskDO>> overdue(@RequestParam(required = false) String assigneeId,
                                         @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(taskService.listOverdue(assigneeId, tid));
    }

    /**
     * P2-36: 标记任务超时（管理员手动标记）
     *
     * @param taskId 任务 ID
     * @param reason 超时原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:timeoutTask", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/timeout")
    public BaseResponse<Void> timeoutTask(@PathVariable String taskId,
                                    @RequestParam(required = false) String reason) {
        taskService.timeoutTask(taskId, reason);
        return BaseResponse.ok();
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
    public BaseResponse<PageResponse<FlowRunTaskDO>> doneSearch(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(taskService.listDoneByAssigneePageMulti(assigneeId, businessType,
                flowCode, startTime, endTime, tid, pageNo, pageSize));
    }

    // ============== GAP-P1: 减签 / GAP-P2: 已阅 / 沟通 / 暂存 / 追加处理人 ==============

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:countersignRemove", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/countersignRemove")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> countersignRemove(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        taskService.countersignRemove(dto);
        return BaseResponse.ok();
    }

    /**
     * GAP-P2: 已阅 — 标记任务已阅
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:markRead", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/read")
    public BaseResponse<Void> markRead(@PathVariable String taskId) {
        String userId = AuthContext.getUserId();
        taskService.markRead(taskId, userId);
        return BaseResponse.ok();
    }

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:communicate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/communicate")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> communicate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        taskService.communicate(dto);
        return BaseResponse.ok();
    }

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:saveDraft", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/saveDraft")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> saveDraft(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.saveDraft(dto);
        return BaseResponse.ok();
    }

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:addApprover", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/addApprover")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> addApprover(@Valid @RequestBody FlowTaskOperateDTO dto) {
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.addApprover(dto);
        return BaseResponse.ok();
    }

    /**
     * P1-3: 取回审批 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
     *
     * <p>对标钉钉/飞书"取回"。仅审批人本人可操作，且下一节点待办必须未处理。
     *
     * @param hisTaskId 历史任务 ID（pmis_flow_his_task.id）
     * @param comment   取回说明（可选）
     * @return 统一响应结果，包含新创建的待办任务 ID
     */
    @Idempotent(key = "flowTask:retract", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{hisTaskId}/retract")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<String> retract(@PathVariable String hisTaskId,
                                  @RequestParam(required = false) String comment) {
        return BaseResponse.ok(taskService.retract(hisTaskId, AuthContext.getUserId(), comment));
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
    @Idempotent(key = "flowTask:suspendTask", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/suspend")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> suspendTask(@PathVariable String taskId,
                                    @RequestParam(required = false) String reason) {
        workflowFacade.suspendTask(taskId, AuthContext.getUserId(), reason);
        return BaseResponse.ok();
    }

    /**
     * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowTask:activateTask", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/activate")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> activateTask(@PathVariable String taskId) {
        workflowFacade.activateTask(taskId, AuthContext.getUserId());
        return BaseResponse.ok();
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
            return BaseResponse.ok(Map.of("userId", 0, "todoCount", 0));
        }
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        // P0-1 修复：移除 countOverdue 死代码（结果被覆盖），直接用 listTodoByUser 计算待办数
        var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        long count = tasks == null ? 0 : tasks.size();
        return BaseResponse.ok(Map.of(
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
    @Idempotent(key = "flowTask:pushMyTodoCount", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/todo/pushMine")
    public BaseResponse<Boolean> pushMyTodoCount() {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.ok(false);
        }
        todoCountPushService.pushTodoCount(userId);
        return BaseResponse.ok(true);
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
    public BaseResponse<List<Map<String, Object>>> nodeDurationStats(
            @RequestParam String flowCode,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(taskService.nodeDurationStats(flowCode, tid));
    }

    /**
     * P0-3: 超期任务列表（stats/overdue 别名，前端兼容）
     *
     * @param assigneeId 办理人 ID（可空）
     * @return 超期任务列表
     */
    @GetMapping("/stats/overdue")
    public BaseResponse<List<FlowRunTaskDO>> statsOverdue(
            @RequestParam(required = false) String assigneeId) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(taskService.listOverdue(assigneeId, tenantId));
    }
}
