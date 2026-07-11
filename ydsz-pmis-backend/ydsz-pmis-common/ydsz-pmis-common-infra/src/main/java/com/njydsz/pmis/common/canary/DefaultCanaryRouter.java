package com.njydsz.pmis.common.canary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认灰度路由器实现（P2-1 架构优化）。
 *
 * <p>自动发现所有 {@link CanaryTarget} 实现，按模块名路由。
 * 灰度配置存储在内存中（生产环境可替换为 Redis / DB 持久化）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class DefaultCanaryRouter implements CanaryRouter {

    /** 模块 → 灰度目标 映射（Spring 自动注入） */
    private final Map<String, CanaryTarget> targets;

    /** 模块 → 灰度配置 映射（内存存储，可扩展为持久化） */
    private final Map<String, CanaryConfig> configStore = new ConcurrentHashMap<>();

    /**
     * 构造器，Spring 自动注入所有 CanaryTarget 实现。
     *
     * @param canaryTargets 灰度目标列表
     */
    public DefaultCanaryRouter(List<CanaryTarget> canaryTargets) {
        this.targets = canaryTargets == null ? Map.of() :
                canaryTargets.stream()
                        .collect(Collectors.toMap(CanaryTarget::getModule, t -> t, (a, b) -> a));
        log.info("[CanaryRouter] 已加载 {} 个灰度目标: {}", targets.size(), targets.keySet());
    }

    @Override
    public String route(String module, CanaryContext context) {
        CanaryTarget target = targets.get(module);
        if (target == null) {
            return "default";
        }
        try {
            return target.selectVersion(context);
        } catch (Exception e) {
            log.warn("[CanaryRouter] 灰度路由异常，降级到 default: module={} err={}",
                    module, e.getMessage());
            return "default";
        }
    }

    @Override
    public List<CanaryConfig> getConfigs(String module) {
        return configStore.values().stream()
                .filter(c -> module == null || module.equals(c.getModule()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateConfig(String module, CanaryConfig config) {
        config.setModule(module);
        configStore.put(module + ":" + config.getVersion(), config);
        log.info("[CanaryRouter] 灰度配置更新: module={} version={} percent={} enabled={}",
                module, config.getVersion(), config.getPercent(), config.isEnabled());
    }
}
