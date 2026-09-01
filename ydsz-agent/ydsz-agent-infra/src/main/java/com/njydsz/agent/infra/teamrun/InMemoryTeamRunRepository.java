package com.njydsz.agent.infra.teamrun;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.njydsz.agent.domain.teamrun.TeamRun;
import com.njydsz.agent.domain.teamrun.TeamRunRepository;

import org.springframework.stereotype.Component;

/**
 * 基于内存的 Team Run 仓储实现。
 *
 * <p>使用 ConcurrentHashMap 存储 Team Run 聚合根，适用于开发和测试环境。
 * 生产环境建议替换为基于数据库的实现。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Component
public class InMemoryTeamRunRepository implements TeamRunRepository {

    private final Map<String, TeamRun> teamRunStore = new ConcurrentHashMap<>();

    @Override
    public TeamRun save(TeamRun teamRun) {
        Objects.requireNonNull(teamRun, "teamRun 不能为 null");
        teamRunStore.put(teamRun.getTeamRunId(), teamRun);
        return teamRun;
    }

    @Override
    public Optional<TeamRun> findById(String teamRunId) {
        return Optional.ofNullable(teamRunStore.get(teamRunId));
    }

    @Override
    public List<TeamRun> findActiveByTenant(String tenantId) {
        return teamRunStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .filter(t -> t.getStatus().isActive())
                .collect(Collectors.toList());
    }

    @Override
    public List<TeamRun> findAllByTenant(String tenantId) {
        return teamRunStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String teamRunId) {
        teamRunStore.remove(teamRunId);
    }

    @Override
    public long countByTenant(String tenantId) {
        return teamRunStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .count();
    }
}
