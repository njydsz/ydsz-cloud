package com.njydsz.pmis.common.core.featureflag;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 特性开关管理器（增强版）
 *
 * <p>支持多数据源的特性开关管理：
 * <ul>
 *   <li>静态配置（application.yml）</li>
 *   <li>动态配置（Nacos / Apollo）</li>
 *   <li>灰度发布（按用户百分比）</li>
 *   <li>A/B 测试（按用户分组）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * if (FeatureFlagManager.isEnabled("new-payment-flow")) {
 *     return newPaymentService.charge(request);
 * } else {
 *     return oldPaymentService.charge(request);
 * }
 *
 * // 灰度发布
 * if (FeatureFlagManager.isEnabledForUser("new-dashboard", userId)) {
 *     return newDashboard();
 * } else {
 *     return oldDashboard();
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public class FeatureFlagManager {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagManager.class);

    private final Map<String, Boolean> staticFlags = new ConcurrentHashMap<>();
    private final Map<String, Integer> percentageFlags = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> whitelistFlags = new ConcurrentHashMap<>();

    /**
     * 注册静态开关
     */
    public void registerStatic(String flag, boolean enabled) {
        staticFlags.put(flag, enabled);
    }

    /**
     * 注册百分比灰度开关
     *
     * @param flag       开关名
     * @param percentage 百分比（0-100）
     */
    public void registerPercentage(String flag, int percentage) {
        percentageFlags.put(flag, Math.max(0, Math.min(100, percentage)));
    }

    /**
     * 注册白名单开关
     *
     * @param flag     开关名
     * @param userIds  白名单用户 ID
     */
    public void registerWhitelist(String flag, Set<String> userIds) {
        whitelistFlags.put(flag, ConcurrentHashMap.newKeySet());
        whitelistFlags.get(flag).addAll(userIds);
    }

    /**
     * 检查开关是否启用
     */
    public boolean isEnabled(String flag) {
        Boolean staticValue = staticFlags.get(flag);
        if (staticValue != null) {
            return staticValue;
        }
        return false;
    }

    /**
     * 检查开关是否对特定用户启用
     *
     * <p>优先级：白名单 > 百分比灰度 > 静态开关
     */
    public boolean isEnabledForUser(String flag, String userId) {
        // 1. 白名单优先
        Set<String> whitelist = whitelistFlags.get(flag);
        if (whitelist != null && whitelist.contains(userId)) {
            return true;
        }

        // 2. 百分比灰度
        Integer percentage = percentageFlags.get(flag);
        if (percentage != null) {
            return hashUserId(userId) % 100 < percentage;
        }

        // 3. 静态开关
        return isEnabled(flag);
    }

    /**
     * 动态更新开关状态
     */
    public void updateFlag(String flag, boolean enabled) {
        staticFlags.put(flag, enabled);
        log.info("Feature flag updated: {}={}", flag, enabled);
    }

    /**
     * 添加白名单用户
     */
    public void addToWhitelist(String flag, String userId) {
        whitelistFlags.computeIfAbsent(flag, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    /**
     * 移除白名单用户
     */
    public void removeFromWhitelist(String flag, String userId) {
        Set<String> whitelist = whitelistFlags.get(flag);
        if (whitelist != null) {
            whitelist.remove(userId);
        }
    }

    /**
     * 获取所有开关状态
     */
    public Map<String, Boolean> getAllFlags() {
        return Map.copyOf(staticFlags);
    }

    /**
     * 对 userId 进行一致性哈希
     */
    private int hashUserId(String userId) {
        return Math.abs(userId.hashCode());
    }
}
