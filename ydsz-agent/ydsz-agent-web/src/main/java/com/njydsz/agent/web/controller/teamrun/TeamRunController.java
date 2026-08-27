package com.njydsz.agent.web.controller.teamrun;

import java.util.List;
import java.util.Map;

import com.njydsz.agent.domain.teamrun.TeamRun;
import com.njydsz.agent.domain.teamrun.TeamRunPattern;
import com.njydsz.agent.server.teamrun.TeamRunOrchestrationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Team Run 管理控制器。
 *
 * <p>提供 Team Run 的 REST API，包括创建、添加成员、启动、查询、取消等操作。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class TeamRunController {

    private final TeamRunOrchestrationService orchestrationService;

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    public TeamRunController(TeamRunOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * 创建 Team Run。
     *
     * @param tenantId  租户 ID
     * @param request   创建请求
     * @return 创建的 Team Run
     */
    public TeamRun createTeamRun(String tenantId, CreateTeamRunRequest request) {
        validateTenantId(tenantId);
        return orchestrationService.createTeamRun(
                tenantId,
                request.title(),
                request.description(),
                request.pattern(),
                request.initiatedBy(),
                request.context()
        );
    }

    /**
     * 添加成员到 Team Run。
     *
     * @param tenantId  租户 ID
     * @param teamRunId Team Run ID
     * @param request   添加成员请求
     * @return 更新后的 Team Run
     */
    public TeamRun addMember(String tenantId, String teamRunId, AddMemberRequest request) {
        validateTenantId(tenantId);
        return orchestrationService.addMember(
                teamRunId,
                tenantId,
                request.agentCode(),
                request.agentName(),
                request.role(),
                request.executionOrder(),
                request.inputContext()
        );
    }

    /**
     * 启动 Team Run。
     *
     * @param tenantId  租户 ID
     * @param teamRunId Team Run ID
     * @return 启动后的 Team Run
     */
    public TeamRun startTeamRun(String tenantId, String teamRunId) {
        validateTenantId(tenantId);
        return orchestrationService.startTeamRun(teamRunId, tenantId);
    }

    /**
     * 取消 Team Run。
     *
     * @param tenantId  租户 ID
     * @param teamRunId Team Run ID
     * @return 取消后的 Team Run
     */
    public TeamRun cancelTeamRun(String tenantId, String teamRunId) {
        validateTenantId(tenantId);
        return orchestrationService.cancelTeamRun(teamRunId, tenantId);
    }

    /**
     * 获取 Team Run 详情。
     *
     * @param tenantId  租户 ID
     * @param teamRunId Team Run ID
     * @return Team Run 详情
     */
    public TeamRun getTeamRun(String tenantId, String teamRunId) {
        validateTenantId(tenantId);
        return orchestrationService.getTeamRun(teamRunId, tenantId);
    }

    /**
     * 列出租户下活跃的 Team Run。
     *
     * @param tenantId 租户 ID
     * @return Team Run 列表
     */
    public List<TeamRun> listActiveTeamRuns(String tenantId) {
        validateTenantId(tenantId);
        return orchestrationService.listActiveTeamRuns(tenantId);
    }

    /**
     * 校验租户 ID。
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
    }

    /**
     * 创建 Team Run 请求。
     */
    public record CreateTeamRunRequest(
            String title,
            String description,
            TeamRunPattern pattern,
            String initiatedBy,
            Map<String, Object> context) {
    }

    /**
     * 添加成员请求。
     */
    public record AddMemberRequest(
            String agentCode,
            String agentName,
            String role,
            int executionOrder,
            String inputContext) {
    }
}
