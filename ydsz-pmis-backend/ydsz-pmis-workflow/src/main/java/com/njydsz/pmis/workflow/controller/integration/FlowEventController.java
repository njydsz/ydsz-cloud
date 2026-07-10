package com.njydsz.pmis.workflow.controller.integration;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.entity.integration.FlowEventSubscriptionDO;
import com.njydsz.pmis.workflow.service.integration.FlowEventSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 事件 Controller
 *
 * <p>P0-1: BPMN 事件触发（消息关联 / 错误抛出）。
 * 通知通道配置已移至独立的消息通知引擎 ydsz-pmis-message。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-event", description = "工作流事件接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowEventController {

    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** P0-1: BPMN 事件订阅服务（消息关联 / 错误抛出） */
    private final FlowEventSubscriptionService eventSubscriptionService;

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
    @Idempotent(key = "flow-event:correlate-message", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/event/correlate-message")
    public Result<Integer> correlateMessage(
            @RequestParam String messageName,
            @RequestParam(required = false) String correlationKey,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
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
    @Idempotent(key = "flow-event:throw-error", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/event/throw-error")
    public Result<Integer> throwError(
            @RequestParam String errorCode,
            @RequestParam(required = false) String instanceId,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(eventSubscriptionService.throwError(tid, instanceId, errorCode, payload));
    }

    /**
     * P0-1: 查询实例的事件订阅列表
     *
     * @param instanceId 实例 ID
     * @return 订阅列表（含 WAITING / COMPLETED / CANCELLED 状态）
     */
    @GetMapping("/instance/{instanceId}/event-subscriptions")
    public Result<List<FlowEventSubscriptionDO>> listEventSubscriptions(
            @PathVariable String instanceId) {
        return Result.ok(eventSubscriptionService.listByInstance(instanceId));
    }
}
