package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IpUtils 单元测试（P2-8 安全加固）
 *
 * <p>覆盖范围:
 * <ul>
 *   <li>IPv4 格式校验</li>
 *   <li>CIDR 匹配（命中/不命中/边界）</li>
 *   <li>白名单统一判定（单个 IP + CIDR 混合）</li>
 *   <li>客户端 IP 解析（X-Forwarded-For / X-Real-IP / RemoteAddr）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("IpUtils IP 工具测试")
class IpUtilsTest {

    // ==================== isValidIp ====================

    @Test
    @DisplayName("isValidIp - 合法 IPv4 应返回 true")
    void isValidIp_valid_shouldReturnTrue() {
        assertTrue(IpUtils.isValidIp("0.0.0.0"));
        assertTrue(IpUtils.isValidIp("127.0.0.1"));
        assertTrue(IpUtils.isValidIp("192.168.1.100"));
        assertTrue(IpUtils.isValidIp("10.0.0.1"));
        assertTrue(IpUtils.isValidIp("255.255.255.255"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "256.1.1.1",       // 段值超 255
            "192.168.1",       // 段数不足
            "192.168.1.1.1",   // 段数过多
            "192.168.1.a",     // 非数字
            "192.168.1.-1",    // 负数
            "192.168.01.1",    // 前导零
            "",                // 空
            "abc",             // 非法
            "192.168.1.1/24",  // 含 CIDR 前缀
            " 192.168.1.1 "    // 含空格
    })
    @DisplayName("isValidIp - 非法 IPv4 应返回 false")
    void isValidIp_invalid_shouldReturnFalse(String ip) {
        assertFalse(IpUtils.isValidIp(ip));
    }

    @Test
    @DisplayName("isValidIp - null 应返回 false")
    void isValidIp_null_shouldReturnFalse() {
        assertFalse(IpUtils.isValidIp(null));
    }

    // ==================== isInRange (CIDR 匹配) ====================

    @Test
    @DisplayName("isInRange - CIDR 范围内应返回 true")
    void isInRange_withinCidr_shouldReturnTrue() {
        assertTrue(IpUtils.isInRange("192.168.1.100", "192.168.1.0/24"));
        assertTrue(IpUtils.isInRange("192.168.1.0", "192.168.1.0/24"));
        assertTrue(IpUtils.isInRange("192.168.1.255", "192.168.1.0/24"));
        assertTrue(IpUtils.isInRange("10.0.0.5", "10.0.0.0/8"));
        assertTrue(IpUtils.isInRange("172.16.5.10", "172.16.0.0/12"));
    }

    @Test
    @DisplayName("isInRange - CIDR 范围外应返回 false")
    void isInRange_outsideCidr_shouldReturnFalse() {
        assertFalse(IpUtils.isInRange("192.168.2.100", "192.168.1.0/24"));
        assertFalse(IpUtils.isInRange("192.169.1.100", "192.168.1.0/24"));
        assertFalse(IpUtils.isInRange("11.0.0.1", "10.0.0.0/8"));
    }

    @Test
    @DisplayName("isInRange - /32 前缀应精确匹配")
    void isInRange_prefix32_shouldExactMatch() {
        assertTrue(IpUtils.isInRange("192.168.1.1", "192.168.1.1/32"));
        assertFalse(IpUtils.isInRange("192.168.1.2", "192.168.1.1/32"));
    }

    @Test
    @DisplayName("isInRange - /0 前缀应匹配所有 IPv4")
    void isInRange_prefix0_shouldMatchAll() {
        assertTrue(IpUtils.isInRange("0.0.0.0", "0.0.0.0/0"));
        assertTrue(IpUtils.isInRange("255.255.255.255", "0.0.0.0/0"));
        assertTrue(IpUtils.isInRange("8.8.8.8", "0.0.0.0/0"));
    }

    @Test
    @DisplayName("isInRange - 无前缀长度的 CIDR 应视为单个 IP 精确匹配")
    void isInRange_noPrefix_shouldExactMatch() {
        assertTrue(IpUtils.isInRange("10.0.0.1", "10.0.0.1"));
        assertFalse(IpUtils.isInRange("10.0.0.2", "10.0.0.1"));
    }

    @Test
    @DisplayName("isInRange - 边界 IP（网络地址与广播地址）")
    void isInRange_boundary_shouldReturnTrue() {
        // /24 网络地址 192.168.1.0 与广播地址 192.168.1.255 均在范围内
        assertTrue(IpUtils.isInRange("192.168.1.0", "192.168.1.0/24"));
        assertTrue(IpUtils.isInRange("192.168.1.255", "192.168.1.0/24"));
    }

    @Test
    @DisplayName("isInRange - 非法 IP 或 CIDR 应返回 false")
    void isInRange_invalidInput_shouldReturnFalse() {
        assertFalse(IpUtils.isInRange("invalid", "192.168.1.0/24"));
        assertFalse(IpUtils.isInRange("192.168.1.1", "invalid/24"));
        assertFalse(IpUtils.isInRange("192.168.1.1", "192.168.1.0/33"));
        assertFalse(IpUtils.isInRange("192.168.1.1", "192.168.1.0/-1"));
        assertFalse(IpUtils.isInRange("192.168.1.1", null));
        assertFalse(IpUtils.isInRange("192.168.1.1", ""));
    }

    // ==================== isAllowed (白名单判定) ====================

    @Test
    @DisplayName("isAllowed - 命中单个 IP 应返回 true")
    void isAllowed_singleIp_shouldReturnTrue() {
        Set<String> whitelist = Set.of("10.0.0.1", "10.0.0.2");
        assertTrue(IpUtils.isAllowed("10.0.0.1", whitelist));
        assertTrue(IpUtils.isAllowed("10.0.0.2", whitelist));
    }

    @Test
    @DisplayName("isAllowed - 未命中单个 IP 应返回 false")
    void isAllowed_singleIp_notMatched_shouldReturnFalse() {
        Set<String> whitelist = Set.of("10.0.0.1");
        assertFalse(IpUtils.isAllowed("10.0.0.3", whitelist));
    }

    @Test
    @DisplayName("isAllowed - 命中 CIDR 应返回 true")
    void isAllowed_cidr_shouldReturnTrue() {
        Set<String> whitelist = Set.of("192.168.1.0/24", "10.0.0.1");
        assertTrue(IpUtils.isAllowed("192.168.1.100", whitelist));
        assertTrue(IpUtils.isAllowed("10.0.0.1", whitelist));
    }

    @Test
    @DisplayName("isAllowed - 未命中 CIDR 应返回 false")
    void isAllowed_cidr_notMatched_shouldReturnFalse() {
        Set<String> whitelist = Set.of("192.168.1.0/24");
        assertFalse(IpUtils.isAllowed("192.168.2.100", whitelist));
    }

    @Test
    @DisplayName("isAllowed - 白名单为空时应返回 false（由调用方决定放行策略）")
    void isAllowed_emptyWhitelist_shouldReturnFalse() {
        assertFalse(IpUtils.isAllowed("10.0.0.1", Set.of()));
        assertFalse(IpUtils.isAllowed("10.0.0.1", null));
    }

    @Test
    @DisplayName("isAllowed - 非法 IP 应返回 false")
    void isAllowed_invalidIp_shouldReturnFalse() {
        Set<String> whitelist = Set.of("10.0.0.1");
        assertFalse(IpUtils.isAllowed("invalid", whitelist));
        assertFalse(IpUtils.isAllowed("", whitelist));
        assertFalse(IpUtils.isAllowed(null, whitelist));
    }

    @Test
    @DisplayName("isAllowed - 白名单含空白条目时应跳过")
    void isAllowed_blankEntries_shouldSkip() {
        Set<String> whitelist = Set.of("10.0.0.1", "", "  ");
        assertTrue(IpUtils.isAllowed("10.0.0.1", whitelist));
        assertFalse(IpUtils.isAllowed("10.0.0.2", whitelist));
    }

    // ==================== getClientIp ====================

    @Test
    @DisplayName("getClientIp - X-Forwarded-For 存在时应取第一个 IP")
    void getClientIp_xForwardedFor_shouldReturnFirstIp() {
        ServerHttpRequest request = mockRequest("1.2.3.4, 5.6.7.8", null, null);
        assertEquals("1.2.3.4", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("getClientIp - X-Forwarded-For 单个 IP 时应直接返回")
    void getClientIp_xForwardedForSingle_shouldReturnIp() {
        ServerHttpRequest request = mockRequest("1.2.3.4", null, null);
        assertEquals("1.2.3.4", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("getClientIp - 无 X-Forwarded-For 时应回退到 X-Real-IP")
    void getClientIp_noXff_shouldFallbackToXRealIp() {
        ServerHttpRequest request = mockRequest(null, "9.9.9.9", null);
        assertEquals("9.9.9.9", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("getClientIp - 无代理头时应回退到 RemoteAddr")
    void getClientIp_noProxyHeaders_shouldFallbackToRemoteAddr() {
        ServerHttpRequest request = mockRequest(null, null, "8.8.8.8");
        assertEquals("8.8.8.8", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("getClientIp - 所有头均缺失时应返回空字符串")
    void getClientIp_allMissing_shouldReturnEmpty() {
        ServerHttpRequest request = mockRequest(null, null, null);
        assertEquals("", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("getClientIp - null 请求应返回空字符串")
    void getClientIp_nullRequest_shouldReturnEmpty() {
        assertEquals("", IpUtils.getClientIp(null));
    }

    @Test
    @DisplayName("getClientIp - X-Forwarded-For 优先级高于 X-Real-IP")
    void getClientIp_xffTakesPrecedenceOverXRealIp() {
        ServerHttpRequest request = mockRequest("1.1.1.1", "2.2.2.2", null);
        assertEquals("1.1.1.1", IpUtils.getClientIp(request));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 mock 的 ServerHttpRequest
     *
     * @param xff        X-Forwarded-For 头值（null 表示不设置）
     * @param xRealIp    X-Real-IP 头值（null 表示不设置）
     * @param remoteAddr 远端地址 host（null 表示不设置）
     * @return mock 请求对象
     */
    private ServerHttpRequest mockRequest(String xff, String xRealIp, String remoteAddr) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (xff != null) {
            headers.add("X-Forwarded-For", xff);
        }
        if (xRealIp != null) {
            headers.add("X-Real-IP", xRealIp);
        }
        when(request.getHeaders()).thenReturn(headers);
        if (remoteAddr != null) {
            try {
                InetSocketAddress socketAddress = new InetSocketAddress(
                        InetAddress.getByName(remoteAddr), 12345);
                when(request.getRemoteAddress()).thenReturn(socketAddress);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        } else {
            when(request.getRemoteAddress()).thenReturn(null);
        }
        return request;
    }
}
