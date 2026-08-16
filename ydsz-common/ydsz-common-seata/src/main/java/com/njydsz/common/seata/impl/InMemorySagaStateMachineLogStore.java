package com.njydsz.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.njydsz.common.seata.api.SagaStateMachineLog;
import com.njydsz.common.seata.api.SagaStateMachineLogStore;

/**
 * 内存版 SAGA 状态机日志存储
 *
 * <p>使用 {@link ConcurrentHashMap} 存储 SAGA 状态机日志，
 * 适用于单机开发/测试环境。
 *
 * <p>生产环境应使用数据库实现（如 {@code DbSagaStateMachineLogStore}）
 * 以支持跨实例恢复。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class InMemorySagaStateMachineLogStore implements SagaStateMachineLogStore {

    private final Map<String, SagaStateMachineLog> store = new ConcurrentHashMap<>();

    @Override
    public void save(SagaStateMachineLog log) {
        store.put(log.getXid(), log);
    }

    @Override
    public void updateState(String xid, SagaStateMachineLog.SagaState state) {
        SagaStateMachineLog log = store.get(xid);
        if (log != null) {
            log.setState(state);
        }
    }

    @Override
    public SagaStateMachineLog findByXid(String xid) {
        return store.get(xid);
    }

    @Override
    public List<SagaStateMachineLog> findTimeoutPending(LocalDateTime threshold, int limit) {
        return store.values().stream()
                .filter(log -> !log.getState().isFinal())
                .filter(log -> log.getUpdatedAt().isBefore(threshold))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String xid) {
        store.remove(xid);
    }
}
