package com.njydsz.agent.infra.trigger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerRepository;
import com.njydsz.agent.domain.trigger.TriggerType;

/**
 * 基于内存的触发器仓储实现。
 *
 * <p>使用 ConcurrentHashMap 存储触发器配置，适用于开发和测试环境。
 * 生产环境建议替换为基于数据库的实现（如 JPA/MyBatis）。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Component
public class InMemoryTriggerRepository implements TriggerRepository {

    private final Map<String, AgentTrigger> triggerStore = new ConcurrentHashMap<>();

    @Override
    public AgentTrigger save(AgentTrigger trigger) {
        Objects.requireNonNull(trigger, "trigger 不能为 null");
        triggerStore.put(trigger.getTriggerId(), trigger);
        return trigger;
    }

    @Override
    public Optional<AgentTrigger> findById(String triggerId) {
        return Optional.ofNullable(triggerStore.get(triggerId));
    }

    @Override
    public List<AgentTrigger> findEnabledByTenant(String tenantId) {
        return triggerStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .filter(AgentTrigger::isEnabled)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTrigger> findByTenantAndType(String tenantId, TriggerType triggerType) {
        return triggerStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .filter(AgentTrigger::isEnabled)
                .filter(t -> t.getTriggerType() == triggerType)
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentTrigger> findAllEnabledCronTriggers() {
        return triggerStore.values().stream()
                .filter(AgentTrigger::isEnabled)
                .filter(t -> t.getTriggerType() == TriggerType.CRON)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String triggerId) {
        triggerStore.remove(triggerId);
    }

    @Override
    public long countByTenant(String tenantId) {
        return triggerStore.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .count();
    }
}
