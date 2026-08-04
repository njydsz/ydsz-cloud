package com.remisoft.gateway.config;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小冒烟级单测，补充 P0 测试覆盖缺口。
 *
 * <p>测试 {@link GatewayIpUtils#isAllowed(String, Set)} 的 IP 白名单匹配逻辑：
 * <ul>
 *   <li>精确匹配</li>
 *   <li>CIDR 子网匹配</li>
 *   <li>空值 / 不匹配场景</li>
 * </ul>
 *
 * <p>纯计算、无外部依赖（DB/Redis），直接调用静态方法即可。
 */
class GatewayIpUtilsSmokeTest {

    @Nested
    @DisplayName("isAllowed - IP 白名单匹配")
    class IsAllowedTests {

        @Test
        @DisplayName("精确匹配成功")
        void exactMatch_succeeds() {
            Set<String> whitelist = Set.of("192.168.1.100", "10.0.0.1");
            assertThat(GatewayIpUtils.isAllowed("192.168.1.100", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("10.0.0.1", whitelist)).isTrue();
        }

        @Test
        @DisplayName("精确匹配失败")
        void exactMatch_fails() {
            Set<String> whitelist = Set.of("192.168.1.100");
            assertThat(GatewayIpUtils.isAllowed("192.168.1.101", whitelist)).isFalse();
        }

        @Test
        @DisplayName("CIDR 子网匹配成功")
        void cidrMatch_succeeds() {
            Set<String> whitelist = Set.of("192.168.1.0/24");
            assertThat(GatewayIpUtils.isAllowed("192.168.1.1", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("192.168.1.254", whitelist)).isTrue();
        }

        @Test
        @DisplayName("CIDR 子网外 IP 不匹配")
        void cidrMatch_outsideRange_fails() {
            Set<String> whitelist = Set.of("192.168.1.0/24");
            assertThat(GatewayIpUtils.isAllowed("192.168.2.1", whitelist)).isFalse();
        }

        @Test
        @DisplayName("CIDR /32 仅匹配单个 IP")
        void cidrSlash32_singleIp() {
            Set<String> whitelist = Set.of("10.0.0.5/32");
            assertThat(GatewayIpUtils.isAllowed("10.0.0.5", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("10.0.0.6", whitelist)).isFalse();
        }

        @Test
        @DisplayName("IPv6 CIDR 匹配成功")
        void cidrIpv6_succeeds() {
            Set<String> whitelist = Set.of("2001:db8::/32");
            assertThat(GatewayIpUtils.isAllowed("2001:db8::1", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("2001:db9::1", whitelist)).isFalse();
        }

        @Test
        @DisplayName("空 IP 或空白名单返回 false")
        void emptyOrNullInputs_returnsFalse() {
            assertThat(GatewayIpUtils.isAllowed(null, Set.of("10.0.0.1"))).isFalse();
            assertThat(GatewayIpUtils.isAllowed("10.0.0.1", null)).isFalse();
            assertThat(GatewayIpUtils.isAllowed("10.0.0.1", Set.of())).isFalse();
            assertThat(GatewayIpUtils.isAllowed("", Set.of("10.0.0.1"))).isFalse();
        }

        @Test
        @DisplayName("混合精确 + CIDR 白名单，命中任一即 true")
        void mixedWhitelist_firstMatch() {
            Set<String> whitelist = Set.of("10.0.0.1", "172.16.0.0/12");
            assertThat(GatewayIpUtils.isAllowed("10.0.0.1", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("172.16.5.5", whitelist)).isTrue();
            assertThat(GatewayIpUtils.isAllowed("8.8.8.8", whitelist)).isFalse();
        }
    }
}
