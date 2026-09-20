package com.njydsz.common.netty.session;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 基于内存的 SessionRepository 实现。
 *
 * <p>使用双层索引结构：
 *
 * <ul>
 *   <li>sessionId → ConnectionSession（主索引，支持精确查找和去重）
 *   <li>bizId → Set&lt;sessionId&gt;（二级索引，支持按业务 ID 快速查找）
 * </ul>
 *
 * <p>适用于单实例部署场景。集群部署请替换为 Redis 实现（预留 SPI）。
 *
 * <p>所有操作均为 O(1) 时间复杂度（find 除外，为 O(n)）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class InMemorySessionRepository implements SessionRepository {

  /** 主索引：sessionId → ConnectionSession */
  private final ConcurrentHashMap<String, ConnectionSession> sessionMap = new ConcurrentHashMap<>();

  /** 二级索引：bizId → sessionId 集合（支持同一 bizId 多端登录） */
  private final ConcurrentHashMap<String, Set<String>> bizIdIndex = new ConcurrentHashMap<>();

  @Override
  public void add(ConnectionSession session) {
    if (session == null) {
      throw new IllegalArgumentException("session 不能为 null");
    }
    ConnectionSession existing = sessionMap.putIfAbsent(session.getSessionId(), session);
    if (existing != null) {
      throw new IllegalArgumentException("sessionId 已存在: " + session.getSessionId());
    }
    String bizId = session.getBizId();
    if (bizId != null) {
      addToBizIdIndex(bizId, session.getSessionId());
    }
    log.debug("[Netty-SessionRepo] 注册会话: id={}, bizId={}", session.getSessionId(), bizId);
  }

  @Override
  public ConnectionSession remove(String sessionId) {
    ConnectionSession removed = sessionMap.remove(sessionId);
    if (removed != null) {
      String bizId = removed.getBizId();
      if (bizId != null) {
        removeFromBizIdIndex(bizId, sessionId);
      }
      log.debug("[Netty-SessionRepo] 移除会话: id={}, bizId={}", sessionId, bizId);
    }
    return removed;
  }

  @Override
  public ConnectionSession getById(String sessionId) {
    return sessionMap.get(sessionId);
  }

  @Override
  public List<ConnectionSession> getByBizId(String bizId) {
    if (bizId == null) {
      return Collections.emptyList();
    }
    Set<String> sessionIds = bizIdIndex.get(bizId);
    if (sessionIds == null || sessionIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<ConnectionSession> result = new ArrayList<>(sessionIds.size());
    for (String sid : sessionIds) {
      ConnectionSession session = sessionMap.get(sid);
      if (session != null) {
        result.add(session);
      }
    }
    return result;
  }

  @Override
  public Collection<ConnectionSession> getAll() {
    return Collections.unmodifiableCollection(sessionMap.values());
  }

  @Override
  public int size() {
    return sessionMap.size();
  }

  @Override
  public List<ConnectionSession> find(Predicate<ConnectionSession> predicate) {
    if (predicate == null) {
      return Collections.emptyList();
    }
    return sessionMap.values().stream()
        .filter(predicate)
        .collect(Collectors.toList());
  }

  @Override
  public boolean updateBizId(String sessionId, String bizId) {
    ConnectionSession session = sessionMap.get(sessionId);
    if (session == null) {
      log.warn("[Netty-SessionRepo] 会话不存在: {}", sessionId);
      return false;
    }
    try {
      session.setBizId(bizId);
      addToBizIdIndex(bizId, sessionId);
      log.debug("[Netty-SessionRepo] 更新 bizId: sessionId={}, bizId={}", sessionId, bizId);
      return true;
    } catch (IllegalStateException e) {
      log.warn("[Netty-SessionRepo] 更新 bizId 失败: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public long countByBizId(String bizId) {
    if (bizId == null) {
      return 0;
    }
    Set<String> sessionIds = bizIdIndex.get(bizId);
    return sessionIds == null ? 0 : sessionIds.size();
  }

  @Override
  public boolean contains(String sessionId) {
    return sessionMap.containsKey(sessionId);
  }

  /**
   * 添加 sessionId 到 bizId 索引。
   *
   * @param bizId 业务标识
   * @param sessionId 会话 ID
   */
  private void addToBizIdIndex(String bizId, String sessionId) {
    bizIdIndex.computeIfAbsent(bizId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
  }

  /**
   * 从 bizId 索引中移除 sessionId。
   *
   * @param bizId 业务标识
   * @param sessionId 会话 ID
   */
  private void removeFromBizIdIndex(String bizId, String sessionId) {
    Set<String> sessionIds = bizIdIndex.get(bizId);
    if (sessionIds != null) {
      sessionIds.remove(sessionId);
      if (sessionIds.isEmpty()) {
        bizIdIndex.remove(bizId);
      }
    }
  }
}
