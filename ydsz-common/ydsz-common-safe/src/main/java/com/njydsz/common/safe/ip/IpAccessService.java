package com.njydsz.common.safe.ip;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.config.IpAccessProperties;

/**
 * IP 访问控制服务
 *
 * <p>提供 IP 黑白名单管理能力，支持：
 *
 * <ul>
 *   <li>CIDR 网段匹配（如 10.0.0.0/8、192.168.1.0/24）
 *   <li>Redis 持久化存储（实时生效，分布式共享）
 *   <li>本地缓存（降低 Redis 查询延迟）
 *   <li>静态名单（配置文件加载）
 *   <li>自动封禁/解封 API
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see IpAccessFilter
 */
public class IpAccessService {

  private static final Logger LOG = LoggerFactory.getLogger(IpAccessService.class);

  private static final String BLACKLIST_SUFFIX = "blacklist";
  private static final String WHITELIST_SUFFIX = "whitelist";

  private final IpAccessProperties properties;
  private final RedisStringOps redisStringOps;

  private final Cache<String, Boolean> blacklistCache;
  private final List<CidrBlock> staticBlacklistCidrs = new ArrayList<>(4);
  private final List<CidrBlock> staticWhitelistCidrs = new ArrayList<>(4);

  /**
   * 构造 IP 访问控制服务
   *
   * @param properties IP 访问控制配置
   * @param redisStringOps Redis 字符串操作
   */
  public IpAccessService(IpAccessProperties properties, RedisStringOps redisStringOps) {
    this.properties = properties;
    this.redisStringOps = redisStringOps;

    // 构建本地缓存（对标 Caffeine 语义）
    this.blacklistCache =
        YdszCache.newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(properties.getLocalCacheSize())
            .expireAfterWrite(properties.getLocalCacheTtlSeconds(), TimeUnit.SECONDS)
            .build();

    // 解析静态黑白名单 CIDR
    for (String cidr : properties.getStaticBlacklist()) {
      CidrBlock block = CidrBlock.parse(cidr);
      if (block != null) {
        staticBlacklistCidrs.add(block);
      }
    }
    for (String cidr : properties.getStaticWhitelist()) {
      CidrBlock block = CidrBlock.parse(cidr);
      if (block != null) {
        staticWhitelistCidrs.add(block);
      }
    }

    LOG.info(
        "[IpAccessService] 初始化完成：mode={}, staticBlacklist={}, staticWhitelist={}",
        properties.getMode(),
        staticBlacklistCidrs.size(),
        staticWhitelistCidrs.size());
  }

  /**
   * 判断 IP 是否被允许访问。
   *
   * <p>检查流程：
   *
   * <ol>
   *   <li>白名单优先级最高：白名单中的 IP 直接放行
   *   <li>黑名单检查：黑名单中的 IP 拒绝访问
   *   <li>不在任何名单中的 IP 默认放行
   * </ol>
   *
   * @param ip IP 地址
   * @return true 表示允许访问，false 表示拒绝
   */
  public boolean isAllowed(String ip) {
    if (!properties.isEnabled()) {
      return true;
    }
    if (ip == null || ip.isEmpty()) {
      return false;
    }

    // 静态白名单优先
    if (matchesAnyCidr(ip, staticWhitelistCidrs)) {
      return true;
    }

    // 静态黑名单
    if (matchesAnyCidr(ip, staticBlacklistCidrs)) {
      return false;
    }

    // 动态 Redis 黑名单
    if (isBlacklisted(ip)) {
      return false;
    }

    // 白名单模式下：不在白名单中则拒绝
    if (properties.getMode() == IpAccessProperties.AccessMode.WHITELIST
        && !isWhitelisted(ip)) {
      return false;
    }

    return true;
  }

  /**
   * 判断 IP 是否在动态黑名单中（查询本地缓存 → Redis）。
   *
   * @param ip IP 地址
   * @return true 表示在黑名单中
   */
  public boolean isBlacklisted(String ip) {
    Boolean cached = blacklistCache.getIfPresent(ip);
    if (cached != null) {
      return cached;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    boolean blocked = redisStringOps.get(key) != null;
    blacklistCache.put(ip, blocked);
    return blocked;
  }

  /**
   * 判断 IP 是否在动态白名单中。
   *
   * @param ip IP 地址
   * @return true 表示在白名单中
   */
  public boolean isWhitelisted(String ip) {
    String key = properties.getRedisKeyPrefix() + WHITELIST_SUFFIX + ":" + ip;
    return redisStringOps.get(key) != null;
  }

  /**
   * 将 IP 加入动态黑名单。
   *
   * @param ip IP 地址
   * @param duration 封禁时长
   * @param unit 封禁时长单位
   */
  public void blockIp(String ip, long duration, TimeUnit unit) {
    if (ip == null || ip.isEmpty()) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    redisStringOps.set(key, "1", duration, unit);
    blacklistCache.put(ip, true);
    LOG.info("[IpAccessService] IP 已封禁：ip={}, duration={} {}", ip, duration, unit.name());
  }

  /**
   * 将 IP 加入默认时长的动态黑名单。
   *
   * @param ip IP 地址
   */
  public void blockIp(String ip) {
    blockIp(ip, properties.getDefaultBlockSeconds(), TimeUnit.SECONDS);
  }

  /**
   * 将 IP 从动态黑名单中移除。
   *
   * @param ip IP 地址
   */
  public void unblockIp(String ip) {
    if (ip == null || ip.isEmpty()) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    redisStringOps.delete(key);
    blacklistCache.remove(ip);
    LOG.info("[IpAccessService] IP 已解封：ip={}", ip);
  }

  /**
   * 将 IP 加入动态白名单。
   *
   * @param ip IP 地址
   * @param durationSeconds 有效时长（秒），0 表示永不过期
   */
  public void whitelistIp(String ip, long durationSeconds) {
    if (ip == null || ip.isEmpty()) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + WHITELIST_SUFFIX + ":" + ip;
    if (durationSeconds > 0) {
      redisStringOps.set(key, "1", durationSeconds, TimeUnit.SECONDS);
    } else {
      redisStringOps.set(key, "1");
    }
    LOG.info("[IpAccessService] IP 已加白名单：ip={}, ttl={}s", ip, durationSeconds);
  }

  /**
   * 检查 IP 是否匹配任一 CIDR 网段。
   *
   * @param ip IP 地址
   * @param cidrs CIDR 网段列表
   * @return 匹配成功返回 true
   */
  private boolean matchesAnyCidr(String ip, List<CidrBlock> cidrs) {
    long ipLong = ipToLong(ip);
    if (ipLong < 0) {
      return false;
    }
    for (CidrBlock cidr : cidrs) {
      if (cidr.contains(ipLong)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 将 IPv4 地址字符串转换为 long 数值。
   *
   * @param ip IPv4 地址
   * @return long 数值，解析失败返回 -1
   */
  private long ipToLong(String ip) {
    if (!StringUtils.hasText(ip)) {
      return -1;
    }
    String[] parts = ip.split("\\.");
    if (parts.length != 4) {
      return -1;
    }
    try {
      long result = 0;
      for (String part : parts) {
        int value = Integer.parseInt(part);
        if (value < 0 || value > 255) {
          return -1;
        }
        result = (result << 8) | value;
      }
      return result;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * CIDR 网段表示（支持 192.168.1.0/24、10.0.0.0/8 等）。
   */
  static final class CidrBlock {
    private final long network;
    private final long mask;

    CidrBlock(long network, long mask) {
      this.network = network;
      this.mask = mask;
    }

    /** 判断指定 IP（long 形式）是否在当前 CIDR 网段内 */
    boolean contains(long ip) {
      return (ip & mask) == (network & mask);
    }

    /**
     * 解析 CIDR 字符串（如 "192.168.1.0/24"）或单个 IP（如 "10.0.0.1"）。
     *
     * @param cidr CIDR 字符串或 IP 地址
     * @return CidrBlock 实例，解析失败返回 null
     */
    static CidrBlock parse(String cidr) {
      if (cidr == null || cidr.isBlank()) {
        return null;
      }
      cidr = cidr.trim();
      String addr;
      int prefixLen;
      int slashIdx = cidr.indexOf('/');
      if (slashIdx < 0) {
        addr = cidr;
        prefixLen = 32;
      } else {
        addr = cidr.substring(0, slashIdx).trim();
        try {
          prefixLen = Integer.parseInt(cidr.substring(slashIdx + 1).trim());
        } catch (NumberFormatException e) {
          return null;
        }
        if (prefixLen < 0 || prefixLen > 32) {
          return null;
        }
      }
      String[] parts = addr.split("\\.");
      if (parts.length != 4) {
        return null;
      }
      try {
        long ip = 0;
        for (String part : parts) {
          int value = Integer.parseInt(part);
          if (value < 0 || value > 255) {
            return null;
          }
          ip = (ip << 8) | value;
        }
        long mask = prefixLen == 0 ? 0L : (0xFFFFFFFFL << (32 - prefixLen)) & 0xFFFFFFFFL;
        return new CidrBlock(ip, mask);
      } catch (NumberFormatException e) {
        return null;
      }
    }
  }
}
