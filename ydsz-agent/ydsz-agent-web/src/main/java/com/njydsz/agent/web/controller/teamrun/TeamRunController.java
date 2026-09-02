package com.njydsz.agent.web.controller.teamrun;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.teamrun.TeamRun;
import com.njydsz.agent.domain.teamrun.TeamRunPattern;
import com.njydsz.agent.server.teamrun.TeamRunOrchestrationService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * Team Run 管理控制器。
 *
 * <p>提供 Team Run 的 REST API，包括创建、添加成员、启动、查询、取消等操作。</p>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 / 第三方系统
 *     → ydsz-gateway
 *       → ydsz-agent-web（本 Controller）
 *         → TeamRunOrchestrationService（应用服务）
 *           → TeamRunRepository（仓储）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/teamruns")
@RequiredArgsConstructor
@Tag(name = "Team Run 管理", description = "多 Agent 协作编排 / 查询 / 控制")
public class TeamRunController {

  /** Team Run 编排服务 */
  private final TeamRunOrchestrationService orchestrationService;

  /**
   * 创建 Team Run。
   *
   * @param tenantId 租户 ID
   * @param request 创建请求体
   * @return 创建的 Team Run
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_CREATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createTeamRun'")
  @Idempotent(key = "'agent:teamrun:create:' + #request.title()", ttlSeconds = 5)
  @RateLimit(resource = "agent.teamrun.create", threshold = 10)
  @PostMapping
  @Operation(summary = "创建 Team Run", description = "创建多 Agent 协作任务")
  public YdszResponse<TeamRun> createTeamRun(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @Valid @RequestBody CreateTeamRunRequest request) {
    log.info("[TeamRun-API] 创建 Team Run: tenantId={}, title={}, pattern={}",
        tenantId, request.title(), request.pattern());
    TeamRun teamRun = orchestrationService.createTeamRun(
        tenantId,
        request.title(),
        request.description(),
        request.pattern(),
        request.initiatedBy(),
        request.context());
    return YdszResponse.success(teamRun);
  }

  /**
   * 添加成员到 Team Run。
   *
   * @param tenantId 租户 ID
   * @param teamRunId Team Run ID
   * @param request 添加成员请求体
   * @return 更新后的 Team Run
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_UPDATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'addMember: ' + #teamRunId")
  @PostMapping("/{teamRunId}/members")
  @Operation(summary = "添加成员", description = "向 Team Run 添加 Agent 成员")
  public YdszResponse<TeamRun> addMember(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String teamRunId,
      @Valid @RequestBody AddMemberRequest request) {
    log.info("[TeamRun-API] 添加成员: tenantId={}, teamRunId={}, agentCode={}",
        tenantId, teamRunId, request.agentCode());
    TeamRun teamRun = orchestrationService.addMember(
        teamRunId,
        tenantId,
        request.agentCode(),
        request.agentName(),
        request.role(),
        request.executionOrder(),
        request.inputContext());
    return YdszResponse.success(teamRun);
  }

  /**
   * 启动 Team Run。
   *
   * @param tenantId 租户 ID
   * @param teamRunId Team Run ID
   * @return 启动后的 Team Run
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_UPDATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'startTeamRun: ' + #teamRunId")
  @PostMapping("/{teamRunId}/start")
  @Operation(summary = "启动 Team Run", description = "启动多 Agent 协作执行")
  public YdszResponse<TeamRun> startTeamRun(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String teamRunId) {
    log.info("[TeamRun-API] 启动 Team Run: tenantId={}, teamRunId={}", tenantId, teamRunId);
    TeamRun teamRun = orchestrationService.startTeamRun(teamRunId, tenantId);
    return YdszResponse.success(teamRun);
  }

  /**
   * 取消 Team Run。
   *
   * @param tenantId 租户 ID
   * @param teamRunId Team Run ID
   * @return 取消后的 Team Run
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_UPDATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'cancelTeamRun: ' + #teamRunId")
  @PostMapping("/{teamRunId}/cancel")
  @Operation(summary = "取消 Team Run", description = "取消正在执行的多 Agent 协作")
  public YdszResponse<TeamRun> cancelTeamRun(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String teamRunId) {
    log.info("[TeamRun-API] 取消 Team Run: tenantId={}, teamRunId={}", tenantId, teamRunId);
    TeamRun teamRun = orchestrationService.cancelTeamRun(teamRunId, tenantId);
    return YdszResponse.success(teamRun);
  }

  /**
   * 获取 Team Run 详情。
   *
   * @param tenantId 租户 ID
   * @param teamRunId Team Run ID
   * @return Team Run 详情
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_VIEW)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'getTeamRun: ' + #teamRunId")
  @GetMapping("/{teamRunId}")
  @Operation(summary = "获取 Team Run 详情")
  public YdszResponse<TeamRun> getTeamRun(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
      @PathVariable @NotBlank String teamRunId) {
    TeamRun teamRun = orchestrationService.getTeamRun(teamRunId, tenantId);
    return YdszResponse.success(teamRun);
  }

  /**
   * 列出租户下活跃的 Team Run。
   *
   * @param tenantId 租户 ID
   * @return Team Run 列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_VIEW)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'listActiveTeamRuns'")
  @GetMapping
  @Operation(summary = "列出租户下活跃的 Team Run")
  public YdszResponse<List<TeamRun>> listActiveTeamRuns(
      @RequestHeader("X-Tenant-Id") @NotBlank String tenantId) {
    List<TeamRun> teamRuns = orchestrationService.listActiveTeamRuns(tenantId);
    return YdszResponse.success(teamRuns);
  }

  /**
   * 创建 Team Run 请求。
   *
   * @param title 标题
   * @param description 描述
   * @param pattern 协作模式
   * @param initiatedBy 发起人
   * @param context 上下文配置
   */
  public record CreateTeamRunRequest(
      @NotBlank String title,
      String description,
      TeamRunPattern pattern,
      String initiatedBy,
      Map<String, Object> context) {
  }

  /**
   * 添加成员请求。
   *
   * @param agentCode Agent 代码
   * @param agentName Agent 名称
   * @param role 角色
   * @param executionOrder 执行顺序
   * @param inputContext 输入上下文
   */
  public record AddMemberRequest(
      @NotBlank String agentCode,
      String agentName,
      String role,
      int executionOrder,
      String inputContext) {
  }
}
