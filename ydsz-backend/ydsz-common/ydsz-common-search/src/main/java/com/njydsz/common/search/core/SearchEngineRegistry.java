package com.njydsz.common.search.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.config.SearchProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索引擎策略注册中心
 * <p>
 * 管理所有已注册的 {@link SearchStrategy} 实例，支持主引擎 + 降级链模式。
 * 搜索时优先使用主引擎，主引擎不可用或异常时按 fallback 链自动降级。
 *
 * <p>引擎能力查询通过 {@link #getIndexStrategy()} 和 {@link #getSuggestStrategy()} 方法，
 * 返回 Optional 以支持引擎不实现对应能力的场景。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class SearchEngineRegistry {

    private final Map<String, SearchStrategy> engineMap;
    private final SearchStrategy primary;
    private final List<SearchStrategy> fallbackChain;

    /**
     * 构造引擎注册中心
     *
     * @param strategies 所有已注册的引擎策略
     * @param properties 搜索配置（用于确定 primary 和 fallbacks）
     */
    public SearchEngineRegistry(List<SearchStrategy> strategies, SearchProperties properties) {
        this.engineMap = strategies.stream()
                .collect(Collectors.toMap(
                        SearchStrategy::getEngineName,
                        s -> s,
                        (a, b) -> {
                            log.warn("[SearchEngineRegistry] 引擎名称冲突，保留后者: {} -> {}",
                                    a.getClass().getSimpleName(), b.getClass().getSimpleName());
                            return b;
                        },
                        LinkedHashMap::new
                ));

        String primaryName = properties.getPrimary();
        this.primary = engineMap.get(primaryName);
        if (this.primary == null) {
            log.warn("[SearchEngineRegistry] 主引擎 '{}' 未找到，可用引擎: {}",
                    primaryName, engineMap.keySet());
        }

        this.fallbackChain = new ArrayList<>();
        if (properties.getFallbacks() != null) {
            for (String fallbackName : properties.getFallbacks()) {
                SearchStrategy fallback = engineMap.get(fallbackName);
                if (fallback != null && fallback != this.primary) {
                    this.fallbackChain.add(fallback);
                }
            }
        }

        log.info("[SearchEngineRegistry] 初始化完成: primary={}, fallbacks={}, engines={}",
                primaryName,
                this.fallbackChain.stream().map(SearchStrategy::getEngineName).toList(),
                engineMap.keySet());
    }

    /**
     * 执行搜索 — 主引擎优先，失败按降级链降级
     *
     * @param request 搜索请求
     * @return 搜索响应
     */
    public SearchResponse search(SearchRequest request) {
        // 尝试主引擎
        if (primary != null && primary.isAvailable()) {
            try {
                return primary.search(request);
            } catch (Exception e) {
                log.warn("[SearchEngineRegistry] 主引擎 '{}' 搜索失败，尝试降级: {}",
                        primary.getEngineName(), e.getMessage());
            }
        } else if (primary != null) {
            log.debug("[SearchEngineRegistry] 主引擎 '{}' 不可用，尝试降级", primary.getEngineName());
        }

        // 按 fallback 链降级
        for (SearchStrategy fallback : fallbackChain) {
            if (fallback.isAvailable()) {
                try {
                    SearchResponse response = fallback.search(request);
                    response.setDegraded(true);
                    return response;
                } catch (Exception e) {
                    log.warn("[SearchEngineRegistry] 降级引擎 '{}' 搜索失败: {}",
                            fallback.getEngineName(), e.getMessage());
                }
            }
        }

        // 全部失败
        return SearchResponse.empty(request.getPage(), request.getPageSize());
    }

    /**
     * 获取主引擎的索引策略（如果主引擎实现了 IndexStrategy）
     *
     * @return 索引策略 Optional
     */
    public Optional<IndexStrategy> getIndexStrategy() {
        if (primary != null && primary instanceof IndexStrategy idx) {
            return Optional.of(idx);
        }
        return Optional.empty();
    }

    /**
     * 获取主引擎的建议策略（如果主引擎实现了 SuggestStrategy）
     *
     * @return 建议策略 Optional
     */
    public Optional<SuggestStrategy> getSuggestStrategy() {
        if (primary != null && primary instanceof SuggestStrategy sug) {
            return Optional.of(sug);
        }
        // fallback 链中查找
        for (SearchStrategy fallback : fallbackChain) {
            if (fallback instanceof SuggestStrategy sug && fallback.isAvailable()) {
                return Optional.of(sug);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取主引擎
     *
     * @return 主引擎策略，可能为 null
     */
    public SearchStrategy getPrimary() {
        return primary;
    }

    /**
     * 获取所有已注册的引擎
     *
     * @return 引擎列表
     */
    public List<SearchStrategy> getAllEngines() {
        return List.copyOf(engineMap.values());
    }

    /**
     * 按名称获取引擎
     *
     * @param name 引擎名称
     * @return 引擎策略，不存在返回 null
     */
    public SearchStrategy getEngine(String name) {
        return engineMap.get(name);
    }

    /**
     * 检查主引擎是否可用
     *
     * @return 主引擎可用返回 true
     */
    public boolean isPrimaryAvailable() {
        return primary != null && primary.isAvailable();
    }

    /**
     * 获取主引擎能力描述
     *
     * @return 引擎能力，主引擎为 null 时返回最小能力
     */
    public EngineCapability getPrimaryCapability() {
        return primary != null ? primary.getCapability() : EngineCapability.minimal();
    }

    @Override
    public String toString() {
        return "SearchEngineRegistry{primary=" + (primary != null ? primary.getEngineName() : "null")
                + ", fallbacks=" + fallbackChain.stream().map(SearchStrategy::getEngineName).toList()
                + ", engines=" + engineMap.keySet() + "}";
    }
}
