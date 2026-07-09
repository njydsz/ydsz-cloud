package com.njydsz.pmis.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IpWhitelistProperties 单元测试
 *
 * <p>验证 IP 白名单配置属性的默认值、setter/getter 行为。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("IpWhitelistProperties IP白名单配置属性测试")
class IpWhitelistPropertiesTest {

    private IpWhitelistProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IpWhitelistProperties();
    }

    @Test
    @DisplayName("正常场景：默认 ipWhitelist 为空字符串")
    void defaultIpWhitelistShouldBeEmpty() {
        assertEquals("", properties.getIpWhitelist());
    }

    @Test
    @DisplayName("正常场景：默认 ipWhitelistEnabled 为 false")
    void defaultIpWhitelistEnabledShouldBeFalse() {
        assertFalse(properties.isIpWhitelistEnabled());
    }

    @Test
    @DisplayName("正常场景：默认 ipWhitelistSkipPaths 为空列表")
    void defaultSkipPathsShouldBeEmpty() {
        assertNotNull(properties.getIpWhitelistSkipPaths());
        assertTrue(properties.getIpWhitelistSkipPaths().isEmpty());
    }

    @Test
    @DisplayName("正常场景：setter/getter 正确设置 ipWhitelist")
    void setIpWhitelistShouldWork() {
        String whitelist = "192.168.1.0/24,10.0.0.1";
        properties.setIpWhitelist(whitelist);
        assertEquals(whitelist, properties.getIpWhitelist());
    }

    @Test
    @DisplayName("正常场景：setter/getter 正确设置 ipWhitelistEnabled")
    void setIpWhitelistEnabledShouldWork() {
        properties.setIpWhitelistEnabled(true);
        assertTrue(properties.isIpWhitelistEnabled());

        properties.setIpWhitelistEnabled(false);
        assertFalse(properties.isIpWhitelistEnabled());
    }

    @Test
    @DisplayName("正常场景：setter/getter 正确设置 ipWhitelistSkipPaths")
    void setSkipPathsShouldWork() {
        List<String> paths = new ArrayList<>();
        paths.add("/health");
        paths.add("/auth/login");
        properties.setIpWhitelistSkipPaths(paths);

        assertEquals(2, properties.getIpWhitelistSkipPaths().size());
        assertEquals("/health", properties.getIpWhitelistSkipPaths().get(0));
        assertEquals("/auth/login", properties.getIpWhitelistSkipPaths().get(1));
    }

    @Test
    @DisplayName("正常场景：setter/getter 支持覆盖 skipPaths")
    void skipPathsShouldBeReplacable() {
        List<String> initial = new ArrayList<>();
        initial.add("/health");
        properties.setIpWhitelistSkipPaths(initial);
        assertEquals(1, properties.getIpWhitelistSkipPaths().size());

        List<String> replacement = new ArrayList<>();
        replacement.add("/health");
        replacement.add("/auth/login");
        replacement.add("/auth/captcha");
        properties.setIpWhitelistSkipPaths(replacement);
        assertEquals(3, properties.getIpWhitelistSkipPaths().size());
    }
}
