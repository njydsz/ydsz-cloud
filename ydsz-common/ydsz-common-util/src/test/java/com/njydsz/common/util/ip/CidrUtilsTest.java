package com.njydsz.common.util.ip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CidrUtils} 单元测试。
 *
 * <p>覆盖：IPv4/IPv6 CIDR 判断、子网掩码、网络地址、广播地址、缓存等。
 *
 * @since 26.09.19
 */
@DisplayName("CidrUtils 测试")
class CidrUtilsTest {

  @BeforeEach
  void clearCache() {
    CidrUtils.clearCache();
  }

  @Nested
  @DisplayName("IPv4 CIDR 判断")
  class Ipv4RangeTest {

    @Test
    @DisplayName("isInRange: 192.168.1.100 在 192.168.1.0/24 内")
    void isInRange_withinSubnet() {
      assertThat(CidrUtils.isInRange("192.168.1.100", "192.168.1.0/24")).isTrue();
    }

    @Test
    @DisplayName("isInRange: 192.168.2.100 不在 192.168.1.0/24 内")
    void isInRange_outsideSubnet() {
      assertThat(CidrUtils.isInRange("192.168.2.100", "192.168.1.0/24")).isFalse();
    }

    @Test
    @DisplayName("isInRange: /0 包含所有 IP")
    void isInRange_prefix0_containsAll() {
      assertThat(CidrUtils.isInRange("10.0.0.1", "0.0.0.0/0")).isTrue();
    }

    @Test
    @DisplayName("isInRange: /32 仅包含单个 IP")
    void isInRange_prefix32_singleIp() {
      assertThat(CidrUtils.isInRange("192.168.1.1", "192.168.1.1/32")).isTrue();
      assertThat(CidrUtils.isInRange("192.168.1.2", "192.168.1.1/32")).isFalse();
    }

    @Test
    @DisplayName("isInRange: 非法输入返回 false")
    void isInRange_invalidInput_returnsFalse() {
      assertThat(CidrUtils.isInRange(null, "192.168.1.0/24")).isFalse();
      assertThat(CidrUtils.isInRange("192.168.1.1", null)).isFalse();
      assertThat(CidrUtils.isInRange("invalid", "192.168.1.0/24")).isFalse();
      assertThat(CidrUtils.isInRange("192.168.1.1", "invalid-cidr")).isFalse();
    }

    @Test
    @DisplayName("isInRange: IPv4/IPv6 混用时返回 false")
    void isInRange_mixedIpv4Ipv6_returnsFalse() {
      assertThat(CidrUtils.isInRange("::1", "192.168.1.0/24")).isFalse();
    }
  }

  @Nested
  @DisplayName("IPv6 CIDR 判断")
  class Ipv6RangeTest {

    @Test
    @DisplayName("isInRange: IPv6 地址在网段内")
    void isInRange_ipv6WithinSubnet() {
      assertThat(CidrUtils.isInRange("2001:db8::1", "2001:db8::/32")).isTrue();
    }

    @Test
    @DisplayName("isInRange: IPv6 地址不在网段内")
    void isInRange_ipv6OutsideSubnet() {
      assertThat(CidrUtils.isInRange("2001:db9::1", "2001:db8::/32")).isFalse();
    }
  }

  @Nested
  @DisplayName("整型转换")
  class IpConvertTest {

    @Test
    @DisplayName("ipToLong/longToIp: 往返一致")
    void ipToLong_roundTrip() {
      String ip = "192.168.1.100";
      long ipLong = CidrUtils.ipToLong(ip);
      assertThat(CidrUtils.longToIp(ipLong)).isEqualTo(ip);
    }

    @Test
    @DisplayName("ipToLong: 边界值 0.0.0.0")
    void ipToLong_minValue() {
      assertThat(CidrUtils.ipToLong("0.0.0.0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("ipToLong: 边界值 255.255.255.255")
    void ipToLong_maxValue() {
      assertThat(CidrUtils.ipToLong("255.255.255.255")).isEqualTo(0xFFFFFFFFL);
    }
  }

  @Nested
  @DisplayName("子网掩码与前缀")
  class NetmaskTest {

    @Test
    @DisplayName("getPrefixLength: 255.255.255.0 → 24")
    void getPrefixLength_classC() {
      assertThat(CidrUtils.getPrefixLength("255.255.255.0")).isEqualTo(24);
    }

    @Test
    @DisplayName("getNetmaskFromPrefix: 24 → 255.255.255.0")
    void getNetmaskFromPrefix_prefix24() {
      assertThat(CidrUtils.getNetmaskFromPrefix(24)).isEqualTo("255.255.255.0");
    }

    @Test
    @DisplayName("getNetworkAddress: 192.168.1.100/24 → 192.168.1.0")
    void getNetworkAddress_correct() {
      assertThat(CidrUtils.getNetworkAddress("192.168.1.100", 24)).isEqualTo("192.168.1.0");
    }

    @Test
    @DisplayName("getBroadcastAddress: 192.168.1.100/24 → 192.168.1.255")
    void getBroadcastAddress_correct() {
      assertThat(CidrUtils.getBroadcastAddress("192.168.1.100", 24)).isEqualTo("192.168.1.255");
    }
  }

  @Nested
  @DisplayName("缓存")
  class CacheTest {

    @Test
    @DisplayName("查询后缓存 size 增加")
    void cache_afterQuery() {
      CidrUtils.isInRange("192.168.1.1", "192.168.1.0/24");
      assertThat(CidrUtils.getCacheSize()).isGreaterThan(0);
    }

    @Test
    @DisplayName("clearCache 清空")
    void cache_clearCache() {
      CidrUtils.isInRange("192.168.1.1", "192.168.1.0/24");
      CidrUtils.clearCache();
      assertThat(CidrUtils.getCacheSize()).isEqualTo(0);
    }
  }
}
