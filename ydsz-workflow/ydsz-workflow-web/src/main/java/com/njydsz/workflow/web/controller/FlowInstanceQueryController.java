package com.njydsz.workflow.web.controller.instance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.FlowInstanceService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程实例查询 Controller — 查询与视图
 *
 * <p>流程实例的 HTTP 入口，承担工作流引擎「运行时」的查询与视图渲染：
 * 审计轨迹 / 时间线 / 流程图 / 回放 / 分页查询 / 我发起的 / 全部实例（管理员视图）。
 *
 * <p><b>业务背景：</b>对标钉钉 / 飞书 / 企微审批中心的"查询视图"能力。Controller 仅做参数透传，
 * 所有业务逻辑下沉到 {@link FlowInstanceService} 与 {@link WorkflowFacade}。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>审计与时间线</b>：{@code GET /instance/{id}/auditTrail}（审计轨迹） /
 *       {@code /instance/{id}/timeline}（合并历史任务 + 审计日志 + 当前待办的统一时间线）</li>
 *   <li><b>流程图与回放</b>：{@code GET /instance/{id}/diagram}（流程图，高亮当前节点） /
 *       {@code /instance/{id}/replay}（回放步骤序列，驱动前端 FlowDiagramReplay 组件）</li>
 *   <li><b>分页查询</b>：{@code GET /instance/page}（多维分页） /
 *       {@code /instance/my}（我发起的） /
 *       {@code /instance/all}（管理员视图，需要 workflow:monitor:view 权限）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>管理员视图接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_MONITOR_VIEW} 权限码；其他查询接口默认登录即可访问。
 *
 * <p><b>拆分说明：</b>本类从原 {@code FlowInstanceController} 拆分而来，仅保留查询与视图类接口。
 * 启动与控制类接口见 {@link FlowInstanceController}；
 * 变量 / 表单 / 催办类接口见 {@link FlowInstanceVariableController}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowInstanceService 流程实例服务
 * @see WorkflowFacade 工作流门面
 * @see FlowInstanceController 启动与控制接口
 * @see FlowInstanceVariableController 变量 / 表单 / 催办接口
 */
@Slf4j
@RestController
@Tag(name = "workflow-instance-query", description = "工作流流程实例查询与视图接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowInstanceQueryController {

    /** 流程实例服务（P2-23/P2-24 分页查询与变量读写） */
    private final FlowInstanceService instanceService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;

    /**
     * 审计轨迹查询
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含审计轨迹列表
     */
    @GetMapping("/instance/{id}/auditTrail")
    public BaseResponse<List<Map<String, Object>>> auditTrail(@PathVariable String id) {
        return BaseResponse.success(workflowFacade.listAuditTrail(id));
    }

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含时间线列表
     */
    @GetMapping("/instance/{id}/timeline")
    public BaseResponse<List<Map<String, Object>>> timeline(@PathVariable String id) {
        return BaseResponse.success(workflowFacade.getTimeline(id));
    }

    /**
     * P2-22: 流程图查询（高亮当前节点）
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含 definition / nodes / skips，nodes 中每个节点带 active 标记
     */
    @GetMapping("/instance/{id}/diagram")
    public BaseResponse<Map<String, Object>> diagram(@PathVariable String id) {
        return BaseResponse.success(workflowFacade.getDiagram(id));
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
    public BaseResponse<List<Map<String, Object>>> replay(@PathVariable String id) {
        return BaseResponse.success(workflowFacade.getReplaySteps(id));
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
    public BaseResponse<List<FlowInstanceVO>> instancePage(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String initiatorId,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        BaseResponse<FlowInstance> pageResult = instanceService.page(businessType, initiatorId, flowStatus,
                startTime, endTime, tid, pageNo, pageSize);
        List<FlowInstance> instances = pageResult.getData();
        List<FlowInstanceVO> vos = WorkflowConverter.INSTANT.flowInstanceListToVO(instances);
        return BaseResponse.successPage(pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
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
    public BaseResponse<List<FlowInstanceVO>> instanceMy(
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        BaseResponse<FlowInstance> pageResult = instanceService.page(null, AuthContextUtils.getUserId(), status,
                startTime, endTime, AuthContextUtils.getTenantIdOrDefault("1"),
                pageNum, pageSize);
        List<FlowInstance> instances = pageResult.getData();
        List<FlowInstanceVO> vos = WorkflowConverter.INSTANT.flowInstanceListToVO(instances);
        return BaseResponse.successPage(pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
    }

    /**
     * GAP-P0-1: 全部流程实例查询（管理员视图）
     *
     * <p>对标钉钉/飞书/企微审批中心"全部"Tab。需要 {@code workflow:monitor:view} 权限。
     * 与 {@code /instance/page} 的区别：本端点语义为"管理员看全部"，强制不按 initiatorId 过滤，
     * 返回精简 Map 结构（避免泄露定义内部字段）。
     *
     * <p>P0-2 修复：返回类型由 {@code List<Map>} 改为 {@code BaseResponse<Map>}，
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
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
    public BaseResponse<Map<String, Object>> instanceAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return workflowFacade.listAllInstances(businessType, flowStatus,
                startTime, endTime, page, size);
    }
}
