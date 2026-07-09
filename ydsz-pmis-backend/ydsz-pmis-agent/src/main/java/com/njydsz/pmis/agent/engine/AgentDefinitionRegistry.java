package com.njydsz.pmis.agent.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 定义注册中心（P1-6 落地）。
 *
 * <p>对标 Coze Bot Store / Dify 应用列表：
 * <ul>
 *   <li>管理所有 Agent 的 {@link AgentDefinition} 配置</li>
 *   <li>支持编程式注册和运行时动态注册</li>
 *   <li>支持按 agentType 查找配置</li>
 *   <li>支持配置刷新（热更新）</li>
 * </ul>
 *
 * <p>后续扩展：从 DB 加载 Agent 定义，支持管理界面增删改查。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-6)
 */
@Slf4j
@Component
public class AgentDefinitionRegistry {

    /** agentType → AgentDefinition */
    private final Map<String, AgentDefinition> registry = new ConcurrentHashMap<>();

    /**
     * 注册 Agent 定义。
     *
     * @param definition Agent 定义
     */
    public void register(AgentDefinition definition) {
        if (definition == null || definition.getAgentType() == null || definition.getAgentType().isBlank()) {
            throw new IllegalArgumentException("AgentDefinition 及 agentType 不能为空");
        }
        registry.put(definition.getAgentType(), definition);
        log.info("[AgentRegistry] 注册 Agent: type={}, displayName={}, mode={}",
                definition.getAgentType(), definition.getDisplayName(), definition.getReasoningMode());
    }

    /**
     * 获取 Agent 定义。
     *
     * @param agentType Agent 类型
     * @return Agent 定义；不存在返回 null
     */
    public AgentDefinition get(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return null;
        }
        return registry.get(agentType);
    }

    /**
     * 获取 Agent 定义（不存在时抛异常）。
     *
     * @param agentType Agent 类型
     * @return Agent 定义
     * @throws IllegalArgumentException Agent 不存在时
     */
    public AgentDefinition require(String agentType) {
        AgentDefinition def = get(agentType);
        if (def == null) {
            throw new IllegalArgumentException("Agent 定义不存在: " + agentType);
        }
        return def;
    }

    /**
     * 移除 Agent 定义。
     *
     * @param agentType Agent 类型
     */
    public void unregister(String agentType) {
        AgentDefinition removed = registry.remove(agentType);
        if (removed != null) {
            log.info("[AgentRegistry] 移除 Agent: type={}", agentType);
        }
    }

    /**
     * 获取所有已注册的 Agent 定义。
     *
     * @return 不可修改的 Agent 定义列表
     */
    public List<AgentDefinition> listAll() {
        return List.copyOf(registry.values());
    }

    /**
     * 获取所有已注册的 Agent 类型。
     *
     * @return Agent 类型列表
     */
    public Set<String> listAgentTypes() {
        return Set.copyOf(registry.keySet());
    }

    /**
     * 判断 Agent 是否已注册。
     *
     * @param agentType Agent 类型
     * @return true=已注册
     */
    public boolean contains(String agentType) {
        return agentType != null && registry.containsKey(agentType);
    }

    /**
     * 清空注册表（用于测试）。
     */
    public void clear() {
        registry.clear();
        log.info("[AgentRegistry] 注册表已清空");
    }
}
