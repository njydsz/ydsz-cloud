package com.njydsz.workflow.web.controller.instance;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 移动端适配 Controller
 *
 * <p>P1-3: 对标钉钉/飞书移动端审批能力，提供精简字段、快速操作、
 * 一站式首页概览等移动端专属接口。
 *
 * <p>与 PC 端 {@link FlowTaskController} 的区别：
 * <ul>
 *   <li>响应体仅包含移动端必要字段，减少 60%+ payload</li>
 *   <li>提供首页聚合接口（待办数/已办数/超期数/待办列表一次返回）</li>
 *   <li>快速审批/驳回接口（仅需 taskId + comment）</li>
 *   <li>支持标记优先级排序的待办列表</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-mobile", description = "工作流移动端适配接口")
@RequestMapping("/workflow/mobile")
@RequiredArgsConstructor
@Validated
public class FlowMobileController {

    private final FlowTaskService taskService;
    private final WorkflowFacade workflowFacade;

    // ==================== 首页聚合 ====================

    /**
     * 移动端首页聚合数据
     *
     * <p>一次请求返回：待办数、已办数、超期数、待办列表（Top 5），
     * 减少移动端首屏请求次数。
     *
     * @return 首页聚合数据
     */
    @GetMapping("/home")
    @Operation(summary = "移动端首页聚合数据")
    public BaseResponse<Map<String, Object>> home() {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.ok(Map.of(
                    "todoCount", 0,
                    "doneCount", 0,
                    "overdueCount", 0,
                    "todoList", List.of()
            ));
        }
        String tenantId = AuthContext.getTenantIdOrDefault("1");

        List<FlowRunTaskDO> todoTasks = taskService.listTodoByUser(userId, null, null, tenantId);
        int todoCount = todoTasks == null ? 0 : todoTasks.size();

        List<FlowRunTaskDO> doneTasks = taskService.listDoneByAssignee(userId, tenantId);
        int doneCount = doneTasks == null ? 0 : doneTasks.size();

        List<FlowRunTaskDO> overdueTasks = taskService.listOverdue(userId, tenantId);
        int overdueCount = overdueTasks == null ? 0 : overdueTasks.size();

        // Top 5 待办（按优先级降序、创建时间升序）
        List<MobileTodoVO> topTodos = todoTasks == null ? List.of() :
                todoTasks.stream()
                        .sorted(Comparator
                                .comparing(FlowRunTaskDO::getPriority,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(FlowRunTaskDO::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(5)
                        .map(MobileTodoVO::from)
                        .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todoCount", todoCount);
        result.put("doneCount", doneCount);
        result.put("overdueCount", overdueCount);
        result.put("todoList", topTodos);
        result.put("timestamp", System.currentTimeMillis());
        return BaseResponse.ok(result);
    }

    // ==================== 精简待办列表 ====================

    /**
     * 移动端待办列表（精简字段）
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 20，上限 50）
     * @return 精简待办列表
     */
    @GetMapping("/todo")
    @Operation(summary = "移动端待办列表")
    public BaseResponse<List<MobileTodoVO>> todo(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.ok(List.of());
        }
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        List<FlowRunTaskDO> tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        if (tasks == null || tasks.isEmpty()) {
            return BaseResponse.ok(List.of());
        }
        // 按优先级降序、创建时间升序排序
        List<FlowRunTaskDO> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator
                .comparing(FlowRunTaskDO::getPriority,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FlowRunTaskDO::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int fromIndex = Math.min((page - 1) * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());
        List<MobileTodoVO> result = sorted.subList(fromIndex, toIndex).stream()
                .map(MobileTodoVO::from)
                .toList();
        return BaseResponse.ok(result);
    }

    // ==================== 精简已办列表 ====================

    /**
     * 移动端已办列表（精简字段）
     *
     * @param page 页码（默认 1）
     * @param size 每页大小（默认 20，上限 50）
     * @return 精简已办列表
     */
    @GetMapping("/done")
    @Operation(summary = "移动端已办列表")
    public BaseResponse<List<MobileTodoVO>> done(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        String userId = AuthContext.getUserId();
        if (userId == null) {
            return BaseResponse.ok(List.of());
        }
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        List<FlowRunTaskDO> tasks = taskService.listDoneByAssignee(userId, tenantId);
        if (tasks == null || tasks.isEmpty()) {
            return BaseResponse.ok(List.of());
        }
        // 按完成时间降序
        List<FlowRunTaskDO> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator
                .comparing(FlowRunTaskDO::getFinishAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        int fromIndex = Math.min((page - 1) * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());
        List<MobileTodoVO> result = sorted.subList(fromIndex, toIndex).stream()
                .map(MobileTodoVO::from)
                .toList();
        return BaseResponse.ok(result);
    }

    // ==================== 精简任务详情 ====================

    /**
     * 移动端任务详情（精简字段）
     *
     * @param taskId 任务 ID
     * @return 精简任务详情
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "移动端任务详情")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_VIEW)
    public BaseResponse<MobileTaskDetailVO> taskDetail(@PathVariable String taskId) {
        Map<String, Object> detail = workflowFacade.getTaskDetail(taskId);
        if (detail == null || detail.isEmpty()) {
            return BaseResponse.ok(null);
        }
        return BaseResponse.ok(MobileTaskDetailVO.from(detail));
    }

    // ==================== 快速操作 ====================

    /**
     * 快速通过（仅需 taskId + comment）
     *
     * @param taskId  任务 ID
     * @param comment 审批意见（可选）
     * @return 操作结果
     */
    @Idempotent(key = "flowMobile:quickPass", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/quickPass")
    @Operation(summary = "移动端快速通过")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> quickPass(@PathVariable String taskId,
                                    @RequestParam(required = false) String comment) {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(taskId);
        dto.setComment(comment);
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.completeTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 快速驳回（仅需 taskId + comment）
     *
     * @param taskId  任务 ID
     * @param comment 驳回意见
     * @return 操作结果
     */
    @Idempotent(key = "flowMobile:quickReject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/{taskId}/quickReject")
    @Operation(summary = "移动端快速驳回")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> quickReject(@PathVariable String taskId,
                                      @RequestParam(required = false) String comment) {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(taskId);
        dto.setComment(comment);
        dto.setUserId(AuthContext.getUserId());
        dto.setUserName(AuthContext.getUsername());
        workflowFacade.rejectTask(dto);
        return BaseResponse.ok();
    }

    /**
     * 移动端批量通过
     *
     * @param taskIds 任务 ID 列表
     * @param comment 审批意见（可选）
     * @return 成功数
     */
    @Idempotent(key = "flowMobile:batchPass", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/task/batchPass")
    @Operation(summary = "移动端批量通过")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Void> batchPass(@RequestParam List<String> taskIds,
                                    @RequestParam(required = false) String comment) {
        workflowFacade.batchPassTasks(taskIds, AuthContext.getUserId(), comment);
        return BaseResponse.ok();
    }

    // ==================== 精简审批轨迹 ====================

    /**
     * 移动端审批轨迹（精简字段）
     *
     * @param instanceId 实例 ID
     * @return 精简时间线
     */
    @GetMapping("/instance/{instanceId}/timeline")
    @Operation(summary = "移动端审批轨迹")
    public BaseResponse<List<MobileTimelineVO>> timeline(@PathVariable String instanceId) {
        List<Map<String, Object>> timeline = workflowFacade.getTimeline(instanceId);
        if (timeline == null || timeline.isEmpty()) {
            return BaseResponse.ok(List.of());
        }
        List<MobileTimelineVO> result = timeline.stream()
                .map(MobileTimelineVO::from)
                .toList();
        return BaseResponse.ok(result);
    }

    // ==================== 移动端 VO ====================

    /**
     * 移动端待办/已办列表项 VO（精简字段）
     */
    @Data
    public static class MobileTodoVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 任务 ID */
        private String taskId;
        /** 流程名称 */
        private String flowName;
        /** 节点名称 */
        private String nodeName;
        /** 业务单号 */
        private String businessNo;
        /** 业务类型 */
        private String businessType;
        /** 任务状态 */
        private String taskStatus;
        /** 优先级（0-9，越大越紧急） */
        private Integer priority;
        /** 是否超期 */
        private Boolean overdue;
        /** 创建时间 */
        private LocalDateTime createTime;
        /** 截止时间 */
        private LocalDateTime dueAt;

        static MobileTodoVO from(FlowRunTaskDO task) {
            MobileTodoVO vo = new MobileTodoVO();
            vo.taskId = task.getId();
            vo.flowName = task.getFlowName();
            vo.nodeName = task.getNodeName();
            vo.businessNo = task.getBusinessNo();
            vo.businessType = task.getBusinessType();
            vo.taskStatus = task.getTaskStatus();
            vo.priority = task.getPriority();
            vo.createTime = task.getCreatedAt();
            vo.dueAt = task.getDueAt();
            vo.overdue = task.getDueAt() != null && task.getDueAt().isBefore(LocalDateTime.now());
            return vo;
        }
    }

    /**
     * 移动端任务详情 VO（精简字段）
     */
    @Data
    public static class MobileTaskDetailVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 任务 ID */
        private String taskId;
        /** 实例 ID */
        private String instanceId;
        /** 流程名称 */
        private String flowName;
        /** 节点名称 */
        private String nodeName;
        /** 业务单号 */
        private String businessNo;
        /** 业务类型 */
        private String businessType;
        /** 任务状态 */
        private String taskStatus;
        /** 办理人 ID */
        private String assigneeId;
        /** 办理人姓名 */
        private String assigneeName;
        /** 优先级 */
        private Integer priority;
        /** 审批意见 */
        private String comment;
        /** 创建时间 */
        private LocalDateTime createTime;
        /** 截止时间 */
        private LocalDateTime dueAt;
        /** 是否超期 */
        private Boolean overdue;
        /** 可操作列表 */
        private List<String> actions;

        static MobileTaskDetailVO from(Map<String, Object> detail) {
            MobileTaskDetailVO vo = new MobileTaskDetailVO();
            vo.taskId = (String) detail.get("taskId");
            vo.instanceId = (String) detail.get("instanceId");
            vo.flowName = (String) detail.get("flowName");
            vo.nodeName = (String) detail.get("nodeName");
            vo.businessNo = (String) detail.get("businessNo");
            vo.businessType = (String) detail.get("businessType");
            vo.taskStatus = (String) detail.get("taskStatus");
            vo.assigneeId = (String) detail.get("assigneeId");
            vo.assigneeName = (String) detail.get("assigneeName");
            vo.priority = detail.get("priority") instanceof Number n
                    ? n.intValue() : null;
            vo.comment = (String) detail.get("comment");
            Object ct = detail.get("createTime");
            vo.createTime = ct instanceof LocalDateTime ldt ? ldt : null;
            Object due = detail.get("dueAt");
            vo.dueAt = due instanceof LocalDateTime ldt ? ldt : null;
            vo.overdue = vo.dueAt != null && vo.dueAt.isBefore(LocalDateTime.now());
            // 根据状态推断可操作列表
            vo.actions = new ArrayList<>();
            if ("PENDING".equals(vo.taskStatus) || "CLAIMED".equals(vo.taskStatus)) {
                vo.actions.addAll(List.of("PASS", "REJECT", "TRANSFER", "DELEGATE"));
            }
            return vo;
        }
    }

    /**
     * 移动端审批轨迹 VO（精简字段）
     */
    @Data
    public static class MobileTimelineVO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 类型：TASK / AUDIT / TODO */
        private String type;
        /** 节点名称 */
        private String nodeName;
        /** 操作人 ID */
        private String operatorId;
        /** 操作人姓名 */
        private String operatorName;
        /** 动作：PASS/REJECT/TRANSFER 等 */
        private String action;
        /** 审批意见 */
        private String comment;
        /** 时间 */
        private LocalDateTime timestamp;

        static MobileTimelineVO from(Map<String, Object> item) {
            MobileTimelineVO vo = new MobileTimelineVO();
            vo.type = (String) item.get("type");
            vo.nodeName = (String) item.get("nodeName");
            vo.operatorId = (String) item.get("operatorId");
            vo.operatorName = (String) item.get("operatorName");
            vo.action = (String) item.get("action");
            vo.comment = (String) item.get("comment");
            Object ts = item.get("timestamp");
            vo.timestamp = ts instanceof LocalDateTime ldt ? ldt : null;
            return vo;
        }
    }
}
