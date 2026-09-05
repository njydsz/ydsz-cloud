package com.njydsz.agent.web.controller.trigger;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerType;
import com.njydsz.agent.server.trigger.TriggerManagementService.TriggerManagementException;
import com.njydsz.agent.server.trigger.TriggerManagementService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * 触发器管理控制器。
 *
 * <p>提供触发器的 REST API，包括 CRUD 操作和启用/禁用控制。</p>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 / 第三方系统
 *     → ydsz-gateway
 *       → ydsz-agent-web（本 Controller）
 *         → TriggerManagementService（应用服务）
 *           → TriggerRepository（仓储）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/triggers")
@RequiredArgsConstructor
@Tag(name = "触发器管理", description = "触发器 CRUD / 启用禁用 / 查询")
public class TriggerController {

  /** 触发器管理服务 */
  private final TriggerManagementService triggerManagementService;

  /**
   * 创建触发器。
   *
   * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
   * @param request 创建请求体
   * @return 创建的触发器
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_CREATE)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createTrigger'")
  @Idempotent(key = "'agent:trigger:create:' + #request.name()", ttlSeconds = 5)
  @RateLimit(resource = "agent.trigger.create", threshold = 20)
  @PostMapping
  @Operation(summary = "创建触发器", description = "为指定 Agent 创建定时或事件触发器")
  public YdszResponse<AgentTrigger> createTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @Valid @RequestBody CreateTriggerRequest request) {
    log.info("[Trigger-API] 创建触发器: tenantId={}, name={}, type={}",
        tenantId, request.name(), request.triggerType());
    AgentTrigger trigger = triggerManagementService.createTrigger(
        tenantId,
        request.name(),
        request.description(),
        request.triggerType(),
        request.targetAgentCode(),
        request.targetAgentType(),
        request.cronExpression(),
        request.matchPattern(),
        request.config(),
        request.maxExecutionsPerHour());
    return YdszResponse.success(trigger);
  }

  /**
   * 更新触发器。
   *
   * @param tenantId 租户 ID
   * @param triggerId 触发器 ID
   * @param request 更新请求体
   * @return 更新后的触发器
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_UPDATE)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateTrigger: ' + #triggerId")
  @Idempotent(key = "'agent:trigger:update:' + #triggerId", ttlSeconds = 5)
  @RateLimit(resource = "agent.trigger.update", threshold = 20)
  @PutMapping("/{triggerId}")
  @Operation(summary = "更新触发器", description = "更新触发器的配置信息")
  public YdszResponse<AgentTrigger> updateTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String triggerId,
      @Valid @RequestBody UpdateTriggerRequest request) {
    log.info("[Trigger-API] 更新触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    AgentTrigger trigger = triggerManagementService.updateTrigger(
        triggerId,
        tenantId,
        request.name(),
        request.description(),
        request.cronExpression(),
        request.matchPattern(),
        request.config(),
        request.maxExecutionsPerHour());
    return YdszResponse.success(trigger);
  }

  /**
   * 启用触发器。
   *
   * @param tenantId 租户 ID
   * @param triggerId 触发器 ID
   * @return 操作结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_UPDATE)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'enableTrigger: ' + #triggerId")
  @PostMapping("/{triggerId}/enable")
  @Operation(summary = "启用触发器")
  public YdszResponse<Void> enableTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String triggerId) {
    log.info("[Trigger-API] 启用触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.enableTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 禁用触发器。
   *
   * @param tenantId 租户 ID
   * @param triggerId 触发器 ID
   * @return 操作结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_UPDATE)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'disableTrigger: ' + #triggerId")
  @PostMapping("/{triggerId}/disable")
  @Operation(summary = "禁用触发器")
  public YdszResponse<Void> disableTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String triggerId) {
    log.info("[Trigger-API] 禁用触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.disableTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 删除触发器。
   *
   * @param tenantId 租户 ID
   * @param triggerId 触发器 ID
   * @return 操作结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_DELETE)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteTrigger: ' + #triggerId")
  @DeleteMapping("/{triggerId}")
  @Operation(summary = "删除触发器")
  public YdszResponse<Void> deleteTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String triggerId) {
    log.info("[Trigger-API] 删除触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.deleteTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 获取触发器详情。
   *
   * @param tenantId 租户 ID
   * @param triggerId 触发器 ID
   * @return 触发器详情
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_VIEW)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'getTrigger: ' + #triggerId")
  @GetMapping("/{triggerId}")
  @Operation(summary = "获取触发器详情")
  public YdszResponse<AgentTrigger> getTrigger(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String triggerId) {
    AgentTrigger trigger = triggerManagementService.getTrigger(triggerId, tenantId);
    return YdszResponse.success(trigger);
  }

  /**
   * 列出租户下所有启用触发器。
   *
   * @param tenantId 租户 ID
   * @return 触发器列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_VIEW)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'listTriggers'")
  @GetMapping
  @Operation(summary = "列出租户下所有启用触发器")
  public YdszResponse<List<AgentTrigger>> listTriggers(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId) {
    List<AgentTrigger> triggers = triggerManagementService.listEnabledTriggers(tenantId);
    return YdszResponse.success(triggers);
  }

  /**
   * 创建触发器请求。
   *
   * @param name 触发器名称
   * @param description 触发器描述
   * @param triggerType 触发类型
   * @param targetAgentCode 目标 Agent 代码
   * @param targetAgentType 目标 Agent 类型
   * @param cronExpression cron 表达式（CRON 类型必填）
   * @param matchPattern 匹配模式（正则表达式）
   * @param config 额外配置
   * @param maxExecutionsPerHour 每小时最大执行次数
   */
  public record CreateTriggerRequest(
      @NotBlank String name,
      String description,
      TriggerType triggerType,
      @NotBlank String targetAgentCode,
      String targetAgentType,
      String cronExpression,
      String matchPattern,
      Map<String, Object> config,
      Integer maxExecutionsPerHour) {
  }

  /**
   * 更新触发器请求。
   *
   * @param name 触发器名称
   * @param description 触发器描述
   * @param cronExpression cron 表达式
   * @param matchPattern 匹配模式
   * @param config 额外配置
   * @param maxExecutionsPerHour 每小时最大执行次数
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
