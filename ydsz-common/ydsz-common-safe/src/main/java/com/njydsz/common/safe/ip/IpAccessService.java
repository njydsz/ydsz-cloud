package com.njydsz.common.safe.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
 * @since 1.0.0
 * @see IpAccessFilter
 */
public class IpAccessService {

  private static final Logger LOG = LoggerFactory.getLogger(IpAccessService.class);

  private static final String BLACKLIST_SUFFIX = "blacklist";
  private static final String WHITELIST_SUFFIX = "whitelist";

  private final IpAccessProperties properties;
  private final RedisStringOps redisStringOps;

  private final Cache<String, Boolean> blacklistCache;
  private final List<CidrBlock> staticBlacklistCidrs = new ArrayList<>();
  private final List<CidrBlock> staticWhitelistCidrs = new ArrayList<>();

  /**
   * @param properties IP 访问控制配置
   * @param redisStringOps Redis String 操作
   */
  public IpAccessService(IpAccessProperties properties, RedisStringOps redisStringOps) {
    this.properties = properties;
    this.redisStringOps = redisStringOps;

    this.blacklistCache =
        YdszCache.<String, Boolean>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(properties.getLocalCacheTtlSeconds(), TimeUnit.SECONDS)
            .maximumSize(properties.getLocalCacheSize())
            .build();

    loadStaticList(properties.getStaticBlacklist(), staticBlacklistCidrs);
    loadStaticList(properties.getStaticWhitelist(), staticWhitelistCidrs);

    LOG.info(
        "IP 访问控制服务初始化: mode={}, staticBlacklist={}, staticWhitelist={}",
        properties.getMode(),
        staticBlacklistCidrs.size(),
        staticWhitelistCidrs.size());
  }

  /**
   * 检查 IP 是否允许访问
   *
   * <p>黑名单模式：IP 在黑名单中则拒绝；白名单模式：IP 不在白名单中则拒绝。 静态名单优先级最高（先检查），然后检查 Redis 动态名单。
   *
   * @param ip 客户端 IP
   * @return true 允许访问，false 拒绝
   */
  public boolean isAllowed(String ip) {
    if (!StringUtils.hasText(ip)) {
      return true;
    }

    for (CidrBlock cidr : staticWhitelistCidrs) {
      if (cidr.matches(ip)) {
        return true;
      }
    }

    for (CidrBlock cidr : staticBlacklistCidrs) {
      if (cidr.matches(ip)) {
        return properties.getMode() != IpAccessProperties.AccessMode.BLACKLIST;
      }
    }

    if (properties.getMode() == IpAccessProperties.AccessMode.BLACKLIST) {
      if (isInRedisBlacklist(ip)) {
        return false;
      }
      return true;
    } else {
      if (isInRedisWhitelist(ip)) {
        return true;
      }
      return false;
    }
  }

  /**
   * 将 IP 加入黑名单
   *
   * @param ip 要封禁的 IP
   * @param blockSeconds 封禁时长（秒）
   */
  public void block(String ip, long blockSeconds) {
    if (!StringUtils.hasText(ip)) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    redisStringOps.set(
        key, "1", blockSeconds > 0 ? blockSeconds : properties.getDefaultBlockSeconds());
    blacklistCache.put(ip, true);
    LOG.info(
        "IP {} 已加入黑名单，封禁 {} 秒",
        ip,
        blockSeconds > 0 ? blockSeconds : properties.getDefaultBlockSeconds());
  }

  /**
   * 将 IP 加入黑名单（使用默认封禁时长）
   *
   * @param ip 要封禁的 IP
   */
  public void block(String ip) {
    block(ip, properties.getDefaultBlockSeconds());
  }

  /**
   * 将 IP 从黑名单移除
   *
   * @param ip 要解封的 IP
   */
  public void unblock(String ip) {
    if (!StringUtils.hasText(ip)) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    redisStringOps.del(key);
    blacklistCache.invalidate(ip);
    LOG.info("IP {} 已从黑名单移除", ip);
  }

  /**
   * 将 IP 加入白名单
   *
   * @param ip 要加入白名单的 IP
   */
  public void whitelist(String ip) {
    if (!StringUtils.hasText(ip)) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + WHITELIST_SUFFIX + ":" + ip;
    redisStringOps.set(key, "1");
    LOG.info("IP {} 已加入白名单", ip);
  }

  /**
   * 将 IP 从白名单移除
   *
   * @param ip 要移除的 IP
   */
  public void unwhitelist(String ip) {
    if (!StringUtils.hasText(ip)) {
      return;
    }
    String key = properties.getRedisKeyPrefix() + WHITELIST_SUFFIX + ":" + ip;
    redisStringOps.del(key);
    LOG.info("IP {} 已从白名单移除", ip);
  }

  private boolean isInRedisBlacklist(String ip) {
    Boolean cached = blacklistCache.getIfPresent(ip);
    if (cached != null) {
      return cached;
    }
    String key = properties.getRedisKeyPrefix() + BLACKLIST_SUFFIX + ":" + ip;
    boolean blocked = redisStringOps.hasKey(key);
    blacklistCache.put(ip, blocked);
    return blocked;
  }

  private boolean isInRedisWhitelist(String ip) {
    String key = properties.getRedisKeyPrefix() + WHITELIST_SUFFIX + ":" + ip;
    return redisStringOps.hasKey(key);
  }

  private void loadStaticList(List<String> entries, List<CidrBlock> target) {
    if (entries == null || entries.isEmpty()) {
      return;
    }
    for (String entry : entries) {
      String trimmed = entry.trim();
      if (!StringUtils.hasText(trimmed)) {
        continue;
      }
      try {
        target.add(new CidrBlock(trimmed));
      } catch (Exception e) {
        LOG.warn("无法解析 CIDR 规则: {} - {}", trimmed, e.getMessage());
      }
    }
  }

  /**
   * CIDR 网段匹配器
   *
   * <p>支持 IPv4 CIDR 表示法（如 192.168.1.0/24），也支持单个 IP（如 192.168.1.100）。
   */
  static class CidrBlock {

    private final byte[] networkAddress;
    private final int prefixLength;

    CidrBlock(String cidr) throws UnknownHostException {
      int slashIndex = cidr.indexOf('/');
      String ipPart = slashIndex >= 0 ? cidr.substring(0, slashIndex) : cidr;
      String prefixPart = slashIndex >= 0 ? cidr.substring(slashIndex + 1) : "32";

      this.networkAddress = InetAddress.getByName(ipPart).getAddress();
      this.prefixLength = Integer.parseInt(prefixPart);
    }

    boolean matches(String ip) {
      try {
        byte[] ipBytes = InetAddress.getByName(ip).getAddress();
        if (ipBytes.length != networkAddress.length) {
          return false;
        }
        int fullBytes = prefixLength / 8;
        int partialBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
          if (ipBytes[i] != networkAddress[i]) {
            return false;
          }
        }
        if (partialBits > 0 && fullBytes < ipBytes.length) {
          int mask = 0xFF << (8 - partialBits);
          if ((ipBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
            return false;
          }
        }
        return true;
      } catch (UnknownHostException e) {
        return false;
      }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < networkAddress.length; i++) {
        if (i > 0) {
          sb.append('.');
        }
        sb.append(networkAddress[i] & 0xFF);
      }
      sb.append('/').append(prefixLength);
      return sb.toString();
    }
  }
}
