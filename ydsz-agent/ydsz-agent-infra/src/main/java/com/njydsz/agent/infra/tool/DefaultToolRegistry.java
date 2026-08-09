package com.njydsz.agent.infra.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.YdszJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.agent.domain.tool.ToolRegistration;
import com.njydsz.agent.domain.tool.ToolRegistry;

/**
 * 默认工具注册中心实现
 *
 * <p>使用 {@link ConcurrentHashMap} 存储工具注册条目，线程安全。
 * 支持编程式注册和注解扫描注册（通过 {@code ToolAnnotationScanner}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);
    /** 工具注册表（key=工具名） */
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

    /**
     * 注册一个携带完整元数据的工具条目。
     *
     * <p>与 {@link #register(String, ToolExecutor)} 的区别在于：后者只能生成
     * {@code "Tool: xxx"} 这类占位描述与空参数 schema，而本方法保留调用方定义的
     * description 与参数 schema，LLM 才能据此正确选择工具并填充入参，
     * 因此注解扫描与显式声明场景应优先使用本方法。
     *
     * <p>以工具名为键写入，同名工具后注册者覆盖先注册者，可用于运行时热更新工具实现。
     *
     * <p><b>并发</b>：底层为 {@link ConcurrentHashMap}，可在运行期安全并发调用。
     *
     * @param registration 工具注册条目，不可为 {@code null}，其 name 需全局唯一
     */
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
            return YdszJson.toJson(Map.of("error", "工具未找到: " + toolCall.getName()));
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
            return YdszJson.toJson(Map.of(
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
