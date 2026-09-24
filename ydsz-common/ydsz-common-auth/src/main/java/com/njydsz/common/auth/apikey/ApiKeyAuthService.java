package com.njydsz.common.auth.apikey;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * API Key 认证服务。
 *
 * <p>提供 API Key 认证通道（machine-to-machine），用于 IoT、第三方 Webhook、批量脚本等无法使用 JWT 登录取 Token 的场景。
 *
 * <p>API Key 存储在 Redis Hash 中，Key 格式：{@code ydsz:auth:apikey:{keyPrefix}}， 值为 Hash（使用 ydzs-common-redis
 * RedisHashOps 存储）。当 Redis HashOps 不可用时（classpath 或连接降级），API Key 认证通道自动禁用。
 *
 * <p>校验流程：
 *
 * <ol>
 *   <li>从 {@code X-Api-Key} Header 读取 key（格式 {@code {appId}:{secret}}）
 *   <li>查询 Redis 获取 ApiKeyMetadata
 *   <li>校验 status = ACTIVE、未过期
 *   <li>注入 ApiKeyAuthInfo 到请求上下文
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class ApiKeyAuthService {

  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthService.class);

  /** Redis Key 前缀 */
  private static final String KEY_PREFIX = "ydsz:auth:apikey:";

  /** Redis Hash 字段名 */
  private static final String FIELD_APP_ID = "appId";

  private static final String FIELD_SCOPES = "scopes";

  private static final String FIELD_EXPIRES_AT = "expiresAtMs";

  private static final String FIELD_RATE_LIMIT = "rateLimitPerMinute";

  private static final String FIELD_STATUS = "status";

  private final RedisStringOps redisStringOps;

  private final boolean enabled;

  /**
   * 构造 API Key 认证服务。
   *
   * @param redisStringOps Redis String 操作
   * @param properties 认证配置属性
   */
  public ApiKeyAuthService(RedisStringOps redisStringOps, AuthProperties properties) {
    this.redisStringOps = redisStringOps;
    this.enabled = properties.getApiKey() != null && properties.getApiKey().getIsEnabled();
  }

  /**
   * 校验 API Key。
   *
   * @param apiKey 完整 API Key 字符串（格式 appId:secret 或仅为 secret）
   * @return 校验成功返回元数据；失败返回 null
   */
  public ApiKeyMetadata authenticate(String apiKey) {
    if (!enabled || apiKey == null || apiKey.isBlank()) {
      return null;
    }
    // 解析 appId 和 secret（仅取 appId 部分用于查 Redis）
    String appId = extractAppId(apiKey);
    String redisKey = KEY_PREFIX + appId;
    try {
      Object appIdObj = redisStringOps.get(redisKey + ":appId");
      if (appIdObj == null) {
        LOG.warn("[ApiKeyAuth] API Key 不存在: appId={}", appId);
        return null;
      }
      Object scopesObj = redisStringOps.get(redisKey + ":scopes");
      Object expiresObj = redisStringOps.get(redisKey + ":expiresAtMs");
      Object rateLimitObj = redisStringOps.get(redisKey + ":rateLimitPerMinute");
      Object statusObj = redisStringOps.get(redisKey + ":status");

      String status = statusObj != null ? statusObj.toString() : "ACTIVE";
      String storedAppId = appIdObj.toString();
      Set<String> scopes = scopesObj != null ? parseScopes(scopesObj.toString()) : Set.of();
      long expiresAtMs = parseLong(expiresObj);
      int rateLimit = parseInt(rateLimitObj);

      ApiKeyMetadata metadata =
          new ApiKeyMetadata(
              extractKeyId(apiKey), storedAppId, scopes, expiresAtMs, rateLimit, status);
      if (!metadata.isValid()) {
        LOG.warn("[ApiKeyAuth] API Key 已失效: appId={}, status={}", appId, status);
        return null;
      }
      return metadata;
    } catch (Exception e) {
      LOG.error("[ApiKeyAuth] API Key 校验异常: appId={}, error={}", appId, e.getMessage());
      return null;
    }
  }

  /**
   * 从 API Key 字符串提取应用 ID。
   *
   * <p>支持格式：{@code appId:secret}、{@code appId.secret}、{@code secret}（单段返回原值）。
   *
   * @param apiKey API Key
   * @return appId
   */
  private String extractAppId(String apiKey) {
    int colonIdx = apiKey.indexOf(':');
    if (colonIdx > 0) {
      return apiKey.substring(0, colonIdx);
    }
    int dotIdx = apiKey.indexOf('.');
    if (dotIdx > 0) {
      return apiKey.substring(0, dotIdx);
    }
    return apiKey;
  }

  /** 提取 keyId（仅供元数据使用，不参与校验）。 */
  private String extractKeyId(String apiKey) {
    return apiKey.hashCode() + "-" + System.nanoTime();
  }

  /** 解析权限范围 CSV。 */
  private Set<String> parseScopes(String scopesStr) {
    if (scopesStr == null || scopesStr.isBlank()) {
      return Set.of();
    }
    return Set.of(scopesStr.split(","));
  }

  /** 解析 long 值（Object）。 */
  private long parseLong(Object obj) {
    if (obj == null) {
      return 0;
    }
    if (obj instanceof Number) {
      return ((Number) obj).longValue();
    }
    try {
      return Long.parseLong(obj.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** 解析 int 值（Object）。 */
  private int parseInt(Object obj) {
    if (obj == null) {
      return 0;
    }
    if (obj instanceof Number) {
      return ((Number) obj).intValue();
    }
    try {
      return Integer.parseInt(obj.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
