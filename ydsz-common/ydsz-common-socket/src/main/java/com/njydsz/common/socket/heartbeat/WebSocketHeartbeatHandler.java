package com.njydsz.common.socket.heartbeat;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.session.OnlineUserService;

/**
 * WebSocket 心跳保活处理器。
 *
 * <p>维护 {@code sessionId → lastHeartbeatTime} 映射。当 Redis 可用时，使用 Redis Sorted Set（{@code
 * ydsz:ws:heartbeat:sessions}）在集群范围内维护心跳状态，避免单节点宕机导致心跳记录丢失；Redis 不可用时降级为本地
 * fallback。
 *
 * <p><b>O(1) 快速定位优化</b>：引入辅助 Hash 索引（{@code ydsz:ws:heartbeat:index}）维护 {@code
 * sessionId → ZSet memberValue} 映射。{@link #updateHeartbeat(String)} 和 {@link
 * #unregisterSession(String)} 仅需一次 HGET + 一次 ZADD/ZREM（O(1)），避免全表扫描 Sorted Set（O(n)）。
 *
 * <p>Sorted Set value 格式：{@code userId:sessionId}，清理时可直接解析出 userId，无需额外查询。
 *
 * <p>通过 {@link Scheduled} 定时扫描超时 Session，超过 {@link
 * WebSocketProperties.Heartbeat#getStaleSessionTimeout()} 未活跃的 Session 标记为僵尸连接并触发下线清理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class WebSocketHeartbeatHandler {

  private final WebSocketProperties properties;
  private final OnlineUserService onlineUserService;
  private final StringRedisTemplate redisTemplate;

  /** 是否使用 Redis 维护心跳（true=Redis，false=本地 fallback） */
  private final boolean isUsingRedis;

  /** Sorted Set value 分隔符 */
  private static final String VALUE_SEPARATOR = ":";

  public WebSocketHeartbeatHandler(
      WebSocketProperties properties,
      OnlineUserService onlineUserService,
      StringRedisTemplate redisTemplate) {
    this.properties = properties;
    this.onlineUserService = onlineUserService;
    this.redisTemplate = redisTemplate;
    this.isUsingRedis = redisTemplate != null;
    log.info(
        "[WS-Heartbeat] 初始化心跳处理器: backend={}",
        isUsingRedis ? "Redis Sorted Set + Hash Index" : "Local fallback");
  }

  /**
   * 注册新连接的 Session。
   *
   * <p>Sorted Set member = {@code userId:sessionId}；辅助 Hash 索引 {@code sessionId → member}。
   *
   * @param sessionId STOMP Session ID
   * @param userId 用户 ID
   */
  public void registerSession(String sessionId, String userId) {
    if (sessionId == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (isUsingRedis) {
      String member = userId != null ? userId + VALUE_SEPARATOR + sessionId : sessionId;
      String heartbeatKey = getHeartbeatKey();
      // Pipeline: ZADD + HSET
      redisTemplate.opsForZSet().add(heartbeatKey, member, now);
      redisTemplate.opsForHash().put(getHeartbeatIndexKey(), sessionId, member);
    }
  }

  /**
   * 更新 Session 心跳时间戳（客户端心跳到达时调用）。
   *
   * <p><b>O(1) 实现</b>：通过辅助 Hash 索引直接获取 member 值，单次 ZADD 更新 score。
   *
   * @param sessionId STOMP Session ID
   */
  public void updateHeartbeat(String sessionId) {
    if (sessionId == null || !isUsingRedis) {
      return;
    }
    long now = System.currentTimeMillis();
    String heartbeatKey = getHeartbeatKey();
    // O(1) Hash 查询获取完整 member 字符串
    Object memberObj = redisTemplate.opsForHash().get(getHeartbeatIndexKey(), sessionId);
    if (memberObj instanceof String member) {
      redisTemplate.opsForZSet().add(heartbeatKey, member, now);
    } else {
      // 索引丢失时兜底：退化为直接写入 sessionId（可能不含 userId）
      redisTemplate.opsForZSet().add(heartbeatKey, sessionId, now);
      log.debug("[WS-Heartbeat] 索引丢失, sessionId={}, 使用 sessionId 直接更新", sessionId);
    }
  }

  /**
   * 移除已断开的 Session。
   *
   * <p><b>O(1) 实现</b>：通过辅助 Hash 索引直接获取 member 值，单次 ZREM 删除。
   *
   * @param sessionId STOMP Session ID
   */
  public void unregisterSession(String sessionId) {
    if (sessionId == null || !isUsingRedis) {
      return;
    }
    String heartbeatKey = getHeartbeatKey();
    // O(1) Hash 查询获取完整 member 字符串
    Object memberObj = redisTemplate.opsForHash().get(getHeartbeatIndexKey(), sessionId);
    if (memberObj instanceof String member) {
      redisTemplate.opsForZSet().remove(heartbeatKey, member);
    } else {
      // 索引丢失时尝试按 sessionId 后缀匹配（兜底清理历史遗留数据）
      cleanupLegacyMember(heartbeatKey, sessionId);
    }
    // 删除辅助索引
    redisTemplate.opsForHash().delete(getHeartbeatIndexKey(), sessionId);
  }

  /**
   * 定时扫描僵尸 Session。
   *
   * <p>超过 {@code staleSessionTimeout} 未收到心跳的 Session，调用 {@link
   * OnlineUserService#markOffline(String, String)} 清理。
   */
  @Scheduled(fixedDelayString = "${ydsz.websocket.heartbeat.stale-session-timeout:60000}")
  public void cleanStaleSessions() {
    if (!isUsingRedis) {
      return;
    }
    long now = System.currentTimeMillis();
    long staleTimeout = properties.getHeartbeat().getStaleSessionTimeout();
    long cutoffTime = now - staleTimeout;
    int cleaned = cleanStaleSessionsFromRedis(cutoffTime);

    if (cleaned > 0) {
      log.info("[WS-Heartbeat] 僵尸 Session 清理完成, 清理数={}", cleaned);
    }
  }

  /**
   * 从 Redis Sorted Set 中清理僵尸 Session。
   *
   * <p>使用 ZRANGEBYSCORE 仅扫描超时区间内数据（通常远小于全量）。
   *
   * @param cutoffTime 截止时间戳（毫秒）
   * @return 清理数量
   */
  private int cleanStaleSessionsFromRedis(long cutoffTime) {
    String heartbeatKey = getHeartbeatKey();
    int cleaned = 0;
    try {
      Set<ZSetOperations.TypedTuple<String>> staleEntries =
          redisTemplate.opsForZSet().rangeByScoreWithScores(heartbeatKey, 0, cutoffTime);
      if (staleEntries == null || staleEntries.isEmpty()) {
        return 0;
      }
      for (ZSetOperations.TypedTuple<String> entry : staleEntries) {
        String member = entry.getValue();
        if (member == null) {
          continue;
        }
        // 解析 value: "userId:sessionId" 或 "sessionId"
        String userId = null;
        String sessionId;
        int sepIndex = member.indexOf(VALUE_SEPARATOR);
        if (sepIndex > 0) {
          userId = member.substring(0, sepIndex);
          sessionId = member.substring(sepIndex + 1);
        } else {
          sessionId = member;
        }
        log.warn("[WS-Heartbeat] 检测到僵尸 Session, 清理: userId={}, sessionId={}", userId, sessionId);
        redisTemplate.opsForZSet().remove(heartbeatKey, member);
        // 同步清理辅助 Hash 索引
        redisTemplate.opsForHash().delete(getHeartbeatIndexKey(), sessionId);
        if (userId != null && onlineUserService != null) {
          onlineUserService.markOffline(userId, sessionId);
        }
        cleaned++;
      }
    } catch (Exception e) {
      log.warn("[WS-Heartbeat] Redis 清理僵尸 Session 异常: err={}", e.getMessage());
    }
    return cleaned;
  }

  /**
   * 获取当前活跃 Session 数量。
   *
   * @return 活跃 Session 数
   */
  public int getActiveSessionCount() {
    if (isUsingRedis) {
      try {
        String key = getHeartbeatKey();
        Long size = redisTemplate.opsForZSet().size(key);
        return size != null ? size.intValue() : 0;
      } catch (Exception e) {
        log.warn("[WS-Heartbeat] Redis 获取 Session 数量异常: err={}", e.getMessage());
        return 0;
      }
    }
    return 0;
  }

  /**
   * 兜底策略：按 sessionId 后缀匹配清理历史遗留 member（索引丢失时的降级处理）。
   *
   * <p>仅在辅助 Hash 索引中找不到对应 entry 时触发，为 O(n) 全表扫描，极少执行。
   *
   * @param heartbeatKey Redis Key
   * @param sessionId 目标 Session ID
   */
  private void cleanupLegacyMember(String heartbeatKey, String sessionId) {
    Set<String> allMembers = redisTemplate.opsForZSet().range(heartbeatKey, 0, -1);
    if (allMembers != null) {
      for (String member : allMembers) {
        if (member.endsWith(VALUE_SEPARATOR + sessionId)) {
          redisTemplate.opsForZSet().remove(heartbeatKey, member);
          break;
        }
      }
    }
  }

  private String getHeartbeatKey() {
    return WebSocketConstants.WS_HEARTBEAT_KEY;
  }

  /**
   * 获取辅助 Hash 索引 Redis Key。
   *
   * <p>该 Hash 维护 {@code sessionId → SortedSet memberValue} 映射,
   * 使 updateHeartbeat/unregisterSession 操作从 O(n) 优化至 O(1)。
   *
   * @return Hash 索引 Redis Key
   */
  private String getHeartbeatIndexKey() {
    return WebSocketConstants.WS_HEARTBEAT_KEY + ":index";
  }

  /**
   * 是否使用 Redis 作为心跳后端。
   *
   * @return true 表示使用 Redis Sorted Set
   */
  public boolean isUsingRedis() {
    return isUsingRedis;
  }
}
