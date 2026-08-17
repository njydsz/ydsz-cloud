package com.njydsz.userinfo.server.auth;

import java.time.Duration;
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
import com.njydsz.userinfo.domain.entity.UserAccount;
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
 *   {accessToken}                 →  Hash&lt;String,Object&gt;  单会话详情（userId/roleCode/refreshToken 等）
 *   userinfo:session:user:{userId} →  Set&lt;accessToken&gt;   该用户所有活跃会话
 * </pre>
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

  private final RedisHashOps redisHashOps;
  private final RedisStringOps redisStringOps;
  private final RedisCollectionOps redisCollectionOps;
  private final TokenBlacklistService tokenBlacklistService;
  private final UserInfoProperties properties;

  /**
   * 写入 Redis 会话（会话 Hash + 会话索引）。
   *
   * <p>会话 Hash 同时保存 refreshToken，供登出时同步吊销（P0-3）。
   *
   * @param accessToken 访问令牌
   * @param refreshToken 刷新令牌
   * @param user 用户账号
   * @param roleCodes 角色编码（逗号分隔）
   * @param roleNames 角色名称（逗号分隔）
   */
  public void createSession(
      String accessToken, String refreshToken, UserAccount user, String roleCodes,
      String roleNames) {
    Map<String, Object> sessionInfo = buildSessionInfo(
        user.getId(), user.getUsername(), roleCodes, roleNames, user.getTenantId(), refreshToken);
    storeSession(accessToken, sessionInfo, user.getId());
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
        userInfo.getUserId(), userInfo.getUsername(), userInfo.getRoleCode(),
        userInfo.getRoleName(), userInfo.getTenantId(), newRefreshToken);
    storeSession(newAccessToken, sessionInfo, userInfo.getUserId());
  }

  /**
   * 登出吊销：从会话 Hash 读取 userId 与 refreshToken，移除会话索引，吊销 access_token 与 refresh_token。
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

    // 从 userId → Set 索引中移除该 token
    if (userId != null) {
      String sessionKey = buildSessionKey(userId);
      redisCollectionOps.sRem(sessionKey, accessToken);
      log.info("Removed token from session index for user: {}", userId);
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
   * <p>从 Redis Set 中读取所有 accessToken，逐个加入黑名单并删除 Hash，最后删除 Set 索引 Key。
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
      log.info("Evicted {} sessions for user: {}", tokens.size(), userId);
      return tokens.size();
    } catch (Exception e) {
      log.warn("Failed to evict sessions for user: {}, error={}", userId, e.getMessage());
      return 0;
    }
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

  private void storeSession(String accessToken, Map<String, Object> sessionInfo, String userId) {
    redisHashOps.hMSet(accessToken, sessionInfo);
    redisStringOps.expire(accessToken, Duration.ofSeconds(properties.getTokenTtlSeconds()));

    String sessionKey = buildSessionKey(userId);
    redisCollectionOps.sAdd(sessionKey, accessToken);
    redisStringOps.expire(sessionKey, Duration.ofSeconds(properties.getTokenTtlSeconds()));
  }

  private Map<String, Object> buildSessionInfo(
      String userId, String username, String roleCodes, String roleNames, String tenantId,
      String refreshToken) {
    Map<String, Object> sessionInfo = new HashMap<>();
    sessionInfo.put("userId", userId);
    sessionInfo.put("username", username);
    sessionInfo.put("roleCode", roleCodes);
    sessionInfo.put("roleName", roleNames);
    sessionInfo.put("tenantId", tenantId);
    sessionInfo.put("refreshToken", refreshToken);
    return sessionInfo;
  }

  private String buildSessionKey(String userId) {
    return SESSION_KEY_PREFIX + userId;
  }
}
