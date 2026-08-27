package com.njydsz.agent.server.teamrun;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.njydsz.agent.domain.teamrun.TeamRun;
import com.njydsz.agent.domain.teamrun.TeamRunMember;
import com.njydsz.agent.domain.teamrun.TeamRunMemberStatus;
import com.njydsz.agent.domain.teamrun.TeamRunPattern;
import com.njydsz.agent.domain.teamrun.TeamRunRepository;
import com.njydsz.agent.domain.teamrun.TeamRunStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Team Run 编排服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建和管理多 Agent 协作执行</li>
 *   <li>根据协调模式调度 Agent 执行顺序</li>
 *   <li>收集和汇总各 Agent 执行结果</li>
 *   <li>处理执行失败和异常情况</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 Team Runs 设计，支持顺序、并行、层级、协商四种协作模式。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class TeamRunOrchestrationService {

    private final TeamRunRepository teamRunRepository;
    private final AgentExecutionService agentExecutionService;

    /** 用于并行执行的线程池 */
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /** 单租户最大 Team Run 数量 */
    private static final int MAX_TEAM_RUNS_PER_TENANT = 20;

    /** 单次 Team Run 最大成员数 */
    private static final int MAX_MEMBERS_PER_TEAM_RUN = 10;

    public TeamRunOrchestrationService(TeamRunRepository teamRunRepository,
                                         AgentExecutionService agentExecutionService) {
        this.teamRunRepository = Objects.requireNonNull(teamRunRepository, "teamRunRepository 不能为 null");
        this.agentExecutionService = Objects.requireNonNull(agentExecutionService, "agentExecutionService 不能为 null");
    }

    /**
     * 创建 Team Run。
     *
     * @param tenantId    租户 ID
     * @param title       标题
     * @param description 描述
     * @param pattern     协作模式
     * @param initiatedBy 发起人
     * @param context     上下文信息
     * @return 创建的 Team Run
     */
    public TeamRun createTeamRun(String tenantId,
                                 String title,
                                 String description,
                                 TeamRunPattern pattern,
                                 String initiatedBy,
                                 Map<String, Object> context) {
        Objects.requireNonNull(tenantId, "tenantId 不能为 null");
        Objects.requireNonNull(title, "title 不能为 null");
        Objects.requireNonNull(pattern, "pattern 不能为 null");

        // 校验租户 Team Run 数量限制
        long currentCount = teamRunRepository.countByTenant(tenantId);
        if (currentCount >= MAX_TEAM_RUNS_PER_TENANT) {
            throw new TeamRunException("租户 Team Run 数量已达上限: " + MAX_TEAM_RUNS_PER_TENANT);
        }

        LocalDateTime now = LocalDateTime.now();
        TeamRun teamRun = TeamRun.builder()
                .teamRunId(generateTeamRunId())
                .tenantId(tenantId)
                .title(title)
                .description(description)
                .pattern(pattern)
                .members(new ArrayList<>())
                .status(TeamRunStatus.CREATED)
                .initiatedBy(initiatedBy)
                .createdAt(now)
                .context(context)
                .build();

        TeamRun saved = teamRunRepository.save(teamRun);
        log.info("[TeamRun] 创建成功: teamRunId={}, tenantId={}, pattern={}",
                saved.getTeamRunId(), tenantId, pattern);

        return saved;
    }

    /**
     * 添加成员到 Team Run。
     *
     * @param teamRunId  Team Run ID
     * @param tenantId   租户 ID
     * @param agentCode  Agent 代码
     * @param agentName  Agent 名称
     * @param role       角色
     * @param order      执行顺序
     * @param inputContext 输入上下文
     * @return 更新后的 Team Run
     */
    public TeamRun addMember(String teamRunId,
                              String tenantId,
                              String agentCode,
                              String agentName,
                              String role,
                              int order,
                              String inputContext) {
        TeamRun teamRun = getTeamRunOrThrow(teamRunId, tenantId);

        if (teamRun.getMembers().size() >= MAX_MEMBERS_PER_TEAM_RUN) {
            throw new TeamRunException("Team Run 成员数量已达上限: " + MAX_MEMBERS_PER_TEAM_RUN);
        }

        if (teamRun.getStatus() != TeamRunStatus.CREATED) {
            throw new TeamRunException("Team Run 已启动，无法添加成员");
        }

        TeamRunMember member = TeamRunMember.builder()
                .memberId(generateMemberId())
                .agentCode(agentCode)
                .agentName(agentName)
                .role(role)
                .executionOrder(order)
                .inputContext(inputContext)
                .status(TeamRunMemberStatus.PENDING)
                .build();

        TeamRun updated = teamRun.withMember(member);
        teamRunRepository.save(updated);
        log.info("[TeamRun] 添加成员成功: teamRunId={}, memberId={}, agentCode={}",
                teamRunId, member.getMemberId(), agentCode);

        return updated;
    }

    /**
     * 启动 Team Run 执行。
     *
     * @param teamRunId Team Run ID
     * @param tenantId  租户 ID
     * @return 启动后的 Team Run
     */
    public TeamRun startTeamRun(String teamRunId, String tenantId) {
        TeamRun teamRun = getTeamRunOrThrow(teamRunId, tenantId);

        if (teamRun.getStatus() != TeamRunStatus.CREATED) {
            throw new TeamRunException("Team Run 状态不正确，无法启动: " + teamRun.getStatus());
        }

        if (teamRun.getMembers().isEmpty()) {
            throw new TeamRunException("Team Run 没有成员，无法启动");
        }

        TeamRun running = teamRun.withStatus(TeamRunStatus.RUNNING);
        teamRunRepository.save(running);
        log.info("[TeamRun] 启动执行: teamRunId={}, pattern={}, members={}",
                teamRunId, teamRun.getPattern(), teamRun.getMembers().size());

        // 根据协作模式执行
        switch (teamRun.getPattern()) {
            case SEQUENTIAL:
                executeSequential(running);
                break;
            case PARALLEL:
                executeParallel(running);
                break;
            case HIERARCHICAL:
                executeHierarchical(running);
                break;
            case NEGOTIATION:
                executeNegotiation(running);
                break;
            default:
                throw new TeamRunException("不支持的协作模式: " + teamRun.getPattern());
        }

        return running;
    }

    /**
     * 顺序执行模式。
     */
    private void executeSequential(TeamRun teamRun) {
        log.info("[TeamRun] 顺序执行模式: teamRunId={}", teamRun.getTeamRunId());

        CompletableFuture.runAsync(() -> {
            TeamRun current = teamRun;
            try {
                TeamRunMember nextMember;
                while ((nextMember = current.getNextPendingMember()) != null) {
                    // 执行单个成员
                    current = executeMember(current, nextMember);
                }
                // 所有成员执行完成
                finalizeTeamRun(current);
            } catch (Exception e) {
                log.error("[TeamRun] 顺序执行异常: teamRunId={}, error={}",
                        teamRun.getTeamRunId(), e.getMessage(), e);
                finalizeTeamRunWithError(current, e.getMessage());
            }
        }, executorService);
    }

    /**
     * 并行执行模式。
     */
    private void executeParallel(TeamRun teamRun) {
        log.info("[TeamRun] 并行执行模式: teamRunId={}", teamRun.getTeamRunId());

        List<CompletableFuture<TeamRunMember>> futures = teamRun.getMembers().stream()
                .map(member -> CompletableFuture.supplyAsync(
                        () -> executeMemberSync(teamRun, member), executorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    // 所有并行任务完成，汇总结果
                    TeamRun current = teamRun;
                    for (CompletableFuture<TeamRunMember> future : futures) {
                        try {
                            TeamRunMember result = future.get();
                            current = current.withUpdatedMember(result.getMemberId(), result);
                        } catch (Exception e) {
                            log.error("[TeamRun] 并行执行获取结果异常: {}", e.getMessage(), e);
                        }
                    }
                    teamRunRepository.save(current);
                    finalizeTeamRun(current);
                })
                .exceptionally(e -> {
                    log.error("[TeamRun] 并行执行异常: teamRunId={}, error={}",
                            teamRun.getTeamRunId(), e.getMessage(), e);
                    finalizeTeamRunWithError(teamRun, e.getMessage());
                    return null;
                });
    }

    /**
     * 层级执行模式（Leader-Worker）。
     */
    private void executeHierarchical(TeamRun teamRun) {
        log.info("[TeamRun] 层级执行模式: teamRunId={}", teamRun.getTeamRunId());

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 先执行 Leader（order 最小的成员）
                TeamRunMember leader = teamRun.getMembers().stream()
                        .min((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
                        .orElseThrow(() -> new TeamRunException("未找到 Leader"));

                TeamRun afterLeader = executeMember(teamRun, leader);

                // 2. 并行执行所有 Worker
                List<TeamRunMember> workers = afterLeader.getMembers().stream()
                        .filter(m -> !m.getMemberId().equals(leader.getMemberId()))
                        .filter(m -> m.getStatus() == TeamRunMemberStatus.PENDING)
                        .toList();

                List<CompletableFuture<TeamRunMember>> workerFutures = workers.stream()
                        .map(worker -> CompletableFuture.supplyAsync(
                                () -> executeMemberSync(afterLeader, worker), executorService))
                        .toList();

                // 3. 等待所有 Worker 完成
                CompletableFuture.allOf(workerFutures.toArray(new CompletableFuture[0])).join();

                // 4. 汇总结果
                TeamRun current = afterLeader;
                for (CompletableFuture<TeamRunMember> future : workerFutures) {
                    TeamRunMember result = future.get();
                    current = current.withUpdatedMember(result.getMemberId(), result);
                }
                teamRunRepository.save(current);
                finalizeTeamRun(current);

            } catch (Exception e) {
                log.error("[TeamRun] 层级执行异常: teamRunId={}, error={}",
                        teamRun.getTeamRunId(), e.getMessage(), e);
                finalizeTeamRunWithError(teamRun, e.getMessage());
            }
        }, executorService);
    }

    /**
     * 协商执行模式。
     */
    private void executeNegotiation(TeamRun teamRun) {
        log.info("[TeamRun] 协商执行模式: teamRunId={}", teamRun.getTeamRunId());

        // 协商模式：所有 Agent 并行执行，然后汇总结果进行"投票"
        executeParallel(teamRun);
    }

    /**
     * 执行单个成员（异步）。
     */
    private TeamRun executeMember(TeamRun teamRun, TeamRunMember member) {
        log.info("[TeamRun] 执行成员: teamRunId={}, memberId={}, agentCode={}",
                teamRun.getTeamRunId(), member.getMemberId(), member.getAgentCode());

        // 更新状态为 RUNNING
        TeamRunMember runningMember = member.withStatus(TeamRunMemberStatus.RUNNING);
        TeamRun current = teamRun.withUpdatedMember(member.getMemberId(), runningMember);
        teamRunRepository.save(current);

        try {
            // 调用 Agent 执行服务
            String result = agentExecutionService.execute(
                    member.getAgentCode(),
                    member.getInputContext(),
                    teamRun.getContext()
            );

            // 更新执行结果
            TeamRunMember completedMember = member.withResult(result, generateExecutionId());
            current = current.withUpdatedMember(member.getMemberId(), completedMember);
            teamRunRepository.save(current);

            log.info("[TeamRun] 成员执行完成: teamRunId={}, memberId={}",
                    teamRun.getTeamRunId(), member.getMemberId());

            return current;

        } catch (Exception e) {
            log.error("[TeamRun] 成员执行失败: teamRunId={}, memberId={}, error={}",
                    teamRun.getTeamRunId(), member.getMemberId(), e.getMessage(), e);

            TeamRunMember failedMember = member.withError(e.getMessage());
            current = current.withUpdatedMember(member.getMemberId(), failedMember);
            teamRunRepository.save(current);

            throw new TeamRunException("成员执行失败: " + member.getMemberId(), e);
        }
    }

    /**
     * 执行单个成员（同步，用于并行模式）。
     */
    private TeamRunMember executeMemberSync(TeamRun teamRun, TeamRunMember member) {
        try {
            String result = agentExecutionService.execute(
                    member.getAgentCode(),
                    member.getInputContext(),
                    teamRun.getContext()
            );
            return member.withResult(result, generateExecutionId());
        } catch (Exception e) {
            return member.withError(e.getMessage());
        }
    }

    /**
     * 完成 Team Run。
     */
    private void finalizeTeamRun(TeamRun teamRun) {
        // 汇总所有成员结果
        StringBuilder summary = new StringBuilder();
        summary.append("## Team Run 执行结果\n\n");
        summary.append("**标题**: ").append(teamRun.getTitle()).append("\n");
        summary.append("**模式**: ").append(teamRun.getPattern().getDescription()).append("\n\n");

        for (TeamRunMember member : teamRun.getMembers()) {
            summary.append("### ").append(member.getAgentName()).append(" (").append(member.getRole()).append(")\n");
            summary.append("- 状态: ").append(member.getStatus().getDescription()).append("\n");
            if (member.getOutputResult() != null) {
                summary.append("- 输出: ").append(member.getOutputResult()).append("\n");
            }
            if (member.getErrorMessage() != null) {
                summary.append("- 错误: ").append(member.getErrorMessage()).append("\n");
            }
            summary.append("\n");
        }

        TeamRun completed = teamRun.withFinalResult(summary.toString());
        teamRunRepository.save(completed);
        log.info("[TeamRun] 执行完成: teamRunId={}", teamRun.getTeamRunId());
    }

    /**
     * 异常完成 Team Run。
     */
    private void finalizeTeamRunWithError(TeamRun teamRun, String errorMessage) {
        TeamRun failed = teamRun.withStatus(TeamRunStatus.FAILED);
        teamRunRepository.save(failed);
        log.error("[TeamRun] 执行失败: teamRunId={}, error={}", teamRun.getTeamRunId(), errorMessage);
    }

    /**
     * 取消 Team Run。
     *
     * @param teamRunId Team Run ID
     * @param tenantId  租户 ID
     * @return 取消后的 Team Run
     */
    public TeamRun cancelTeamRun(String teamRunId, String tenantId) {
        TeamRun teamRun = getTeamRunOrThrow(teamRunId, tenantId);

        if (teamRun.getStatus().isTerminal()) {
            throw new TeamRunException("Team Run 已处于终态，无法取消");
        }

        TeamRun cancelled = teamRun.withStatus(TeamRunStatus.CANCELLED);
        teamRunRepository.save(cancelled);
        log.info("[TeamRun] 已取消: teamRunId={}", teamRunId);

        return cancelled;
    }

    /**
     * 获取 Team Run 详情。
     */
    public TeamRun getTeamRun(String teamRunId, String tenantId) {
        return getTeamRunOrThrow(teamRunId, tenantId);
    }

    /**
     * 列出租户下活跃的 Team Run。
     */
    public List<TeamRun> listActiveTeamRuns(String tenantId) {
        return teamRunRepository.findActiveByTenant(tenantId);
    }

    /**
     * 获取 Team Run 或抛出异常。
     */
    private TeamRun getTeamRunOrThrow(String teamRunId, String tenantId) {
        TeamRun teamRun = teamRunRepository.findById(teamRunId)
                .orElseThrow(() -> new TeamRunException("Team Run 不存在: " + teamRunId));

        if (!teamRun.getTenantId().equals(tenantId)) {
            throw new TeamRunException("无权访问此 Team Run");
        }

        return teamRun;
    }

    /**
     * 生成 Team Run ID。
     */
    private String generateTeamRunId() {
        return "tr-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成成员 ID。
     */
    private String generateMemberId() {
        return "mbr-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成执行 ID。
     */
    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Team Run 异常。
     */
    public static class TeamRunException extends RuntimeException {
        public TeamRunException(String message) {
            super(message);
        }

        public TeamRunException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Agent 执行服务接口。
     *
     * <p>由 server 层实现，将执行委托给具体的 Agent 执行服务。</p>
     */
    public interface AgentExecutionService {
        /**
         * 执行 Agent。
         *
         * @param agentCode   Agent 代码
         * @param inputContext 输入上下文
         * @param context      Team Run 上下文
         * @return 执行结果
         */
        String execute(String agentCode, String inputContext, Map<String, Object> context);
    }
}
