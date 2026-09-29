package com.njydsz.gateway.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * Gateway 模块缓存键构造器（P1-1 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>为 Gateway 模块的本地缓存实例（YdszCache）和分布式缓存键提供租户感知的统一生成能力，
 * 替代原来自建的字符串拼接（{@code "ydsz:ip:blacklist:"}、{@code "gateway:jwt:invalidate"} 等）。
 *
 * <p><b>统一格式：</b>{@code ydsz:{tenantId}:gateway:{entity}:{id}}
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Component("gatewayCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：网关 */
  private static final String MODULE = "gateway";

  /** 构造 Gateway 模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== IP 黑名单 key ==============================

  /**
   * 生成「IP 黑名单」缓存键（L1 YdszCache / L2 Redis 共用）。
   *
   * <p>格式：{@code ydsz:{tenantId}:gateway:ip:blacklist:{clientIp}}
   *
   * @param clientIp 客户端 IP
   * @return 租户隔离的缓存键
   */
  public String ipBlacklist(String clientIp) {
    return buildKey("ip:blacklist", clientIp);
  }

  // ============================== JWT 验证缓存 key ==============================

  /**
   * 生成「JWT 验证结果」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:gateway:jwt:validation:{tokenFingerprint}}
   *
   * @param tokenFingerprint Token 指纹（前 16 位或 SHA-256 摘要）
   * @return 租户隔离的缓存键
   */
  public String jwtValidation(String tokenFingerprint) {
    return buildKey("jwt:validation", tokenFingerprint);
  }

  /**
   * 生成「JWT 失效广播」Redis Pub/Sub channel 名。
   *
   * <p>格式：{@code ydsz:{tenantId}:gateway:jwt:invalidate}
   *
   * @return 租户隔离的 channel 名
   */
  public String jwtInvalidateChannel() {
    return buildKey("jwt:invalidate", "");
  }

  // ============================== 本地限流 key ==============================

  /**
   * 生成「本地令牌桶限流」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:gateway:ratelimit:{bucketKey}}
   *
   * @param bucketKey 限流桶标识（IP / userId / API 路径）
   * @return 租户隔离的缓存键
   */
  public String rateLimitBucket(String bucketKey) {
    return buildKey("ratelimit", bucketKey);
  }

  // ============================== 灰度路由元数据 key ==============================

  /**
   * 生成「灰度路由别名表」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:gateway:gray:service-list:{serviceName}}
   *
   * @param serviceName 后端服务名
   * @return 租户隔离的缓存键
   */
  public String grayServiceList(String serviceName) {
    return buildKey("gray:service-list", serviceName);
  }
}
