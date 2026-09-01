package com.njydsz.agent.server.trigger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerRepository;
import com.njydsz.agent.domain.trigger.TriggerType;

/**
 * 触发器管理服务。
 *
 * <p>提供触发器的 CRUD 操作，包括创建、更新、删除、启用/禁用等功能。
 * 作为触发器聚合的应用服务层，协调领域对象与仓储。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class TriggerManagementService {

    private final TriggerRepository triggerRepository;

    /** 单租户最大触发器数量 */
    private static final int MAX_TRIGGERS_PER_TENANT = 50;

    public TriggerManagementService(TriggerRepository triggerRepository) {
        this.triggerRepository = Objects.requireNonNull(triggerRepository, "triggerRepository 不能为 null");
    }

    /**
     * 创建触发器。
     *
     * @param tenantId          租户 ID
     * @param name              触发器名称
     * @param description       触发器描述
     * @param triggerType       触发类型
     * @param targetAgentCode   目标 Agent 代码
     * @param targetAgentType   目标 Agent 类型
     * @param cronExpression    cron 表达式（CRON 类型必填）
     * @param matchPattern      匹配模式（正则表达式）
     * @param config            额外配置
     * @param maxExecutionsPerHour 每小时最大执行次数
     * @return 创建的触发器
     */
    public AgentTrigger createTrigger(String tenantId,
                                       String name,
                                       String description,
                                       TriggerType triggerType,
                                       String targetAgentCode,
                                       String targetAgentType,
                                       String cronExpression,
                                       String matchPattern,
                                       Map<String, Object> config,
                                       Integer maxExecutionsPerHour) {
        Objects.requireNonNull(tenantId, "tenantId 不能为 null");
        Objects.requireNonNull(name, "name 不能为 null");
        Objects.requireNonNull(triggerType, "triggerType 不能为 null");
        Objects.requireNonNull(targetAgentCode, "targetAgentCode 不能为 null");

        // 校验租户触发器数量限制
        long currentCount = triggerRepository.countByTenant(tenantId);
        if (currentCount >= MAX_TRIGGERS_PER_TENANT) {
            throw new TriggerManagementException(
                    "租户触发器数量已达上限: " + MAX_TRIGGERS_PER_TENANT);
        }

        // 校验 CRON 类型必须提供 cron 表达式
        if (triggerType == TriggerType.CRON && (cronExpression == null || cronExpression.isBlank())) {
            throw new TriggerManagementException("CRON 类型触发器必须提供 cronExpression");
        }

        LocalDateTime now = LocalDateTime.now();
        AgentTrigger trigger = AgentTrigger.builder()
                .triggerId(generateTriggerId())
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .triggerType(triggerType)
                .targetAgentCode(targetAgentCode)
                .targetAgentType(targetAgentType)
                .cronExpression(cronExpression)
                .matchPattern(matchPattern)
                .config(config)
                .enabled(true)
                .maxExecutionsPerHour(maxExecutionsPerHour != null ? maxExecutionsPerHour : 60)
                .createdAt(now)
                .totalTriggerCount(0)
                .build();

        AgentTrigger saved = triggerRepository.save(trigger);
        log.info("[TriggerMgmt] 触发器创建成功: triggerId={}, tenantId={}, name={}",
                saved.getTriggerId(), tenantId, name);

        return saved;
    }

    /**
     * 更新触发器配置。
     *
     * @param triggerId       触发器 ID
     * @param tenantId        租户 ID（用于权限校验）
     * @param name            新名称
     * @param description     新描述
     * @param cronExpression  新 cron 表达式
     * @param matchPattern    新匹配模式
     * @param config          新配置
     * @param maxExecutionsPerHour 新限速值
     * @return 更新后的触发器
     */
    public AgentTrigger updateTrigger(String triggerId,
                                       String tenantId,
                                       String name,
                                       String description,
                                       String cronExpression,
                                       String matchPattern,
                                       Map<String, Object> config,
                                       Integer maxExecutionsPerHour) {
        AgentTrigger existing = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new TriggerManagementException("触发器不存在: " + triggerId));

        // 权限校验
        if (!existing.getTenantId().equals(tenantId)) {
            throw new TriggerManagementException("无权操作此触发器");
        }

        AgentTrigger updated = AgentTrigger.builder()
                .triggerId(existing.getTriggerId())
                .tenantId(existing.getTenantId())
                .name(name != null ? name : existing.getName())
                .description(description != null ? description : existing.getDescription())
                .triggerType(existing.getTriggerType())
                .targetAgentCode(existing.getTargetAgentCode())
                .targetAgentType(existing.getTargetAgentType())
                .cronExpression(cronExpression != null ? cronExpression : existing.getCronExpression())
                .matchPattern(matchPattern != null ? matchPattern : existing.getMatchPattern())
                .config(config != null ? config : existing.getConfig())
                .enabled(existing.isEnabled())
                .maxExecutionsPerHour(maxExecutionsPerHour != null ? maxExecutionsPerHour : existing.getMaxExecutionsPerHour())
                .createdAt(existing.getCreatedAt())
                .lastTriggeredAt(existing.getLastTriggeredAt())
                .totalTriggerCount(existing.getTotalTriggerCount())
                .build();

        triggerRepository.save(updated);
        log.info("[TriggerMgmt] 触发器更新成功: triggerId={}", triggerId);

        return updated;
    }

    /**
     * 启用触发器。
     *
     * @param triggerId 触发器 ID
     * @param tenantId  租户 ID
     */
    public void enableTrigger(String triggerId, String tenantId) {
        updateTriggerEnabledStatus(triggerId, tenantId, true);
    }

    /**
     * 禁用触发器。
     *
     * @param triggerId 触发器 ID
     * @param tenantId  租户 ID
     */
    public void disableTrigger(String triggerId, String tenantId) {
        updateTriggerEnabledStatus(triggerId, tenantId, false);
    }

    /**
     * 删除触发器。
     *
     * @param triggerId 触发器 ID
     * @param tenantId  租户 ID
     */
    public void deleteTrigger(String triggerId, String tenantId) {
        AgentTrigger existing = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new TriggerManagementException("触发器不存在: " + triggerId));

        if (!existing.getTenantId().equals(tenantId)) {
            throw new TriggerManagementException("无权操作此触发器");
        }

        triggerRepository.delete(triggerId);
        log.info("[TriggerMgmt] 触发器删除成功: triggerId={}", triggerId);
    }

    /**
     * 查询触发器详情。
     *
     * @param triggerId 触发器 ID
     * @param tenantId  租户 ID
     * @return 触发器详情
     */
    public AgentTrigger getTrigger(String triggerId, String tenantId) {
        AgentTrigger trigger = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new TriggerManagementException("触发器不存在: " + triggerId));

        if (!trigger.getTenantId().equals(tenantId)) {
            throw new TriggerManagementException("无权访问此触发器");
        }

        return trigger;
    }

    /**
     * 查询租户下所有启用触发器。
     *
     * @param tenantId 租户 ID
     * @return 触发器列表
     */
    public List<AgentTrigger> listEnabledTriggers(String tenantId) {
        return triggerRepository.findEnabledByTenant(tenantId);
    }

    /**
     * 更新触发器启用状态。
     */
    private void updateTriggerEnabledStatus(String triggerId, String tenantId, boolean enabled) {
        AgentTrigger existing = triggerRepository.findById(triggerId)
                .orElseThrow(() -> new TriggerManagementException("触发器不存在: " + triggerId));

        if (!existing.getTenantId().equals(tenantId)) {
            throw new TriggerManagementException("无权操作此触发器");
        }

        AgentTrigger updated = AgentTrigger.builder()
                .triggerId(existing.getTriggerId())
                .tenantId(existing.getTenantId())
                .name(existing.getName())
                .description(existing.getDescription())
                .triggerType(existing.getTriggerType())
                .targetAgentCode(existing.getTargetAgentCode())
                .targetAgentType(existing.getTargetAgentType())
                .cronExpression(existing.getCronExpression())
                .matchPattern(existing.getMatchPattern())
                .config(existing.getConfig())
                .enabled(enabled)
                .maxExecutionsPerHour(existing.getMaxExecutionsPerHour())
                .createdAt(existing.getCreatedAt())
                .lastTriggeredAt(existing.getLastTriggeredAt())
                .totalTriggerCount(existing.getTotalTriggerCount())
                .build();

        triggerRepository.save(updated);
        log.info("[TriggerMgmt] 触发器状态更新: triggerId={}, enabled={}", triggerId, enabled);
    }

    /**
     * 生成触发器 ID。
     *
     * @return 唯一触发器 ID
     */
    private String generateTriggerId() {
        return "trg-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 触发器管理异常。
     */
    public static class TriggerManagementException extends RuntimeException {
        public TriggerManagementException(String message) {
            super(message);
        }
    }
}
