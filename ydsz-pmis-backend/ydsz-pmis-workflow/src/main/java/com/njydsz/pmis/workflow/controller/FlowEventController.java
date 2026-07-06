package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.entity.FlowNotifyChannelDO;
import com.njydsz.pmis.workflow.service.FlowEventSubscriptionService;
import com.njydsz.pmis.workflow.service.FlowNotifyChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 事件与通知通道 Controller
 *
 * <p>P0-1: BPMN 事件触发 / GAP-V2: 通知通道配置（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-event", description = "工作流事件与通知通道接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowEventController {

    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** P0-1: BPMN 事件订阅服务（消息关联 / 错误抛出） */
    private final FlowEventSubscriptionService eventSubscriptionService;
    /** GAP-V2: 通知通道配置服务 */
    private final FlowNotifyChannelService notifyChannelService;

    // ============== 引擎信息 ==============

    /**
     * 查询引擎信息
     *
     * @return 统一响应结果，包含引擎类型与可用性
     */
    @GetMapping("/info")
    @Operation(summary = "查询工作流引擎信息")
    public Result<Map<String, Object>> info() {
        return Result.ok(Map.of(
                "engineType", workflowFacade.engineType(),
                "available", true
        ));
    }

    // ============== P0-1: BPMN 事件触发 ==============

    /**
     * P0-1: 消息关联 — 外部系统通过消息名称触发 WAITING 的 MESSAGE 订阅
     *
     * <p>BPMN intermediateCatchEvent / boundaryEvent 配置 messageEventDefinition 后，
     * 流程推进到该节点时会创建 MESSAGE 类型订阅（WAITING）。
     * 外部系统调用本接口，按 messageName + correlationKey 匹配订阅并触发，
     * 触发后流程从事件捕获节点推进到下游。
     *
     * @param messageName    消息名称（对应 BPMN messageRef）
     * @param correlationKey 关联键（业务标识，可选）
     * @param payload        消息载荷 JSON（会合并到流程变量）
     * @param tenantId       租户 ID（可选）
     * @return 触发的订阅数量
     */
    @PostMapping("/event/correlate-message")
    public Result<Integer> correlateMessage(
            @RequestParam String messageName,
            @RequestParam(required = false) String correlationKey,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(eventSubscriptionService.correlateMessage(tid, messageName, correlationKey, payload));
    }

    /**
     * P0-1: 抛出错误 — 触发 WAITING 的 ERROR 订阅（边界错误事件）
     *
     * <p>BPMN boundaryEvent 配置 errorEventDefinition 后，附着在 userTask 上。
     * 当外部系统抛出匹配 errorCode 的错误时，取消 userTask，流程沿边界事件的出边推进。
     *
     * @param errorCode  错误代码（对应 BPMN errorRef）
     * @param instanceId 实例 ID（可选，为空则按 errorCode 全局匹配）
     * @param payload    错误载荷 JSON
     * @param tenantId   租户 ID（可选）
     * @return 触发的订阅数量
     */
    @PostMapping("/event/throw-error")
    public Result<Integer> throwError(
            @RequestParam String errorCode,
            @RequestParam(required = false) Long instanceId,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(eventSubscriptionService.throwError(tid, instanceId, errorCode, payload));
    }

    /**
     * P0-1: 查询实例的事件订阅列表
     *
     * @param instanceId 实例 ID
     * @return 订阅列表（含 WAITING / COMPLETED / CANCELLED 状态）
     */
    @GetMapping("/instance/{instanceId}/event-subscriptions")
    public Result<List<com.njydsz.pmis.workflow.entity.FlowEventSubscriptionDO>> listEventSubscriptions(
            @PathVariable @Min(1) Long instanceId) {
        return Result.ok(eventSubscriptionService.listByInstance(instanceId));
    }

    // ============== GAP-V2: 通知通道配置 ==============

    /**
     * 查询所有通知通道配置
     *
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 通道配置列表
     */
    @GetMapping("/notify-channel/list")
    public Result<List<FlowNotifyChannelDO>> listNotifyChannels(
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(notifyChannelService.listChannels(tid));
    }

    /**
     * 新增或更新通知通道配置
     *
     * @param dto 通道配置（id 为空时新增，非空时更新）
     * @return 保存后的通道配置
     */
    @PostMapping("/notify-channel/save")
    @PrePermission(PermissionCodes.WORKFLOW_NOTIFY_CONFIG)
    public Result<FlowNotifyChannelDO> saveNotifyChannel(@RequestBody FlowNotifyChannelDO dto) {
        if (dto.getTenantId() == null) {
            dto.setTenantId(SecurityContext.getTenantIdOrDefault(1L));
        }
        return Result.ok(notifyChannelService.saveChannel(dto));
    }

    /**
     * 启用/停用通知通道
     *
     * @param id      通道配置 ID
     * @param enabled 是否启用
     * @return 统一响应结果
     */
    @PutMapping("/notify-channel/{id}/toggle")
    public Result<Void> toggleNotifyChannel(@PathVariable @Min(1) Long id,
                                             @RequestParam Boolean enabled) {
        notifyChannelService.toggleChannel(id, enabled);
        return Result.ok();
    }

    /**
     * 删除通知通道配置
     *
     * @param id 通道配置 ID
     * @return 统一响应结果
     */
    @OperationLog(module = "工作流", action = "删除通知通道", bizType = "FLOW_NOTIFY_CHANNEL")
    @DeleteMapping("/notify-channel/{id}")
    public Result<Void> deleteNotifyChannel(@PathVariable @Min(1) Long id) {
        notifyChannelService.deleteChannel(id);
        return Result.ok();
    }
}
