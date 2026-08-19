package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.vo.UserSessionStatistics;
import com.njydsz.userinfo.domain.vo.UserSessionVO;

/**
 * 管理员会话治理服务。
 *
 * <p>提供管理员查看和强制下线用户会话的能力，支持：
 *
 * <ul>
 *   <li>查询指定用户的活跃会话列表</li>
 *   <li>查询全平台活跃会话（分页）</li>
 *   <li>强制下线指定会话</li>
 *   <li>强制下线用户全部会话</li>
 *   <li>会话统计信息（总会话数、活跃用户数、分端分布）</li>
 * </ul>
 *
 * <p>会话数据存储在 Redis Hash 中，Key 格式为 {@code accessToken}（由 {@link SessionManager} 管理），
 * 字段包含 userId、username、roleCode、roleName、tenantId、refreshToken、deviceType、schemaVersion。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionAdminService {

  /** 用户会话索引 Redis Key 前缀：userinfo:session:user:{userId} */
  private static final String SESSION_KEY_PREFIX = "userinfo:session:user:";

  /** 会话 Hash 中 deviceType 字段名 */
  private static final String SESSION_DEVICE_TYPE_FIELD = "deviceType";

  /** 会话 Hash 中 userId 字段名 */
  private static final String SESSION_USER_ID_FIELD = "userId";

  /** 会话 Hash 中 username 字段名 */
  private static final String SESSION_USERNAME_FIELD = "username";

  private final RedisHashOps redisHashOps;
  private final RedisStringOps redisStringOps;
  private final SessionManager sessionManager;

  /**
   * 查询用户的活跃会话列表。
   *
   * @param userId 用户 ID
   * @return 会话 VO 列表，无活跃度会话时返回空列表
   */
  public List<UserSessionVO> listUserSessions(String userId) {
    Set<String> tokens = sessionManager.listActiveSessions(userId);
    if (tokens.isEmpty()) {
      return new ArrayList<>(0);
    }

    List<UserSessionVO> sessions = new ArrayList<>(tokens.size());
    for (String token : tokens) {
      UserSessionVO vo = buildSessionVO(token);
      if (vo != null) {
        sessions.add(vo);
      }
    }
    return sessions;
  }

  /**
   * 查询全平台活跃会话（分页返回）。
   *
   * <p>当前实现受限于无法高效获取全平台所有 userId，返回空列表。
   * 完整实现需要维护全局会话索引或使用 Redis Scan 扫描 session Key。
   *
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 会话 VO 列表
   */
  public List<UserSessionVO> listAllActiveSessions(int page, int size) {
    // 由于缺乏全局会话索引，此接口当前无法高效实现
    // 后续可通过维护 userinfo:session:all（全局 Set 索引）支持 SCAN
    log.warn("listAllActiveSessions limited by lack of global session index, returning empty");
    return new ArrayList<>(0);
  }

  /**
   * 强制下线指定会话。
   *
   * <p>通过 {@link SessionManager#revokeSession(String)} 吊销单个 Token。
   *
   * @param userId 用户 ID（用于审计）
   * @param accessToken 会话 accessToken
   */
  public void forceLogout(String userId, String accessToken) {
    sessionManager.revokeSession(accessToken);
    log.info("Force logout session: userId={}, accessToken={}", userId, maskToken(accessToken));
  }

  /**
   * 强制下线用户全部会话。
   *
   * @param userId 用户 ID
   */
  public void forceLogoutAll(String userId) {
    int count = sessionManager.evictAllSessions(userId);
    log.info("Force logout all sessions for user: {}, evicted={}", userId, count);
  }

  /**
   * 获取会话统计信息。
   *
   * <p>由于缺乏全局会话索引，当前返回零值。完整实现需要全局会话索引或用户列表遍历。
   *
   * @return 会话统计 record
   */
  public UserSessionStatistics getSessionStatistics() {
    // 完整实现需要遍历所有用户或通过全局会话索引获取
    // 当前返回零值，避免 N+1 查询
    log.debug("getSessionStatistics called - requires global session index for accurate data");
    return new UserSessionStatistics(0, 0, new HashMap<>(4));
  }

  /**
   * 根据 accessToken 构建会话 VO。
   *
   * <p>从 Redis Hash 读取会话字段，组装为 {@link UserSessionVO}。
   *
   * @param accessToken 访问令牌
   * @return 会话 VO，Hash 不存在时返回 null
   */
  private UserSessionVO buildSessionVO(String accessToken) {
    try {
      String userId = redisHashOps.hGet(accessToken, SESSION_USER_ID_FIELD, String.class);
      if (userId == null) {
        return null;
      }

      String deviceTypeCode =
          redisHashOps.hGet(accessToken, SESSION_DEVICE_TYPE_FIELD, String.class);
      String username = redisHashOps.hGet(accessToken, SESSION_USERNAME_FIELD, String.class);

      // 计算剩余 TTL 作为过期时间估算
      Duration ttl = getSessionTtl(accessToken);

      UserSessionVO vo = new UserSessionVO();
      vo.setAccessToken(maskToken(accessToken));
      vo.setUsername(username);
      vo.setDevice(deviceTypeCode != null ? deviceTypeCode : "unknown");
      vo.setExpireTime(ttl != null ? formatDuration(ttl) : "unknown");
      // 会话 Hash 中不存储 loginTime/loginIp，留空
      vo.setLoginTime(null);
      vo.setLoginIp(null);
      vo.setUserAgent(null);
      return vo;
    } catch (Exception e) {
      log.warn("Failed to build session VO for token: {}", maskToken(accessToken), e);
      return null;
    }
  }

  /**
   * 获取会话剩余 TTL。
   *
   * @param key Redis Key（accessToken）
   * @return 剩余 TTL，获取失败返回 null
   */
  private Duration getSessionTtl(String key) {
    try {
      long seconds = redisStringOps.getExpire(key);
      return seconds > 0 ? Duration.ofSeconds(seconds) : null;
    } catch (Exception e) {
      log.warn("[SessionAdmin] 获取会话TTL失败，key={}", maskKey(key), e);
      return null;
    }
  }

  /**
   * Redis Key 脱敏处理（保留前8位，其余替换为*）。
   *
   * @param key Redis Key
   * @return 脱敏后的 Key
   */
  private static String maskKey(String key) {
    if (key == null || key.length() <= 8) {
      return "***";
    }
    return key.substring(0, 8) + "***";
  }

  /**
   * 格式化 Duration 为可读字符串。
   *
   * @param duration 时长
   * @return 格式化字符串
   */
  private String formatDuration(Duration duration) {
    if (duration == null) {
      return "unknown";
    }
    long hours = duration.toHours();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();
    if (hours > 0) {
      return String.format("%dh%02dm%02ds", hours, minutes, seconds);
    }
    if (minutes > 0) {
      return String.format("%dm%02ds", minutes, seconds);
    }
    return String.format("%ds", seconds);
  }

  /**
   * 脱敏展示 Token（仅显示前 8 位 + ... + 后 4 位）。
   *
   * @param token 原始 Token
   * @return 脱敏后的 Token
   */
  private String maskToken(String token) {
    if (token == null || token.length() < 16) {
      return "***";
    }
    return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
  }
}
