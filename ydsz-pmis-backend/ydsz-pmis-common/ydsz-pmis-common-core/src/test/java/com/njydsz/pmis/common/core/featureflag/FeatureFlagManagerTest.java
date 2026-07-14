package com.njydsz.pmis.common.core.featureflag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FeatureFlagManager 单元测试
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@DisplayName("FeatureFlagManager 特性开关测试")
class FeatureFlagManagerTest {

    private FeatureFlagManager manager;

    @BeforeEach
    void setUp() {
        manager = new FeatureFlagManager();
    }

    @Test
    @DisplayName("静态开关 - 启用")
    void isEnabled_staticEnabled() {
        manager.registerStatic("feature-a", true);
        assertTrue(manager.isEnabled("feature-a"));
    }

    @Test
    @DisplayName("静态开关 - 禁用")
    void isEnabled_staticDisabled() {
        manager.registerStatic("feature-b", false);
        assertFalse(manager.isEnabled("feature-b"));
    }

    @Test
    @DisplayName("未注册的开关默认返回 false")
    void isEnabled_unregisteredReturnsFalse() {
        assertFalse(manager.isEnabled("nonexistent"));
    }

    @Test
    @DisplayName("白名单用户应启用")
    void isEnabledForUser_whitelist() {
        manager.registerWhitelist("vip-feature", Set.of("user1", "user2"));
        assertTrue(manager.isEnabledForUser("vip-feature", "user1"));
        assertTrue(manager.isEnabledForUser("vip-feature", "user2"));
        assertFalse(manager.isEnabledForUser("vip-feature", "user3"));
    }

    @Test
    @DisplayName("百分比灰度 - 100% 应全部启用")
    void isEnabledForUser_percentage100() {
        manager.registerPercentage("full-rollout", 100);
        assertTrue(manager.isEnabledForUser("full-rollout", "user1"));
        assertTrue(manager.isEnabledForUser("full-rollout", "user2"));
        assertTrue(manager.isEnabledForUser("full-rollout", "user3"));
    }

    @Test
    @DisplayName("百分比灰度 - 0% 应全部禁用")
    void isEnabledForUser_percentage0() {
        manager.registerPercentage("no-rollout", 0);
        assertFalse(manager.isEnabledForUser("no-rollout", "user1"));
        assertFalse(manager.isEnabledForUser("no-rollout", "user2"));
    }

    @Test
    @DisplayName("动态更新开关")
    void updateFlag_shouldUpdate() {
        manager.registerStatic("dynamic", false);
        assertFalse(manager.isEnabled("dynamic"));

        manager.updateFlag("dynamic", true);
        assertTrue(manager.isEnabled("dynamic"));
    }

    @Test
    @DisplayName("白名单动态添加用户")
    void addToWhitelist_shouldEnableForUser() {
        manager.registerWhitelist("beta", Set.of("user1"));
        assertFalse(manager.isEnabledForUser("beta", "user2"));

        manager.addToWhitelist("beta", "user2");
        assertTrue(manager.isEnabledForUser("beta", "user2"));
    }

    @Test
    @DisplayName("白名单优先级高于百分比")
    void isEnabledForUser_whitelistOverridesPercentage() {
        manager.registerPercentage("feature", 0);
        manager.registerWhitelist("feature", Set.of("vip-user"));

        assertTrue(manager.isEnabledForUser("feature", "vip-user"));
        assertFalse(manager.isEnabledForUser("feature", "normal-user"));
    }

    @Test
    @DisplayName("同一用户多次查询结果一致")
    void isEnabledForUser_consistentResult() {
        manager.registerPercentage("ab-test", 50);
        boolean first = manager.isEnabledForUser("ab-test", "user1");
        boolean second = manager.isEnabledForUser("ab-test", "user1");
        assertEquals(first, second);
    }
}
