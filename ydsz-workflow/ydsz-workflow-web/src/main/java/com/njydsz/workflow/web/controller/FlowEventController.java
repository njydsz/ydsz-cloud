package com.njydsz.workflow.web.controller.integration;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.entity.FlowEventSubscription;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 事件 Controller
 *
 * <p>P0-1: BPMN 事件触发（消息关联 / 错误抛出）。
 * 通知通道配置已移至独立的消息通知引擎 ydsz-message。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-event", description = "工作流事件接口")
@RequestMapping("/api/v1/workflow/engine")
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
    public BaseResponse<Map<String, Object>> info() {
        return BaseResponse.success(Map.of(
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
    @Idempotent(key = "ydsz:workflow:FlowEventController:correlateMessage:lock", ttlSeconds = 5)
    @PostMapping("/event/correlateMessage")
    @Audit(module = "流程事件", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'correlateMessage'")
    public BaseResponse<Integer> correlateMessage(
            @RequestParam String messageName,
            @RequestParam(required = false) String correlationKey,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(eventSubscriptionService.correlateMessage(tid, messageName, correlationKey, payload));
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
    @Idempotent(key = "ydsz:workflow:FlowEventController:throwError:lock", ttlSeconds = 5)
    @PostMapping("/event/throwError")
    @Audit(module = "流程事件", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'throwError'")
    public BaseResponse<Integer> throwError(
            @RequestParam String errorCode,
            @RequestParam(required = false) String instanceId,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(eventSubscriptionService.throwError(tid, instanceId, errorCode, payload));
    }

    /**
     * P0-1: 查询实例的事件订阅列表
     *
     * @param instanceId 实例 ID
     * @return 订阅列表（含 WAITING / COMPLETED / CANCELLED 状态）
     */
    @GetMapping("/instance/{instanceId}/eventSubscriptions")
    public BaseResponse<List<FlowEventSubscriptionVO>> listEventSubscriptions(
            @PathVariable String instanceId) {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowEventSubscriptionListToVO(eventSubscriptionService.listByInstance(instanceId)));
    }
}
