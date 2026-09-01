package com.njydsz.agent.infra.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.runtime.RuntimeSession;
import com.njydsz.agent.domain.runtime.RuntimeSessionStore;

/**
 * 基于内存的 Agent 运行时会话存储实现。
 *
 * <p>使用 ConcurrentHashMap 存储会话，支持高并发读写。
 * 会话数据在应用重启后丢失，适用于单实例部署或开发环境。
 * 生产环境建议替换为 Redis 实现以支持集群部署。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Component
public class InMemoryRuntimeSessionStore implements RuntimeSessionStore {

    private static final int DEFAULT_FIND_ALL_LIMIT = 200;
    private static final Comparator<RuntimeSession> START_TIME_DESC =
            (a, b) -> {
                if (a.getStartTime() == null && b.getStartTime() == null) {
                    return 0;
                }
                if (a.getStartTime() == null) {
                    return 1;
                }
                if (b.getStartTime() == null) {
                    return -1;
                }
                return b.getStartTime().compareTo(a.getStartTime());
            };

    private final ConcurrentHashMap<String, RuntimeSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(RuntimeSession session) {
        if (session == null || session.getExecutionId() == null) {
            return;
        }
        sessions.put(session.getExecutionId(), session);
    }

    @Override
    public Optional<RuntimeSession> findByExecutionId(String executionId) {
        return Optional.ofNullable(sessions.get(executionId));
    }

    @Override
    public List<RuntimeSession> findActiveSessions() {
        return sessions.values().stream()
                .filter(RuntimeSession::isActive)
                .sorted(START_TIME_DESC)
                .collect(Collectors.toList());
    }

    @Override
    public List<RuntimeSession> findActiveSessionsByTenant(String tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return sessions.values().stream()
                .filter(RuntimeSession::isActive)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .sorted(START_TIME_DESC)
                .collect(Collectors.toList());
    }

    @Override
    public void remove(String executionId) {
        if (executionId != null) {
            sessions.remove(executionId);
        }
    }

    @Override
    public List<RuntimeSession> findAll(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), DEFAULT_FIND_ALL_LIMIT);
        return sessions.values().stream()
                .sorted(START_TIME_DESC)
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    @Override
    public long countActive() {
        return sessions.values().stream()
                .filter(RuntimeSession::isActive)
                .count();
    }
}
