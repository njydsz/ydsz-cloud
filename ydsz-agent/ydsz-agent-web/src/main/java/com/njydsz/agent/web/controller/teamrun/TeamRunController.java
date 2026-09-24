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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.teamrun.TeamRun;
import com.njydsz.agent.domain.teamrun.TeamRunPattern;
import com.njydsz.agent.server.teamrun.TeamRunOrchestrationService;
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
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/agent/teamruns")
@RequiredArgsConstructor
@Tag(name = "Team Run 管理", description = "多 Agent 协作编排 / 查询 / 控制")
public class TeamRunController {

  /** Team Run 编排服务 */
  private final TeamRunOrchestrationService orchestrationService;

  /**
   * 创建 Team Run。
   *
   * <p>创建一个多 Agent 协作任务实例，指定协作模式和初始上下文。
   * 协作模式（{@link TeamRunPattern}）决定 Agent 间的任务分配策略：
   * <ul>
   *   <li>{@code PIPELINE} — 串行流水线：Agent A → Agent B → Agent C，前一个 Agent 的输出是后一个的输入</li>
   *   <li>{@code PARALLEL} — 并行执行：所有 Agent 同时执行，各自独立输入，最终聚合输出</li>
   *   <li>{@code HIERARCHY} — 层级汇报：Manager Agent 分配任务 → Worker Agent 执行 → Manager 汇总</li>
   *   <li>{@code NETWORK} — 网络协作：Agent 间可互相通信，动态决定消息路由</li>
   * </ul>
   *
   * <p>Token 预估：创建一个 Team Run 本身不消耗 LLM Token；
   * 实际 Token 消耗在启动后由成员 Agent 数量和调用轮次决定，
   * 粗略估算 {@code ≈ sum(单个 Agent 预估 Token * Agent 数量 * 平均轮次)}。
   *
   * @param tenantId 租户 ID（从请求头自动获取）
   * @param request 创建请求体（必填：title / pattern；可选：description / initiatedBy / context）
   * @return 统一响应结果，data 为创建的 {@link TeamRun}（含 teamRunId / status / createdAt 等）
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
  public YdszResponse<TeamRun> createTeamRun(@Valid @RequestBody CreateTeamRunRequest request) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
   * <p>将一个 Agent（通过 {@code agentCode} 标识）注册为 Team Run 的成员，
   * 并指定其角色（{@code role}）和执行顺序（{@code executionOrder}）。
   *
   * <p>Token 配额影响：每个成员 Agent 在启动后都会独立消耗 LLM Token，
   * 新增成员会线性增加总 Token 消耗。租户需确保配额足以支撑所有成员的总调用需求。
   *
   * @param tenantId 租户 ID（从请求头自动获取）
   * @param teamRunId Team Run ID（路径参数）
   * @param request 添加成员请求体（必填：agentCode / role / executionOrder；可选：agentName / inputContext）
   * @return 统一响应结果，data 为更新后的 {@link TeamRun}（含最新成员列表）
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
      @PathVariable @NotBlank String teamRunId,
      @Valid @RequestBody AddMemberRequest request) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
   * <p>触发多 Agent 协作任务的执行。根据创建时指定的协作模式（{@link TeamRunPattern}），
   * 编排引擎将按拓扑顺序或并行调度成员 Agent 开始执行。
   *
   * <p>Token 消耗：所有成员 Agent 的实际 LLM 调用均从启动时开始计费，
   * Token 配额消耗 {@code ≈ Σ(每个 Agent 的 prompt+completion tokens)}。
   * 建议在启动前检查租户 Token 配额是否足以支撑预估消耗（通过可观测性面板查询单 Agent 历史用量推算）。
   *
   * <p>启动时机会话状态从 READY 切换为 RUNNING；已启动的任务不可重复启动。
   *
   * @param tenantId 租户 ID（从请求头自动获取）
   * @param teamRunId Team Run ID（路径参数）
   * @return 统一响应结果，data 为启动后的 {@link TeamRun}（status 变为 RUNNING）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_UPDATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'startTeamRun: ' + #teamRunId")
  @PostMapping("/{teamRunId}/start")
  @Operation(summary = "启动 Team Run", description = "启动多 Agent 协作执行")
  public YdszResponse<TeamRun> startTeamRun(@PathVariable @NotBlank String teamRunId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    log.info("[TeamRun-API] 启动 Team Run: tenantId={}, teamRunId={}", tenantId, teamRunId);
    TeamRun teamRun = orchestrationService.startTeamRun(teamRunId, tenantId);
    return YdszResponse.success(teamRun);
  }

  /**
   * 取消 Team Run。
   *
   * <p>中止正在执行的多 Agent 协作任务。取消后所有正在运行的 Agent 执行将被终止，
   * 已完成的 Agent 结果保留为最终输出的一部分。状态从 RUNNING 切换为 CANCELLED。
   *
   * <p>Token 说明：取消后已消耗的 Token 不予退还（LLM 调用已完成），
   * 但可以防止后续未启动的 Agent 继续消耗 Token。建议发现异常时尽早取消以减少损失。
   *
   * @param tenantId 租户 ID（从请求头自动获取）
   * @param teamRunId Team Run ID（路径参数）
   * @return 统一响应结果，data 为取消后的 {@link TeamRun}（status 变为 CANCELLED）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_TEAMRUN_UPDATE)
  @Audit(
      module = "Team Run 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'cancelTeamRun: ' + #teamRunId")
  @PostMapping("/{teamRunId}/cancel")
  @Operation(summary = "取消 Team Run", description = "取消正在执行的多 Agent 协作")
  public YdszResponse<TeamRun> cancelTeamRun(@PathVariable @NotBlank String teamRunId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
  public YdszResponse<TeamRun> getTeamRun(@PathVariable @NotBlank String teamRunId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
  public YdszResponse<List<TeamRun>> listActiveTeamRuns() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
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
