package com.njydsz.common.util.ip;

import com.njydsz.common.util.string.StringUtils;
import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * CIDR 网段计算工具类。
 *
 * <p>提供 IPv4/IPv6 的 CIDR 网段判断、子网掩码转换、网络地址和广播地址计算。
 *
 * <p>自 1.4.0 起从原 {@code IpAddrUtils} 拆分为独立类，聚焦于 CIDR 网段运算。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public final class CidrUtils {

  private CidrUtils() {
    throw new UnsupportedOperationException(
        "CidrUtils is a utility class and cannot be instantiated");
  }

  /** 缓存最大条目数 */
  private static final int MAX_CACHE_SIZE = 1024;

  /** IP 范围判断缓存（ip_cidr -> isInRange 结果），LRU 淘汰，避免"满即全清"导致的命中率抖动 */
  private static final Map<String, Boolean> RANGE_CACHE =
      Collections.synchronizedMap(
          new LinkedHashMap<String, Boolean>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
              return size() > MAX_CACHE_SIZE;
            }
          });

  /**
   * 判断 IP 是否在 CIDR 网段内。
   *
   * <p>自动识别 IPv4 / IPv6 并分派到对应实现；参数为空、CIDR 格式非法 或解析异常时统一返回 {@code false}（宽松失败，不影响调用方主流程）。
   *
   * @param ip 待判断的 IP 地址
   * @param cidr CIDR 网段（如 {@code 192.168.1.0/24} 或 {@code 2001:db8::/32}）
   * @return {@code true} 表示 IP 在网段内；非法输入返回 {@code false}
   */
  public static boolean isInRange(String ip, String cidr) {
    if (StringUtils.isEmpty(ip) || StringUtils.isEmpty(cidr)) {
      return false;
    }
    try {
      String[] parts = cidr.split("/");
      if (parts.length != 2) {
        return false;
      }
      String networkIp = parts[0];
      int prefix = Integer.parseInt(parts[1]);

      if (IpValidator.validIpv4(ip) && IpValidator.validIpv4(networkIp)) {
        return isIpv4InRange(ip, networkIp, prefix);
      } else if (IpValidator.validIpv6(ip) && IpValidator.validIpv6(networkIp)) {
        return isIpv6InRange(ip, networkIp, prefix);
      }
      return false;
    } catch (Exception e) {
      log.warn("IP range check failed for ip: {}, cidr: {}", ip, cidr, e);
      return false;
    }
  }

  /**
   * 判断 IPv4 是否在网段内（基于整型掩码比较）。
   *
   * @param ip 待判断的 IPv4 地址
   * @param networkIp 网段起始地址（网络地址）
   * @param prefix 前缀长度 [0, 32]
   * @return {@code true} 表示在网段内；解析异常返回 {@code false}
   */
  public static boolean isIpv4InRange(String ip, String networkIp, int prefix) {
    String cacheKey = ip + "_" + networkIp + "_" + prefix;
    Boolean cached = RANGE_CACHE.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    boolean result = doIsIpv4InRange(ip, networkIp, prefix);
    putCache(cacheKey, result);
    return result;
  }

  /**
   * IPv4 网段判断的实际实现（不含缓存逻辑）。
   *
   * @param ip 待判断的 IPv4 地址
   * @param networkIp 网段起始地址（网络地址）
   * @param prefix 前缀长度 [0, 32]
   * @return {@code true} 表示在网段内
   */
  private static boolean doIsIpv4InRange(String ip, String networkIp, int prefix) {
    if (prefix < 0 || prefix > 32) {
      return false;
    }
    try {
      long ipLong = ipToLong(ip);
      long networkLong = ipToLong(networkIp);
      if (prefix == 0) {
        return true;
      }
      long mask = 0xFFFFFFFFL << (32 - prefix);
      return (ipLong & mask) == (networkLong & mask);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 判断 IPv6 是否在网段内（基于字节级掩码比较）。
   *
   * @param ip 待判断的 IPv6 地址
   * @param networkIp 网段起始地址（网络地址）
   * @param prefix 前缀长度 [0, 128]
   * @return {@code true} 表示在网段内；解析异常返回 {@code false}
   */
  public static boolean isIpv6InRange(String ip, String networkIp, int prefix) {
    String cacheKey = ip + "_" + networkIp + "_" + prefix;
    Boolean cached = RANGE_CACHE.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    boolean result = doIsIpv6InRange(ip, networkIp, prefix);
    putCache(cacheKey, result);
    return result;
  }

  /**
   * IPv6 网段判断的实际实现（不含缓存逻辑）。
   *
   * @param ip 待判断的 IPv6 地址
   * @param networkIp 网段起始地址（网络地址）
   * @param prefix 前缀长度 [0, 128]
   * @return {@code true} 表示在网段内
   */
  private static boolean doIsIpv6InRange(String ip, String networkIp, int prefix) {
    if (!IpValidator.validIpv6(ip) || !IpValidator.validIpv6(networkIp)) {
      return false;
    }
    try {
      byte[] ipBytes = InetAddress.getByName(ip).getAddress();
      byte[] networkBytes = InetAddress.getByName(networkIp).getAddress();

      for (int i = 0; i < ipBytes.length; i++) {
        int bitsToCheck = Math.min(8, prefix - (i * 8));
        if (bitsToCheck <= 0) break;

        int mask = 0xFF << (8 - bitsToCheck);
        if ((ipBytes[i] & mask) != (networkBytes[i] & mask)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 将 IPv4 地址转换为 32 位无符号长整型。
   *
   * @param ip 点分十进制 IPv4 地址，非空且合法
   * @return 对应的长整型值
   * @throws IllegalArgumentException 当 IP 格式非法时
   */
  public static long ipToLong(String ip) {
    if (!IpValidator.validIpv4(ip)) {
      throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
    }
    String[] parts = ip.split("\\.");
    long result = 0;
    for (int i = 0; i < 4; i++) {
      result = (result << 8) | Integer.parseInt(parts[i]);
    }
    return result;
  }

  /**
   * 将 32 位长整型还原为点分十进制 IPv4 地址。
   *
   * @param ipLong IPv4 对应的长整型值
   * @return 点分十进制地址字符串
   */
  public static String longToIp(long ipLong) {
    return ((ipLong >> 24) & 0xFF)
        + "."
        + ((ipLong >> 16) & 0xFF)
        + "."
        + ((ipLong >> 8) & 0xFF)
        + "."
        + (ipLong & 0xFF);
  }

  /**
   * 从子网掩码反推前缀长度（CIDR prefix）。
   *
   * @param netmask 点分十进制子网掩码，非空且合法
   * @return 前缀长度（0~32）
   * @throws IllegalArgumentException 当掩码格式非法或不连续时
   */
  public static int getPrefixLength(String netmask) {
    if (!IpValidator.validIpv4(netmask)) {
      throw new IllegalArgumentException("Invalid netmask: " + netmask);
    }
    long mask = ipToLong(netmask) & 0xFFFFFFFFL;
    int prefix = 0;
    boolean foundZero = false;
    for (int i = 31; i >= 0; i--) {
      if ((mask & (1L << i)) != 0) {
        if (foundZero) {
          throw new IllegalArgumentException("Invalid netmask: " + netmask);
        }
        prefix++;
      } else {
        foundZero = true;
      }
    }
    return prefix;
  }

  /**
   * 由前缀长度生成对应的子网掩码。
   *
   * @param prefix CIDR 前缀长度（0~32）
   * @return 点分十进制子网掩码
   * @throws IllegalArgumentException 当前缀越界时
   */
  public static String getNetmaskFromPrefix(int prefix) {
    if (prefix < 0 || prefix > 32) {
      throw new IllegalArgumentException("Invalid prefix: " + prefix);
    }
    long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    return longToIp(mask);
  }

  /**
   * 计算 IP 所在子网的网络地址。
   *
   * @param ip IPv4 地址，非空且合法
   * @param prefix CIDR 前缀长度
   * @return 网络地址（点分十进制）
   * @throws IllegalArgumentException 当 IP 非法时
   */
  public static String getNetworkAddress(String ip, int prefix) {
    if (!IpValidator.validIpv4(ip)) {
      throw new IllegalArgumentException("Invalid IP: " + ip);
    }
    if (prefix < 0 || prefix > 32) {
      throw new IllegalArgumentException("Invalid prefix: " + prefix);
    }
    long ipLong = ipToLong(ip);
    long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    return longToIp(ipLong & mask);
  }

  private static void putCache(String key, Boolean value) {
    // LRU 由 LinkedHashMap.removeEldestEntry 自动淘汰，无需手动清理
    RANGE_CACHE.put(key, value);
  }

  /** 清除所有缓存。 */
  public static void clearCache() {
    RANGE_CACHE.clear();
  }

  /**
   * 获取当前缓存大小。
   *
   * @return 缓存条目数
   */
  public static int getCacheSize() {
    return RANGE_CACHE.size();
  }

  /**
   * 计算 IP 所在子网的广播地址。
   *
   * @param ip IPv4 地址，非空且合法
   * @param prefix CIDR 前缀长度
   * @return 广播地址（点分十进制）
   * @throws IllegalArgumentException 当 IP 非法时
   */
  public static String getBroadcastAddress(String ip, int prefix) {
    if (!IpValidator.validIpv4(ip)) {
      throw new IllegalArgumentException("Invalid IP: " + ip);
    }
    if (prefix < 0 || prefix > 32) {
      throw new IllegalArgumentException("Invalid prefix: " + prefix);
    }
    long ipLong = ipToLong(ip);
    long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    long broadcast = (ipLong & mask) | (~mask & 0xFFFFFFFFL);
    return longToIp(broadcast);
  }
}
