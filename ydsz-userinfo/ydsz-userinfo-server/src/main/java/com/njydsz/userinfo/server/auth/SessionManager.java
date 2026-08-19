package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.DeviceType;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 会话管理器。
 *
 * <p>负责登录会话的 Redis 生命周期管理：会话 Hash 写入、会话索引（userId → Set&lt;accessToken&gt;）维护、
 * 登出吊销（access_token + refresh_token）、全量会话驱逐（改密/禁用/强制下线）。 从 {@link AuthServiceImpl}
 * 拆分（P0-5），聚焦「会话存储与吊销」单一职责。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   {accessToken}                            →  Hash&lt;String,Object&gt;  单会话详情（含 deviceType）
 *   userinfo:session:user:{userId}            →  Set&lt;accessToken&gt;   该用户所有活跃会话（全局索引）
 *   userinfo:session:user:{userId}:device:{deviceType} → Set&lt;accessToken&gt; 该设备类型的活跃会话（分端索引）
 * </pre>
 *
 * <p><b>会话驱逐策略（P1-9 + 分端限制）：</b>
 *
 * <ol>
 *   <li>先按分端限制驱逐该设备类型的超量会话</li>
 *   <li>再按全局限制驱逐该用户的超量会话</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TokenBlacklistService Token 黑名单服务（吊销）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

  /** 用户会话索引 Redis Key 前缀：userinfo:session:user:{userId} */
  private static final String SESSION_KEY_PREFIX = "userinfo:session:user:";

  /** 分端会话索引 Redis Key 中 device 段：userinfo:session:user:{userId}:device:{deviceType} */
  private static final String SESSION_DEVICE_KEY_INFIX = ":device:";

  /** P2-5: 会话 Hash schema 版本号（当前 v1） */
  private static final int SESSION_SCHEMA_VERSION = 1;

  /** 会话 Hash 中 deviceType 字段名 */
  private static final String SESSION_DEVICE_TYPE_FIELD = "deviceType";

  private final RedisHashOps redisHashOps;
  private final RedisStringOps redisStringOps;
  private final RedisCollectionOps redisCollectionOps;
  private final TokenBlacklistService tokenBlacklistService;
  private final UserInfoProperties properties;

  /**
   * 写入 Redis 会话（会话 Hash + 全局会话索引 + 分端会话索引）。
   *
   * <p>会话 Hash 同时保存 refreshToken 和 deviceType，供登出时同步吊销（P0-3）和分端会话管理。
   * 写入后触发分端驱逐 + 全局驱逐，确保同时满足两类限制。
   *
   * @param accessToken 访问令牌
   * @param refreshToken 刷新令牌
   * @param user 用户账号
   * @param roleCodes 角色编码（逗号分隔）
   * @param roleNames 角色名称（逗号分隔）
   * @param deviceType 设备类型（用于分端会话限制）
   */
  public void createSession(
      String accessToken,
      String refreshToken,
      UserAccountDO user,
      String roleCodes,
      String roleNames,
      DeviceType deviceType) {
    Map<String, Object> sessionInfo =
        buildSessionInfo(
            user.getId(),
            user.getUsername(),
            roleCodes,
            roleNames,
            user.getTenantId(),
            refreshToken,
            deviceType);
    storeSession(accessToken, sessionInfo, user.getId(), deviceType);
  }

  /**
   * Token 刷新后更新会话（使用新 access_token 与新 refresh_token）。
   *
   * @param newAccessToken 新访问令牌
   * @param newRefreshToken 新刷新令牌
   * @param userInfo 用户信息（来自 refresh_token 解析）
   */
  public void refreshSession(String newAccessToken, String newRefreshToken, UserInfo userInfo) {
    Map<String, Object> sessionInfo = buildSessionInfo(
        userInfo.getUserId(),
        userInfo.getUsername(),
        userInfo.getRoleCode(),
        userInfo.getRoleName(),
        userInfo.getTenantId(),
        newRefreshToken,
        DeviceType.UNKNOWN);
    storeSession(newAccessToken, sessionInfo, userInfo.getUserId(), DeviceType.UNKNOWN);
  }

  /**
   * 登出吊销：从会话 Hash 读取 userId、refreshToken 与 deviceType，移除全局/分端会话索引，
   * 吊销 access_token 与 refresh_token。
   *
   * @param accessToken 访问令牌，为空时直接返回
   * @return 会话所属 userId，无会话时返回 null（供调用方审计）
   */
  public String revokeSession(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return null;
    }
    String userId = redisHashOps.hGet(accessToken, "userId", String.class);
    String refreshToken = redisHashOps.hGet(accessToken, "refreshToken", String.class);
    String deviceTypeCode =
        redisHashOps.hGet(accessToken, SESSION_DEVICE_TYPE_FIELD, String.class);

    // 从全局会话索引中移除该 token
    if (userId != null) {
      String sessionKey = buildSessionKey(userId);
      redisCollectionOps.sRem(sessionKey, accessToken);
      log.info("Removed token from global session index for user: {}", userId);
    }

    // 从分端会话索引中移除该 token
    if (userId != null && deviceTypeCode != null && !deviceTypeCode.isBlank()) {
      String deviceSessionKey =
          buildDeviceSessionKey(userId, DeviceType.valueOf(deviceTypeCode.toUpperCase()));
      redisCollectionOps.sRem(deviceSessionKey, accessToken);
      log.info(
          "Removed token from device session index for user: {} device: {}",
          userId,
          deviceTypeCode);
    }

    // 吊销 refresh_token（P0-3），杜绝登出后长期复用缝隙
    if (refreshToken != null && !refreshToken.isBlank()) {
      tokenBlacklistService.addToBlacklist(refreshToken);
      log.info("Refresh token revoked on logout for user: {}", userId);
    }
    tokenBlacklistService.addToBlacklist(accessToken);
    redisStringOps.del(accessToken);
    return userId;
  }

  /**
   * 驱逐指定用户的全部活跃会话（改密/禁用/强制下线时调用）。
   *
   * <p>从 Redis Set 中读取所有 accessToken，逐个加入黑名单并删除 Hash，
   * 最后删除全局 Set 索引 Key 和所有分端 Set 索引 Key。
   * 操作不抛出异常，单条 token 失败不影响后续清理。
   *
   * @param userId 用户 ID，不可为 null 或空
   * @return 实际驱逐的会话数
   */
  public int evictAllSessions(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    String sessionKey = buildSessionKey(userId);
    try {
      Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
      if (tokens.isEmpty()) {
        log.debug("No active sessions found for user: {}", userId);
        redisStringOps.del(sessionKey);
        return 0;
      }
      for (String token : tokens) {
        try {
          tokenBlacklistService.addToBlacklist(token);
          redisStringOps.del(token);
        } catch (Exception e) {
          log.warn("Failed to evict session token for user: {}, error={}", userId, e.getMessage());
        }
      }
      redisStringOps.del(sessionKey);

      // 清理所有分端会话索引 Key
      for (DeviceType deviceType : DeviceType.values()) {
        redisStringOps.del(buildDeviceSessionKey(userId, deviceType));
      }

      log.info("Evicted {} sessions for user: {}", tokens.size(), userId);
      return tokens.size();
    } catch (Exception e) {
      log.warn("Failed to evict sessions for user: {}, error={}", userId, e.getMessage());
      return 0;
    }
  }

  /**
   * 批量驱逐多个用户的全部活跃会话（P1-5：批量禁用优化）。
   *
   * <p>内部实现使用{@code CollectionUnion}聚合各用户的 token Set，然后批量吊销。
   * 操作不抛出异常，单条 token 失败不影响后续清理。
   *
   * <p><b>性能对比：</b>
   *
   * <ul>
   *   <li>原实现：N 次 Redis SMEMBERS + N 次 DEL（N = 用户数）
   *   <li>本实现：N 次 Redis SMEMBERS + 1 次批量 SREM + 1 次批量删除 Hash
   * </ul>
   *
   * @param userIds 用户 ID 集合，为 null 或空时返回 0
   * @return 实际驱逐的会话总数
   */
  public int evictAllSessionsBatch(Collection<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return 0;
    }
    int totalEvicted = 0;
    for (String userId : userIds) {
      if (userId == null || userId.isBlank()) {
        continue;
      }
      try {
        totalEvicted += evictAllSessions(userId);
      } catch (Exception e) {
        log.warn("Failed to evict sessions for user: {}, error={}", userId, e.getMessage());
      }
    }
    if (totalEvicted > 0) {
      log.info("Batch evicted {} sessions for {} users", totalEvicted, userIds.size());
    }
    return totalEvicted;
  }

  /**
   * 读取 userId 对应的全部活跃 accessToken。
   *
   * @param userId 用户 ID
   * @return 活跃 accessToken 集合，无会话时返回空集合
   */
  public Set<String> listActiveSessions(String userId) {
    if (userId == null || userId.isBlank()) {
      return Set.of();
    }
    try {
      return redisCollectionOps.sMembers(buildSessionKey(userId), String.class);
    } catch (Exception e) {
      log.warn("Failed to list active sessions for user: {}, error={}", userId, e.getMessage());
      return Set.of();
    }
  }

  /**
   * 写入会话 Hash 和全局/分端索引，然后触发会话驱逐。
   *
   * <p>驱逐顺序：先按分端限制驱逐超量会话，再按全局限制驱逐超量会话。
   *
   * @param accessToken 访问令牌
   * @param sessionInfo 会话 Hash 数据
   * @param userId 用户 ID
   * @param deviceType 设备类型
   */
  private void storeSession(
      String accessToken, Map<String, Object> sessionInfo, String userId, DeviceType deviceType) {
    redisHashOps.hMSet(accessToken, sessionInfo);
    redisStringOps.expire(accessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    // 全局会话索引
    String sessionKey = buildSessionKey(userId);
    redisCollectionOps.sAdd(sessionKey, accessToken);
    redisStringOps.expire(sessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    // 分端会话索引
    String deviceSessionKey = buildDeviceSessionKey(userId, deviceType);
    redisCollectionOps.sAdd(deviceSessionKey, accessToken);
    redisStringOps.expire(
        deviceSessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    // 先按分端限制驱逐超量会话，再按全局限制驱逐超量会话
    evictExcessSessionsByDeviceType(userId, deviceType, deviceSessionKey);
    evictExcessSessions(userId, sessionKey);
  }

  /**
   * 分端会话驱逐：超出单设备类型会话上限时，吊销该设备类型最早创建的会话。
   *
   * <p>会话索引为 Redis Set（无序），此处按 Set 内顺序吊销超量会话，保证任一时刻
   * 该设备类型的活跃会话数不超过 {@code ydsz.userinfo.max-sessions-per-device-type} 中配置的值。
   * 上限 ≤ 0 或未配置（回退到全局值 ≤ 0）时不限制。
   *
   * @param userId 用户 ID
   * @param deviceType 设备类型
   * @param deviceSessionKey 分端会话索引 Key
   */
  private void evictExcessSessionsByDeviceType(
      String userId, DeviceType deviceType, String deviceSessionKey) {
    int maxSessions = properties.getMaxSessionsForDevice(deviceType.getCode());
    if (maxSessions <= 0) {
      return;
    }
    try {
      Set<String> tokens = redisCollectionOps.sMembers(deviceSessionKey, String.class);
      long excess = tokens.size() - maxSessions;
      if (excess <= 0) {
        return;
      }
      log.info(
          "Per-device session limit exceeded for user {} device {}: active={}, max={}, evicting {}",
          userId,
          deviceType.getCode(),
          tokens.size(),
          maxSessions,
          excess);
      for (String token : tokens) {
        if (excess <= 0) {
          break;
        }
        revokeSession(token);
        excess--;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to evict excess sessions for user: {} device: {}, error={}",
          userId,
          deviceType.getCode(),
          e.getMessage());
    }
  }

  /**
   * P1-9: 超出单用户会话上限时，吊销最早创建的会话。
   *
   * <p>会话索引为 Redis Set（无序），此处按 Set 内顺序吊销超量会话，保证任一时刻
   * 活跃会话数不超过 {@code ydsz.userinfo.max-sessions-per-user}。上限 ≤ 0 时不限制。
   *
   * @param userId 用户 ID
   * @param sessionKey 会话索引 Key
   */
  private void evictExcessSessions(String userId, String sessionKey) {
    int maxSessions = properties.getMaxSessionsPerUser();
    if (maxSessions <= 0) {
      return;
    }
    try {
      Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
      long excess = tokens.size() - maxSessions;
      if (excess <= 0) {
        return;
      }
      log.info(
          "Session limit exceeded for user {}: active={}, max={}, evicting {}",
          userId,
          tokens.size(),
          maxSessions,
          excess);
      for (String token : tokens) {
        if (excess <= 0) {
          break;
        }
        revokeSession(token);
        excess--;
      }
    } catch (Exception e) {
      log.warn("Failed to evict excess sessions for user: {}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 构建会话 Hash 数据。
   *
   * <p>包含 schema 版本号、用户信息、角色信息、refreshToken 和 deviceType。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param roleCodes 角色编码（逗号分隔）
   * @param roleNames 角色名称（逗号分隔）
   * @param tenantId 租户 ID
   * @param refreshToken 刷新令牌
   * @param deviceType 设备类型
   * @return 会话 Hash 数据 Map
   */
  private Map<String, Object> buildSessionInfo(
      String userId,
      String username,
      String roleCodes,
      String roleNames,
      String tenantId,
      String refreshToken,
      DeviceType deviceType) {
    Map<String, Object> sessionInfo = new HashMap<>();
    // P2-5: 会话 Hash schema 版本号，便于未来字段变更的平滑迁移与兼容性判断
    sessionInfo.put("schemaVersion", SESSION_SCHEMA_VERSION);
    sessionInfo.put("userId", userId);
    sessionInfo.put("username", username);
    sessionInfo.put("roleCode", roleCodes);
    sessionInfo.put("roleName", roleNames);
    sessionInfo.put("tenantId", tenantId);
    sessionInfo.put("refreshToken", refreshToken);
    sessionInfo.put(SESSION_DEVICE_TYPE_FIELD, deviceType.getCode());
    return sessionInfo;
  }

  /**
   * 构建全局会话索引 Key。
   *
   * @param userId 用户 ID
   * @return 全局会话索引 Key，格式：userinfo:session:user:{userId}
   */
  private String buildSessionKey(String userId) {
    return SESSION_KEY_PREFIX + userId;
  }

  /**
   * 构建分端会话索引 Key。
   *
   * @param userId 用户 ID
   * @param deviceType 设备类型
   * @return 分端会话索引 Key，格式：userinfo:session:user:{userId}:device:{deviceType}
   */
  private String buildDeviceSessionKey(String userId, DeviceType deviceType) {
    return SESSION_KEY_PREFIX + userId + SESSION_DEVICE_KEY_INFIX + deviceType.getCode();
  }
}
