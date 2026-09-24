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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerType;
import com.njydsz.agent.server.trigger.TriggerManagementService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
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
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/agent/triggers")
@RequiredArgsConstructor
@Tag(name = "触发器管理", description = "触发器 CRUD / 启用禁用 / 查询")
public class TriggerController {

  /** 触发器管理服务 */
  private final TriggerManagementService triggerManagementService;

  /**
   * 创建触发器。
   *
   * <p>为指定 Agent 创建定时或事件触发器，用于自动化周期执行。
   * 触发器类型（{@link TriggerType}）包括：
   * <ul>
   *   <li>{@code CRON} — 定时触发：按 cron 表达式周期性执行（必填 {@code cronExpression}），如 {@code "0 9 * * *"} 每天 9 点</li>
   *   <li>{@code EVENT} — 事件触发：当匹配模式匹配到事件内容时触发（必填 {@code matchPattern} 正则）</li>
   *   <li>{@code WEBHOOK} — Webhook 触发：接收外部 HTTP 回调后触发执行</li>
   * </ul>
   *
   * <p>Token 配额影响：每个触发器每次触发将执行一次完整 Agent 调用，消耗 LLM Token
   * 约等于该 Agent 单次对话平均 Token 消耗。建议通过 {@code maxExecutionsPerHour} 限制每小时最大触发次数，
   * 防止 Token 配额被异常高频耗尽。租户应结合预估 Token 消耗预留足够配额。
   *
   * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 自动获取，或使用系统默认租户）
   * @param request 创建请求体（必填：name / triggerType / targetAgentCode；CRON 类型必填 cronExpression；EVENT 类型必填 matchPattern；可选：config / maxExecutionsPerHour）
   * @return 统一响应结果，data 为创建的 {@link AgentTrigger}（含 triggerId / status / createdAt）
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
  public YdszResponse<AgentTrigger> createTrigger(@Valid @RequestBody CreateTriggerRequest request) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
      @PathVariable @NotBlank String triggerId,
      @Valid @RequestBody UpdateTriggerRequest request) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
   * <p>将指定触发器标记为 ENABLED 状态，启用后触发器将按照配置的模式（CRON/EVENT/WEBHOOK）监控并自动执行。
   * ENABLED 状态下每次触发都将创建一次新的 Agent 对话，消耗 LLM Token。
   *
   * @param tenantId 租户 ID（从请求头自动获取，数据隔离校验）
   * @param triggerId 触发器 ID（路径参数），不可为空或空白
   * @return 统一响应结果，data 为 null
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
      @PathVariable @NotBlank String triggerId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    log.info("[Trigger-API] 启用触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.enableTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 禁用触发器。
   *
   * <p>将指定触发器标记为 DISABLED 状态，禁用后触发器将不再监控事件或执行定时任务。
   * 这是防止 Token 配额异常消耗的主要运维手段：当发现某触发器产生过高 Token 消耗时，
   * 可先禁用触发器再排查问题。
   *
   * @param tenantId 租户 ID（从请求头自动获取，数据隔离校验）
   * @param triggerId 触发器 ID（路径参数），不可为空或空白
   * @return 统一响应结果，data 为 null
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
      @PathVariable @NotBlank String triggerId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    log.info("[Trigger-API] 禁用触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.disableTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 删除触发器。
   *
   * <p>永久删除指定触发器，删除后不可恢复。触发器的启用/禁用状态不影响删除操作。
   * 删除后正在排队等候的事件将被丢弃，不会触发执行。
   *
   * @param tenantId 租户 ID（从请求头自动获取，数据隔离校验）
   * @param triggerId 触发器 ID（路径参数），不可为空或空白
   * @return 统一响应结果，data 为 null
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
      @PathVariable @NotBlank String triggerId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    log.info("[Trigger-API] 删除触发器: tenantId={}, triggerId={}", tenantId, triggerId);
    triggerManagementService.deleteTrigger(triggerId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 获取触发器详情。
   *
   * <p>查询指定触发器的完整配置信息，包含触发类型、目标 Agent、cron 表达式、
   * 最大执行次数限制、当前状态和审计字段。不消耗 LLM Token。
   *
   * @param tenantId 租户 ID（从请求头自动获取，数据隔离校验）
   * @param triggerId 触发器 ID（路径参数），不可为空或空白
   * @return 统一响应结果，data 为 {@link AgentTrigger}（含 triggerId / name / triggerType / targetAgentCode / cronExpression / matchPattern / status / config / maxExecutionsPerHour 等字段）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_VIEW)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'getTrigger: ' + #triggerId")
  @GetMapping("/{triggerId}")
  @Operation(summary = "获取触发器详情")
  public YdszResponse<AgentTrigger> getTrigger(@PathVariable @NotBlank String triggerId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    AgentTrigger trigger = triggerManagementService.getTrigger(triggerId, tenantId);
    return YdszResponse.success(trigger);
  }

  /**
   * 列出租户下所有启用触发器。
   *
   * <p>仅返回状态为 ENABLED 的触发器列表，按创建时间倒序排列。
   * 用于前端"触发器管理"面板展示当前活跃触发器。不消耗 LLM Token。
   *
   * @param tenantId 租户 ID（从请求头自动获取，数据隔离）
   * @return 统一响应结果，data 为 {@link AgentTrigger} 列表；无活跃触发器时返回空列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TRIGGER_VIEW)
  @Audit(
      module = "触发器管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'listTriggers'")
  @GetMapping
  @Operation(summary = "列出租户下所有启用触发器")
  public YdszResponse<List<AgentTrigger>> listTriggers() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
