package com.remisoft.common.tenant.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.remisoft.common.tenant.TenantContextHolder;

/**
 * 租户级配置隔离。
 *
 * <p>允许不同租户有差异化的配置覆盖（feature flag / 参数 / 阈值）。
 *
 * <p><b>解析优先级：</b>
 * <ol>
 *   <li>per-tenant 覆盖配置（{@code remi.tenant.overrides.{tenantId}.{key}}）</li>
 *   <li>全局默认值</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // YAML 配置
 * remi:
 *   tenant:
 *     overrides:
 *       "tenant_001":
 *         feature.export.enabled: true
 *         limit.api.calls: 500
 *
 * // 代码读取
 * String value = tenantConfigProvider.get("feature.export.enabled", "false");
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TenantConfigProvider {

    private final Map<String, Map<String, String>> overridesCache = new ConcurrentHashMap<>();

    public TenantConfigProvider() {
    }

    /**
     * 获取配置值（带租户覆盖优先级）。
     *
     * @param key          配置 Key
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String get(String key, String defaultValue) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            Map<String, String> tenantOverrides = overridesCache.get(tenantId);
            if (tenantOverrides != null && tenantOverrides.containsKey(key)) {
                return tenantOverrides.get(key);
            }
        }
        return defaultValue;
    }

    /**
     * 获取布尔配置值。
     *
     * @param key          配置 Key
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取整数配置值。
     *
     * @param key          配置 Key
     * @param defaultValue 默认值
     * @return 整数值
     */
    public int getInt(String key, int defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 注册租户配置覆盖。
     *
     * @param tenantId    租户 ID
     * @param key         配置 Key
     * @param value       配置值
     */
    public void setOverride(String tenantId, String key, String value) {
        overridesCache
                .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(key, value);
    }

    /**
     * 批量注册租户配置覆盖。
     *
     * @param tenantId 租户 ID
     * @param overrides 配置 Map
     */
    public void setOverrides(String tenantId, Map<String, String> overrides) {
        if (tenantId != null && overrides != null) {
            overridesCache.put(tenantId, new ConcurrentHashMap<>(overrides));
        }
    }

    /**
     * 清除指定租户的配置覆盖。
     *
     * @param tenantId 租户 ID
     */
    public void clearOverride(String tenantId) {
        overridesCache.remove(tenantId);
    }
}
