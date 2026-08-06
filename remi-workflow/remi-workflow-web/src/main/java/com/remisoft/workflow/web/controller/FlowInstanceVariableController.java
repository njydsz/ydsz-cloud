package com.remisoft.workflow.web.controller.instance;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContextUtils;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import com.remisoft.workflow.WorkflowFacade;
import com.remisoft.workflow.domain.dto.FlowInstanceVariablesDTO;
import com.remisoft.workflow.server.service.FlowInstanceService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 流程实例变量 Controller — 变量 / 表单 / 催办
 *
 * <p>流程实例的 HTTP 入口，承担工作流引擎「运行时」的流程变量读写、表单渲染数据获取、
 * 催办（实例级 / 节点级）三类辅助交互。
 *
 * <p><b>业务背景：</b>对标钉钉 / 飞书 / 企微审批中心的"流程变量"与"催办"能力。
 * Controller 仅做参数透传，所有业务逻辑下沉到 {@link FlowInstanceService} 与 {@link WorkflowFacade}。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>变量读写</b>：{@code GET /instance/{id}/variables}（读取流程变量） /
 *       {@code POST /instance/{id}/variables}（批量写入流程变量，幂等 + 限流）</li>
 *   <li><b>催办</b>：{@code POST /instance/{id}/urge}（实例级催办） /
 *       {@code POST /instance/{id}/urge/node}（节点级催办，仅催办指定 nodeCode 的待办）</li>
 *   <li><b>表单渲染</b>：{@code GET /instance/{instanceId}/formRender}
 *       （审批人打开待办时获取字段权限与变量）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>变量写入与催办通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_INSTANCE_CONTROL} / {@link PermissionCodes#WORKFLOW_INSTANCE_VIEW} 权限码。
 *
 * <p><b>限流：</b>变量写入通过 {@link RateLimit} 限流（{@code 50 QPS}），
 * 催办类操作通过 {@link Idempotent} 5s 防重。
 *
 * <p><b>拆分说明：</b>本类从原 {@code FlowInstanceController} 拆分而来，仅保留变量 / 表单 / 催办类接口。
 * 启动与控制类接口见 {@link FlowInstanceController}；
 * 查询与视图类接口见 {@link FlowInstanceQueryController}。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowInstanceService 流程实例服务
 * @see WorkflowFacade 工作流门面
 * @see FlowInstanceVariablesDTO 流程变量 DTO
 * @see FlowInstanceController 启动与控制接口
 * @see FlowInstanceQueryController 查询与视图接口
 */
@Slf4j
@RestController
@Tag(name = "workflow-instance-variable", description = "工作流流程实例变量 / 表单 / 催办接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowInstanceVariableController {

    /** 流程实例服务（P2-23/P2-24 分页查询与变量读写） */
    private final FlowInstanceService instanceService;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;

    /**
     * P2-24: 读取流程变量
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含变量 Map
     */
    @GetMapping("/instance/{id}/variables")
    public BaseResponse<Map<String, Object>> getVariables(@PathVariable String id) {
        return BaseResponse.success(instanceService.getVariables(id));
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:setVariables:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowinstance.setVariables", threshold = 50)
    @PostMapping("/instance/{id}/variables")
    @Audit(module = "流程变量", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'setVariables'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public BaseResponse<Void> setVariables(@PathVariable String id,
                                     @Valid @RequestBody FlowInstanceVariablesDTO dto) {
        instanceService.setVariables(id, dto.getVariables());
        return BaseResponse.success();
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
    @Idempotent(key = "remi:workflow:FlowInstanceController:urge:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/urge")
    @Audit(module = "流程变量", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'urge'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
    public BaseResponse<List<String>> urge(@PathVariable String id,
                                 @RequestParam(required = false) String comment) {
        return BaseResponse.success(workflowFacade.urgeTask(id, AuthContextUtils.getUserId(), comment));
    }

    /**
     * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
     *
     * <p>nodeCode 不传时退化为实例级催办。
     */
    @Idempotent(key = "remi:workflow:FlowInstanceController:urgeByNode:lock", ttlSeconds = 5)
    @PostMapping("/instance/{id}/urge/node")
    @Audit(module = "流程变量", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'urgeByNode'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
    public BaseResponse<List<String>> urgeByNode(@PathVariable String id,
                                           @RequestParam(required = false) String nodeCode,
                                           @RequestParam(required = false) String comment) {
        return BaseResponse.success(workflowFacade.urgeNodeTask(id, nodeCode, AuthContextUtils.getUserId(), comment));
    }

    /**
     * GAP-V2-02: 获取表单渲染数据 — 审批人打开待办时获取字段权限
     *
     * @param instanceId 流程实例 ID
     * @param taskId     任务 ID（可选，为空取当前节点）
     * @return 渲染数据（nodeCode / formFieldsConfig / variables）
     */
    @GetMapping("/instance/{instanceId}/formRender")
    public BaseResponse<Map<String, Object>> getFormRenderData(
            @PathVariable String instanceId,
            @RequestParam(required = false) String taskId) {
        return BaseResponse.success(instanceService.getFormRenderData(instanceId, taskId));
    }
}
