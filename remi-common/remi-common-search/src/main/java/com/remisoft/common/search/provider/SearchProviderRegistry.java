package com.remisoft.common.search.provider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索提供者注册中心
 * <p>
 * 管理所有 {@link SearchProvider} 实例，按类型查找。
 * 搜索引擎通过注册中心获取各业务模块的 Provider。
 * <p>
 * 支持自动发现：Spring 容器中所有 {@code SearchProvider} Bean 会在构造时自动注册。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class SearchProviderRegistry {

    private final Map<String, SearchProvider<?>> providerMap = new ConcurrentHashMap<>();

    /**
     * 默认构造器（用于无 Provider 场景）
     */
    public SearchProviderRegistry() {
        // 无操作
    }

    /**
     * 自动注册构造器 — Spring 会注入所有 SearchProvider Bean
     *
     * @param providers Spring 容器中所有 SearchProvider 实例
     */
    public SearchProviderRegistry(List<SearchProvider<?>> providers) {
        if (providers != null) {
            for (SearchProvider<?> provider : providers) {
                register(provider);
            }
        }
        log.info("[SearchProviderRegistry] 自动注册完成: count={}, types={}",
                providerMap.size(), providerMap.keySet());
    }

    /**
     * 注册搜索提供者
     *
     * @param provider 搜索提供者
     */
    public void register(SearchProvider<?> provider) {
        if (provider == null || provider.getType() == null) {
            return;
        }
        String type = provider.getType();
        SearchProvider<?> existing = providerMap.put(type, provider);
        if (existing != null) {
            log.warn("[SearchProviderRegistry] 类型 {} 的 Provider 被覆盖: {} -> {}",
                    type, existing.getClass().getSimpleName(), provider.getClass().getSimpleName());
        } else {
            log.info("[SearchProviderRegistry] 注册 Provider: type={}, class={}",
                    type, provider.getClass().getSimpleName());
        }
    }

    /**
     * 注销搜索提供者
     *
     * @param type 实体类型
     */
    public void unregister(String type) {
        providerMap.remove(type);
    }

    /**
     * 获取搜索提供者
     * <p>
     * 由于 Java 类型擦除，泛型类型参数 {@code <T>} 无法在运行时校验。
     * 调用方需确保传入的类型与 Provider 实际泛型类型一致。
     *
     * @param type 实体类型
     * @return 提供者，不存在返回 null
     */
    public <T> SearchProvider<T> getProvider(String type) {
        SearchProvider<?> provider = providerMap.get(type);
        if (provider == null) {
            return null;
        }
        return ProviderTypeBridge.cast(provider);
    }

    /**
     * 获取所有已注册的提供者
     *
     * @return 提供者列表
     */
    public List<SearchProvider<?>> getAllProviders() {
        return List.copyOf(providerMap.values());
    }

    /**
     * 获取所有已注册的类型
     *
     * @return 类型列表
     */
    public List<String> getAllTypes() {
        return List.copyOf(providerMap.keySet());
    }

    /**
     * 检查类型是否已注册
     *
     * @param type 实体类型
     * @return 已注册返回 true
     */
    public boolean contains(String type) {
        return providerMap.containsKey(type);
    }

    /**
     * 获取指定类型列表的提供者
     *
     * @param types 类型列表，为空返回全部
     * @return 提供者列表
     */
    public List<SearchProvider<?>> getProviders(List<String> types) {
        if (types == null || types.isEmpty()) {
            return getAllProviders();
        }
        return types.stream()
                .map(providerMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
