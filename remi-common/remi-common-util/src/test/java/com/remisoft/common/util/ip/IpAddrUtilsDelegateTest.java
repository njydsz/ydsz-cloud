package com.remisoft.common.util.ip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IpAddrUtils} 委托兼容性测试 — 验证 1.4.0 拆分后所有 @Deprecated 委托方法正确转发。
 *
 * @author remi-team
 * @since 1.4.0
 */
@DisplayName("IpAddrUtils 委托兼容性测试")
class IpAddrUtilsDelegateTest {

    @Test
    @DisplayName("validIp 委托到 IpValidator")
    void validIpDelegates() {
        assertThat(IpAddrUtils.validIp("192.168.1.1")).isTrue();
        assertThat(IpAddrUtils.validIp("not-an-ip")).isFalse();
    }

    @Test
    @DisplayName("validIpv4 委托到 IpValidator")
    void validIpv4Delegates() {
        assertThat(IpAddrUtils.validIpv4("10.0.0.1")).isTrue();
        assertThat(IpAddrUtils.validIpv4("999.999.999.999")).isFalse();
        assertThat(IpAddrUtils.validIpv4("")).isFalse();
    }

    @Test
    @DisplayName("validIpv6 委托到 IpValidator")
    void validIpv6Delegates() {
        assertThat(IpAddrUtils.validIpv6("::1")).isTrue();
        assertThat(IpAddrUtils.validIpv6("2001:db8::ff00:42:8329")).isTrue();
        assertThat(IpAddrUtils.validIpv6("not-ipv6")).isFalse();
    }

    @Test
    @DisplayName("isInternalIp 委托到 IpValidator")
    void isInternalIpDelegates() {
        assertThat(IpAddrUtils.isInternalIp("127.0.0.1")).isTrue();
        assertThat(IpAddrUtils.isInternalIp("192.168.1.100")).isTrue();
        assertThat(IpAddrUtils.isInternalIp("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("internalIp 委托到 isInternalIp")
    void internalIpAliasDelegates() {
        assertThat(IpAddrUtils.internalIp("10.0.0.1")).isTrue();
        assertThat(IpAddrUtils.internalIp("114.114.114.114")).isFalse();
    }

    @Test
    @DisplayName("isPrivateIp 委托到 IpValidator")
    void isPrivateIpDelegates() {
        assertThat(IpAddrUtils.isPrivateIp("192.168.1.1")).isTrue();
        assertThat(IpAddrUtils.isPrivateIp("172.16.0.1")).isTrue();
        assertThat(IpAddrUtils.isPrivateIp("8.8.8.8")).isFalse();
        assertThat(IpAddrUtils.isPrivateIp("unknown")).isFalse();
    }

    @Test
    @DisplayName("normalizeIp 委托到 IpValidator")
    void normalizeIpDelegates() {
        assertThat(IpAddrUtils.normalizeIp(" 192.168.1.1 ")).isEqualTo("192.168.1.1");
        assertThat(IpAddrUtils.normalizeIp("::1")).isEqualTo("127.0.0.1");
        assertThat(IpAddrUtils.normalizeIp("unknown")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("normalizeIpv6 委托到 IpValidator")
    void normalizeIpv6Delegates() {
        String normalized = IpAddrUtils.normalizeIpv6("::1");
        assertThat(normalized).isEqualTo("0:0:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("getIpType 委托到 IpValidator")
    void getIpTypeDelegates() {
        assertThat(IpAddrUtils.getIpType("127.0.0.1")).isEqualTo(IpAddrUtils.IpType.LOCALHOST);
        assertThat(IpAddrUtils.getIpType("10.0.0.1")).isEqualTo(IpAddrUtils.IpType.PRIVATE_IPV4);
        assertThat(IpAddrUtils.getIpType("8.8.8.8")).isEqualTo(IpAddrUtils.IpType.PUBLIC_IPV4);
        assertThat(IpAddrUtils.getIpType("::1")).isEqualTo(IpAddrUtils.IpType.LOCALHOST);
    }

    @Test
    @DisplayName("isInRange 委托到 CidrUtils")
    void isInRangeDelegates() {
        assertThat(IpAddrUtils.isInRange("192.168.1.100", "192.168.1.0/24")).isTrue();
        assertThat(IpAddrUtils.isInRange("10.0.0.1", "192.168.1.0/24")).isFalse();
        assertThat(IpAddrUtils.isInRange("", "192.168.1.0/24")).isFalse();
    }

    @Test
    @DisplayName("isIpv4InRange 委托到 CidrUtils")
    void isIpv4InRangeDelegates() {
        assertThat(IpAddrUtils.isIpv4InRange("192.168.1.100", "192.168.1.0", 24)).isTrue();
        assertThat(IpAddrUtils.isIpv4InRange("192.168.2.1", "192.168.1.0", 24)).isFalse();
        assertThat(IpAddrUtils.isIpv4InRange("10.0.0.1", "0.0.0.0", 0)).isTrue();
    }

    @Test
    @DisplayName("isIpv6InRange 委托到 CidrUtils")
    void isIpv6InRangeDelegates() {
        assertThat(IpAddrUtils.isIpv6InRange("2001:db8::1", "2001:db8::", 32)).isTrue();
        assertThat(IpAddrUtils.isIpv6InRange("2002:db8::1", "2001:db8::", 32)).isFalse();
    }

    @Test
    @DisplayName("ipToLong / longToIp 互逆")
    void ipToLongRoundtrip() {
        assertThat(IpAddrUtils.ipToLong("192.168.1.1")).isEqualTo(3232235777L);
        assertThat(IpAddrUtils.longToIp(3232235777L)).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("getPrefixLength / getNetmaskFromPrefix 互逆")
    void prefixLengthRoundtrip() {
        assertThat(IpAddrUtils.getPrefixLength("255.255.255.0")).isEqualTo(24);
        assertThat(IpAddrUtils.getNetmaskFromPrefix(24)).isEqualTo("255.255.255.0");
        assertThat(IpAddrUtils.getNetmaskFromPrefix(0)).isEqualTo("0.0.0.0");
    }

    @Test
    @DisplayName("getNetworkAddress / getBroadcastAddress 委托到 CidrUtils")
    void networkAndBroadcastDelegates() {
        assertThat(IpAddrUtils.getNetworkAddress("192.168.1.100", 24)).isEqualTo("192.168.1.0");
        assertThat(IpAddrUtils.getBroadcastAddress("192.168.1.100", 24)).isEqualTo("192.168.1.255");
    }

    @Test
    @DisplayName("getHostIp 委托到 NetworkInterfaceUtils")
    void getHostIpDelegates() {
        String hostIp = IpAddrUtils.getHostIp();
        assertThat(hostIp).isNotEmpty();
    }

    @Test
    @DisplayName("getHostName 委托到 NetworkInterfaceUtils")
    void getHostNameDelegates() {
        String hostName = IpAddrUtils.getHostName();
        assertThat(hostName).isNotEmpty();
    }

    @Test
    @DisplayName("listLocalIps 委托到 NetworkInterfaceUtils")
    void listLocalIpsDelegates() {
        List<String> ips = IpAddrUtils.listLocalIps();
        assertThat(ips).isNotNull();
    }

    @Test
    @DisplayName("isDataCenterIp 保留在本类，委托到 IpValidator")
    void isDataCenterIpWorks() {
        assertThat(IpAddrUtils.isDataCenterIp("10.0.0.1")).isTrue();
        assertThat(IpAddrUtils.isDataCenterIp("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("isProxyIp 委托到 isDataCenterIp")
    void isProxyIpWorks() {
        assertThat(IpAddrUtils.isProxyIp("192.168.1.1")).isTrue();
        assertThat(IpAddrUtils.isProxyIp("114.114.114.114")).isFalse();
    }
}