package com.njydsz.agent.web.controller.trigger;

import java.util.List;
import java.util.Map;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerType;
import com.njydsz.agent.server.trigger.TriggerManagementService;

import lombok.extern.slf4j.Slf4j;

/**
 * 触发器管理控制器。
 *
 * <p>提供触发器的 REST API，包括 CRUD 操作和启用/禁用控制。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class TriggerController {

    private final TriggerManagementService triggerManagementService;

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    public TriggerController(TriggerManagementService triggerManagementService) {
        this.triggerManagementService = triggerManagementService;
    }

    /**
     * 创建触发器。
     *
     * @param tenantId    租户 ID（从请求头获取）
     * @param request     创建请求
     * @return 创建的触发器
     */
    public AgentTrigger createTrigger(String tenantId, CreateTriggerRequest request) {
        validateTenantId(tenantId);
        return triggerManagementService.createTrigger(
                tenantId,
                request.name(),
                request.description(),
                request.triggerType(),
                request.targetAgentCode(),
                request.targetAgentType(),
                request.cronExpression(),
                request.matchPattern(),
                request.config(),
                request.maxExecutionsPerHour()
        );
    }

    /**
     * 更新触发器。
     *
     * @param tenantId    租户 ID
     * @param triggerId   触发器 ID
     * @param request     更新请求
     * @return 更新后的触发器
     */
    public AgentTrigger updateTrigger(String tenantId, String triggerId, UpdateTriggerRequest request) {
        validateTenantId(tenantId);
        return triggerManagementService.updateTrigger(
                triggerId,
                tenantId,
                request.name(),
                request.description(),
                request.cronExpression(),
                request.matchPattern(),
                request.config(),
                request.maxExecutionsPerHour()
        );
    }

    /**
     * 启用触发器。
     *
     * @param tenantId  租户 ID
     * @param triggerId 触发器 ID
     */
    public void enableTrigger(String tenantId, String triggerId) {
        validateTenantId(tenantId);
        triggerManagementService.enableTrigger(triggerId, tenantId);
    }

    /**
     * 禁用触发器。
     *
     * @param tenantId  租户 ID
     * @param triggerId 触发器 ID
     */
    public void disableTrigger(String tenantId, String triggerId) {
        validateTenantId(tenantId);
        triggerManagementService.disableTrigger(triggerId, tenantId);
    }

    /**
     * 删除触发器。
     *
     * @param tenantId  租户 ID
     * @param triggerId 触发器 ID
     */
    public void deleteTrigger(String tenantId, String triggerId) {
        validateTenantId(tenantId);
        triggerManagementService.deleteTrigger(triggerId, tenantId);
    }

    /**
     * 获取触发器详情。
     *
     * @param tenantId  租户 ID
     * @param triggerId 触发器 ID
     * @return 触发器详情
     */
    public AgentTrigger getTrigger(String tenantId, String triggerId) {
        validateTenantId(tenantId);
        return triggerManagementService.getTrigger(triggerId, tenantId);
    }

    /**
     * 列出租户下所有启用触发器。
     *
     * @param tenantId 租户 ID
     * @return 触发器列表
     */
    public List<AgentTrigger> listTriggers(String tenantId) {
        validateTenantId(tenantId);
        return triggerManagementService.listEnabledTriggers(tenantId);
    }

    /**
     * 校验租户 ID。
     *
     * @param tenantId 租户 ID
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
    }

    /**
     * 创建触发器请求。
     */
    public record CreateTriggerRequest(
            String name,
            String description,
            TriggerType triggerType,
            String targetAgentCode,
            String targetAgentType,
            String cronExpression,
            String matchPattern,
            Map<String, Object> config,
            Integer maxExecutionsPerHour) {
    }

    /**
     * 更新触发器请求。
     */
    public record UpdateTriggerRequest(
            String name,
            String description,
            String cronExpression,
            String matchPattern,
            Map<String, Object> config,
            Integer maxExecutionsPerHour) {
    }
}
