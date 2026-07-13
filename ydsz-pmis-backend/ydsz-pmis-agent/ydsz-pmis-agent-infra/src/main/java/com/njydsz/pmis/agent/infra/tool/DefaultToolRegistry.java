package com.njydsz.pmis.agent.infra.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.domain.model.ToolCall;
import com.njydsz.pmis.agent.domain.model.ToolDefinition;
import com.njydsz.pmis.agent.domain.tool.ToolExecutor;
import com.njydsz.pmis.agent.domain.tool.ToolRegistration;
import com.njydsz.pmis.agent.domain.tool.ToolRegistry;

/**
 * 默认工具注册中心实现
 *
 * <p>使用 {@link ConcurrentHashMap} 存储工具注册条目，线程安全。
 * 支持编程式注册和注解扫描注册（通过 {@code ToolAnnotationScanner}）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class DefaultToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);
    private final Map<String, ToolRegistration> registry = new ConcurrentHashMap<>();

    @Override
    public void register(String name, ToolExecutor executor) {
        ToolRegistration registration = ToolRegistration.builder()
                .name(name)
                .description("Tool: " + name)
                .executor(executor)
                .build();
        registry.put(name, registration);
        log.info("[Tool-Registry] 注册工具: {}", name);
    }

    public void register(ToolRegistration registration) {
        registry.put(registration.getName(), registration);
        log.info("[Tool-Registry] 注册工具: {} (desc={})",
                registration.getName(), registration.getDefinition().getDescription());
    }

    @Override
    public void unregister(String name) {
        registry.remove(name);
        log.info("[Tool-Registry] 注销工具: {}", name);
    }

    @Override
    public String execute(ToolCall toolCall) {
        ToolRegistration registration = registry.get(toolCall.getName());
        if (registration == null) {
            log.warn("[Tool-Registry] 工具未找到: {}", toolCall.getName());
            return JSON.toJSONString(Map.of("error", "工具未找到: " + toolCall.getName()));
        }
        long startTime = System.currentTimeMillis();
        try {
            String result = registration.getExecutor().execute(toolCall.getArguments());
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Tool-Registry] 工具执行完成: {} ({}ms)", toolCall.getName(), duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Tool-Registry] 工具执行失败: {} ({}ms): {}",
                    toolCall.getName(), duration, e.getMessage(), e);
            return JSON.toJSONString(Map.of(
                    "error", "工具执行失败: " + e.getMessage(),
                    "tool", toolCall.getName()));
        }
    }

    @Override
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>(registry.size());
        for (ToolRegistration reg : registry.values()) {
            defs.add(reg.getDefinition());
        }
        return defs;
    }

    @Override
    public int size() {
        return registry.size();
    }

    @Override
    public boolean contains(String name) {
        return registry.containsKey(name);
    }
}
