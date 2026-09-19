package com.njydsz.common.auth.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.model.SessionInfo;

/**
 * 基于本地内存的会话注册表实现（降级/开发模式）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储会话信息，适用于：
 *
 * <ul>
 *   <li>开发/测试环境（无 Redis）</li>
 *   <li>Redis 不可用时的降级兜底</li>
 *   <li>单实例部署</li>
 * </ul>
 *
 * <p><b>限制：</b>不支持跨节点会话同步，集群部署时各节点独立维护会话视图。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class LocalSessionRegistry implements SessionRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(LocalSessionRegistry.class);

  /** 用户 ID → （会话 ID → 会话信息）映射。 */
  private final ConcurrentMap<String, ConcurrentMap<String, SessionInfo>> sessions =
      new ConcurrentHashMap<>(128);

  /** 会话 ID → 用户 ID 反向索引（快速撤销）。 */
  private final ConcurrentMap<String, String> sessionToUser = new ConcurrentHashMap<>(128);

  /** 最大并发会话数（默认 5）。 */
  private final int maxSessionsPerUser;

  public LocalSessionRegistry(int maxSessionsPerUser) {
    this.maxSessionsPerUser = Math.max(1, maxSessionsPerUser);
  }

  public LocalSessionRegistry() {
    this(5);
  }

  @Override
  public void register(SessionInfo sessionInfo) {
    Objects.requireNonNull(sessionInfo, "sessionInfo cannot be null");
    Objects.requireNonNull(sessionInfo.getSessionId(), "sessionId cannot be null");
    Objects.requireNonNull(sessionInfo.getUserId(), "userId cannot be null");
    String userId = sessionInfo.getUserId();
    String sessionId = sessionInfo.getSessionId();
    sessions
        .computeIfAbsent(userId, k -> new ConcurrentHashMap<>(8))
        .put(sessionId, sessionInfo);
    sessionToUser.put(sessionId, userId);
    LOG.debug("[LocalSessionRegistry] 注册会话: userId={}, sessionId={}", userId, sessionId);
    // 超出限制时自动踢出最早的会话
    evictIfNeeded(userId);
  }

  @Override
  public void revoke(String sessionId) {
    if (sessionId == null) {
      return;
    }
    String userId = sessionToUser.remove(sessionId);
    if (userId != null) {
      ConcurrentMap<String, SessionInfo> userSessions = sessions.get(userId);
      if (userSessions != null) {
        userSessions.remove(sessionId);
        if (userSessions.isEmpty()) {
          sessions.remove(userId);
        }
      }
      LOG.info("[LocalSessionRegistry] 撤销会话: userId={}, sessionId={}", userId, sessionId);
    }
  }

  @Override
  public void revokeAll(String userId) {
    if (userId == null) {
      return;
    }
    ConcurrentMap<String, SessionInfo> userSessions = sessions.remove(userId);
    if (userSessions != null) {
      for (String sessionId : userSessions.keySet()) {
        sessionToUser.remove(sessionId);
      }
      LOG.info("[LocalSessionRegistry] 撤销用户全部会话: userId={}, count={}", userId, userSessions.size());
    }
  }

  @Override
  public boolean isSessionValid(String sessionId) {
    return sessionId != null && sessionToUser.containsKey(sessionId);
  }

  @Override
  public List<SessionInfo> listSessions(String userId) {
    if (userId == null) {
      return Collections.emptyList();
    }
    ConcurrentMap<String, SessionInfo> userSessions = sessions.get(userId);
    if (userSessions == null || userSessions.isEmpty()) {
      return Collections.emptyList();
    }
    return userSessions.values().stream()
        .sorted((a, b) -> Long.compare(a.getIssuedAtMs(), b.getIssuedAtMs()))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public int getSessionCount(String userId) {
    if (userId == null) {
      return 0;
    }
    ConcurrentMap<String, SessionInfo> userSessions = sessions.get(userId);
    return userSessions != null ? userSessions.size() : 0;
  }

  @Override
  public SessionInfo getSession(String sessionId) {
    if (sessionId == null) {
      return null;
    }
    String userId = sessionToUser.get(sessionId);
    if (userId == null) {
      return null;
    }
    ConcurrentMap<String, SessionInfo> userSessions = sessions.get(userId);
    return userSessions != null ? userSessions.get(sessionId) : null;
  }

  @Override
  public Collection<String> getAllSessionIds() {
    return Collections.unmodifiableSet(sessionToUser.keySet());
  }

  /** 超出最大并发会话数时踢出最早的会话。 */
  private void evictIfNeeded(String userId) {
    if (userId == null) {
      return;
    }
    ConcurrentMap<String, SessionInfo> userSessions = sessions.get(userId);
    if (userSessions == null) {
      return;
    }
    int excess = userSessions.size() - maxSessionsPerUser;
    if (excess <= 0) {
      return;
    }
    // 按签发时间升序排序，踢出最早的
    List<Map.Entry<String, SessionInfo>> entries =
        userSessions.entrySet().stream()
            .sorted((a, b) -> Long.compare(a.getValue().getIssuedAtMs(), b.getValue().getIssuedAtMs()))
            .collect(Collectors.toCollection(ArrayList::new));
    for (int i = 0; i < excess && i < entries.size(); i++) {
      String evictSessionId = entries.get(i).getKey();
      userSessions.remove(evictSessionId);
      sessionToUser.remove(evictSessionId);
      LOG.info("[LocalSessionRegistry] 踢出最早会话（超出并发限制）: userId={}, sessionId={}", userId, evictSessionId);
    }
  }
}
