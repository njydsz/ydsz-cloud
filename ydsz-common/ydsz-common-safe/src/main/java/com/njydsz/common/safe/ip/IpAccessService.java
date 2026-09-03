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
}
