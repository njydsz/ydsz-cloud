package com.njydsz.workflow.web.controller.instance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 任务查询与统计 Controller（从原 FlowTaskController 拆分而来）
 *
 * <p>承担审批中心的查询与运营统计能力，对标钉钉 / 飞书审批中心"我的审批 / 数据看板"模块。
 * 核心能力包括：
 * <ul>
 *   <li><b>待办查询</b>：{@code GET /task/todo} — 当前用户的待办任务列表</li>
 *   <li><b>已办查询</b>：{@code GET /task/done} — 当前用户的已办任务列表</li>
 *   <li><b>超期查询</b>：{@code GET /task/overdue} — 超期任务列表（P2-32）</li>
 *   <li><b>超时标记</b>：{@code POST /task/{taskId}/timeout} — 管理员手动标记任务超时（P2-36）</li>
 *   <li><b>已办多维筛选</b>：{@code GET /task/done/search} — 已办分页查询（P2-33）</li>
 *   <li><b>节点耗时统计</b>：{@code GET /stats/nodeDuration} — 按节点统计平均耗时（P2-31）</li>
 *   <li><b>超期统计</b>：{@code GET /stats/overdue} — 超期任务列表（stats/overdue 别名，前端兼容）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>查询接口默认按当前登录用户过滤数据；管理员接口（timeoutTask）
 * 通过 {@link Idempotent} 注解 5s 防重。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传、VO 转换，所有查询逻辑下沉到
 * {@link FlowTaskService}（查询/统计子 Service）与 {@link WorkflowFacade}（门面编排）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowTaskService 任务服务门面
 * @see WorkflowFacade 工作流门面（业务编排）
 * @see FlowTaskController 核心任务操作（单任务）
 */
@Slf4j
@RestController
@Tag(name = "workflow-task-query", description = "工作流任务查询与统计接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskQueryController {

    /** 任务服务（查询/统计子 Service） */
    private final FlowTaskService taskService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;

    // ============== 查询与统计 ==============

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
        return BaseResponse.success(workflowFacade.listTodoTasks(AuthContextUtils.getUserId(), page, size));
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
        return BaseResponse.success(workflowFacade.listDoneTasks(AuthContextUtils.getUserId(), page, size));
    }

    /**
     * P2-32: 查询超期任务
     *
     * @param assigneeId 办理人 ID（可选，为空时查全部）
     * @param tenantId   租户 ID（可选）
     * @return 统一响应结果，包含超期任务列表
     */
    @GetMapping("/task/overdue")
    public BaseResponse<List<FlowRunTaskVO>> overdue(@RequestParam(required = false) String assigneeId,
                                         @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowRunTaskListToVO(taskService.listOverdue(assigneeId, tid)));
    }

    /**
     * P2-36: 标记任务超时（管理员手动标记）
     *
     * @param taskId 任务 ID
     * @param reason 超时原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowTaskController:timeoutTask:lock", ttlSeconds = 5)
    @PostMapping("/task/{taskId}/timeout")
    @Audit(module = "流程任务", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'timeoutTask'")
    public BaseResponse<Void> timeoutTask(@PathVariable String taskId,
                                    @RequestParam(required = false) String reason) {
        taskService.timeoutTask(taskId, reason);
        return BaseResponse.success();
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
    public PageResult<FlowRunTaskVO> doneSearch(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        BaseResponse<FlowRunTask> pageResult = taskService.listDoneByAssigneePageMulti(assigneeId, businessType,
                flowCode, startTime, endTime, tid, pageNo, pageSize);
        List<FlowRunTask> tasks = pageResult.getData();
        List<FlowRunTaskVO> vos = WorkflowConverter.INSTANT.flowRunTaskListToVO(tasks);
        return PageResult.success(pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
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
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(taskService.nodeDurationStats(flowCode, tid));
    }

    /**
     * P0-3: 超期任务列表（stats/overdue 别名，前端兼容）
     *
     * @param assigneeId 办理人 ID（可空）
     * @return 超期任务列表
     */
    @GetMapping("/stats/overdue")
    public BaseResponse<List<FlowRunTaskVO>> statsOverdue(
            @RequestParam(required = false) String assigneeId) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowRunTaskListToVO(taskService.listOverdue(assigneeId, tenantId)));
    }
}
