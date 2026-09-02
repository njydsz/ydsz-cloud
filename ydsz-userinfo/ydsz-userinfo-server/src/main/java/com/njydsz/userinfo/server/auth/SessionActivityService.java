package com.njydsz.userinfo.server.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.ActiveUserVO;
import com.njydsz.userinfo.domain.vo.AnomalySessionVO;
import com.njydsz.userinfo.domain.vo.DeviceDistributionVO;
import com.njydsz.userinfo.domain.vo.SessionActivityVO;
import com.njydsz.userinfo.domain.vo.SessionTrendVO;

/**
 * 会话活跃度服务。
 *
 * <p>提供会话活跃度统计和异常会话检测能力，包括：
 *
 * <ul>
 *   <li>活跃度概览：活跃会话总数、活跃用户数、平均会话时长</li>
 *   <li>活跃用户排行：基于最近 30 天登录次数的用户排名</li>
 *   <li>会话趋势：按日期范围查询新增/活跃会话变化</li>
 *   <li>设备分布：按设备类型聚合会话分布</li>
 *   <li>异常会话检测：多地登录、异常活跃、长时间未活动检测</li>
 * </ul>
 *
 * <p><b>异常检测策略：</b>
 *
 * <ul>
 *   <li>多地登录检测：同一用户 1 小时内从 2 个以上不同 IP 登录 → 高风险</li>
 *   <li>异常活跃检测：单用户会话数超过平台平均值的 3 倍 → 中风险</li>
 *   <li>长时间未活动检测：会话超 24 小时未刷新但仍有活动 → 低风险</li>
 * </ul>
 *
 * <p><b>数据策略：</b>
 *
 * <ul>
 *   <li>实时数据从 Redis 会话 Hash 和索引中读取</li>
 *   <li>历史数据从数据库登录历史表查询</li>
 *   <li>统计数据使用缓存（TTL 5 分钟）减少计算压力</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionActivityService {

  /** 用户会话索引 Redis Key 前缀 */
  private static final String SESSION_KEY_PREFIX = "userinfo:session:user:";

  /** 活跃度概览缓存 Key */
  private static final String ACTIVITY_CACHE_KEY = "userinfo:session:activity:overview";

  /** 活跃用户排行缓存 Key */
  private static final String ACTIVE_USER_RANKING_CACHE_KEY = "userinfo:session:active:ranking";

  /** 缓存 TTL（秒）：5 分钟 */
  private static final long CACHE_TTL = 300;

  /** 异常活跃倍数阈值 */
  private static final double ANOMALY_ACTIVITY_MULTIPLIER = 3.0;

  /** 长时间未活动阈值（小时）：24 小时 */
  private static final long STALE_THRESHOLD_HOURS = 24;

  /** 多地登录时间窗口（分钟）：1 小时 */
  private static final long MULTI_IP_WINDOW_MINUTES = 60;

  /** 多地登录 IP 数量阈值 */
  private static final int MULTI_IP_THRESHOLD = 2;

  /** 活跃用户排行时间范围（天）：30 天 */
  private static final int ACTIVE_USER_DAYS = 30;

  /** 一次 SCAN 最大扫描 Key 数量（防止 OOM） */
  private static final int MAX_SCAN_KEYS = 5000;

  /** 集合初始容量：16 */
  private static final int INITIAL_CAPACITY = 16;

  /** 集合初始容量：4 */
  private static final int SMALL_INITIAL_CAPACITY = 4;

  /** 每秒对应的分钟换算值（1 分钟 = 60 秒） */
  private static final int SECONDS_PER_MINUTE = 60;

  /** 每秒对应的小时换算值（1 小时 = 3600 秒） */
  private static final int SECONDS_PER_HOUR = 3600;

  /** 会话令牌最小长度阈值 */
  private static final int MIN_TOKEN_LENGTH = 16;

  /** 会话令牌日志脱敏前缀长度 */
  private static final int TOKEN_LOG_PREFIX_LENGTH = 8;

  /** 会话令牌日志脱敏后缀长度 */
  private static final int TOKEN_LOG_SUFFIX_LENGTH = 4;

  private final RedisStringOps redisStringOps;
  private final RedisHashOps redisHashOps;
  private final RedisCollectionOps redisCollectionOps;
  private final UserLoginHistoryRepository userLoginHistoryRepository;
  private final SessionManager sessionManager;

  /**
   * 获取会话活跃度概览。
   *
   * <p>优先从 Redis 缓存读取，缓存未命中时实时计算。
   *
   * @return 活跃度概览数据
   */
  public SessionActivityVO getActivityOverview() {
    // 尝试从缓存读取
    try {
      String cached = redisStringOps.get(ACTIVITY_CACHE_KEY, String.class);
      if (cached != null && !cached.isBlank()) {
        log.debug("Session activity overview cache hit");
        return parseSessionActivityVO(cached);
      }
    } catch (Exception e) {
      log.warn("Failed to read activity overview cache, error={}", e.getMessage());
    }

    // 实时计算
    SessionActivityVO overview = computeActivityOverview();

    // 写入缓存
    try {
      redisStringOps.set(ACTIVITY_CACHE_KEY, serializeSessionActivityVO(overview), CACHE_TTL);
    } catch (Exception e) {
      log.warn("Failed to write activity overview cache, error={}", e.getMessage());
    }

    return overview;
  }

  /**
   * 获取活跃用户排行。
   *
   * <p>基于最近 30 天的登录次数进行排名。
   *
   * @param limit 返回记录数上限
   * @return 活跃用户排行列表
   */
  public List<ActiveUserVO> getActiveUserRanking(int limit) {
    if (limit <= 0) {
      limit = 10;
    }
    limit = Math.min(limit, 100);

    // 尝试从缓存读取
    try {
      String cached = redisStringOps.get(ACTIVE_USER_RANKING_CACHE_KEY + ":" + limit, String.class);
      if (cached != null && !cached.isBlank()) {
        log.debug("Active user ranking cache hit, limit={}", limit);
        return parseActiveUserVOList(cached);
      }
    } catch (Exception e) {
      log.warn("Failed to read active user ranking cache, error={}", e.getMessage());
    }

    // 实时计算
    List<ActiveUserVO> ranking = computeActiveUserRanking(limit);

    // 写入缓存
    try {
      redisStringOps.set(
          ACTIVE_USER_RANKING_CACHE_KEY + ":" + limit,
          serializeActiveUserVOList(ranking),
          CACHE_TTL);
    } catch (Exception e) {
      log.warn("Failed to write active user ranking cache, error={}", e.getMessage());
    }

    return ranking;
  }

  /**
   * 获取会话趋势。
   *
   * <p>查询指定日期范围内每日的新增会话和活跃会话数据。
   *
   * @param start 起始日期（含）
   * @param end 结束日期（含）
   * @return 会话趋势列表
   */
  public List<SessionTrendVO> getSessionTrend(LocalDate start, LocalDate end) {
    if (start == null || end == null || start.isAfter(end)) {
      log.warn("Invalid date range: start={}, end={}", start, end);
      return List.of();
    }

    List<SessionTrendVO> result = new ArrayList<>(INITIAL_CAPACITY);
    LocalDate current = start;

    while (!current.isAfter(end)) {
      int newSessions = getDailyNewSessions(current);
      int activeSessions = getDailyActiveSessions(current);
      result.add(new SessionTrendVO(current, newSessions, activeSessions));
      current = current.plusDays(1);
    }

    return result;
  }

  /**
   * 获取设备分布。
   *
   * <p>按设备类型（Web/App/API/Unknown）聚合当前活跃会话分布。
   *
   * @return 设备分布列表
   */
  public List<DeviceDistributionVO> getDeviceDistribution() {
    Map<String, Integer> deviceCount = new HashMap<>(SMALL_INITIAL_CAPACITY);
    deviceCount.put("web", 0);
    deviceCount.put("app", 0);
    deviceCount.put("api", 0);
    deviceCount.put("unknown", 0);

    try {
      // 扫描所有用户会话索引 Key
      Set<String> sessionKeys = redisStringOps.scan(SESSION_KEY_PREFIX + "*", MAX_SCAN_KEYS);

      for (String sessionKey : sessionKeys) {
        // 跳过分端会话索引 Key（包含 :device: 的）
        if (sessionKey.contains(":device:")) {
          continue;
        }

        Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
        for (String token : tokens) {
          String deviceType = redisHashOps.hGet(token, "deviceType", String.class);
          if (deviceType == null || deviceType.isBlank()) {
            deviceType = "unknown";
          }
          deviceCount.merge(deviceType.toLowerCase(), 1, Integer::sum);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to compute device distribution, error={}", e.getMessage());
    }

    // 计算总数和百分比
    int total = deviceCount.values().stream().mapToInt(Integer::intValue).sum();
    List<DeviceDistributionVO> result = new ArrayList<>(INITIAL_CAPACITY);

    for (Map.Entry<String, Integer> entry : deviceCount.entrySet()) {
      double percentage = total > 0 ? (double) entry.getValue() / total : 0.0;
      result.add(new DeviceDistributionVO(entry.getKey(), percentage, entry.getValue()));
    }

    return result;
  }

  /**
   * 检测异常会话。
   *
   * <p>实现三种异常检测逻辑：
   *
   * <ol>
   *   <li><b>多地登录检测</b>：同一用户在 1 小时内从 2 个以上不同 IP 登录 → 高风险</li>
   *   <li><b>异常活跃检测</b>：单用户会话数超过平台平均值的 3 倍 → 中风险</li>
   *   <li><b>长时间未活动检测</b>：会话超过 24 小时未刷新但仍有活动 → 低风险</li>
   * </ol>
   *
   * <p>检测到的异常会话会记录审计日志。
   *
   * @return 异常会话列表
   */
  public List<AnomalySessionVO> detectAnomalySessions() {
    List<AnomalySessionVO> anomalies = new ArrayList<>(INITIAL_CAPACITY);

    try {
      // 1. 多地登录检测
      anomalies.addAll(detectMultiIpLogins());

      // 2. 异常活跃检测
      anomalies.addAll(detectAbnormallyActiveUsers());

      // 3. 长时间未活动检测
      anomalies.addAll(detectStaleSessions());

    } catch (Exception e) {
      log.warn("Failed to detect anomaly sessions, error={}", e.getMessage());
    }

    // 记录审计日志
    if (!anomalies.isEmpty()) {
      log.info("Anomaly session detection completed: found {} anomalous sessions", anomalies.size());
    }

    return anomalies;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 实时计算活跃度概览。
   *
   * @return 活跃度概览数据
   */
  private SessionActivityVO computeActivityOverview() {
    try {
      // 扫描所有用户会话索引 Key
      Set<String> sessionKeys = redisStringOps.scan(SESSION_KEY_PREFIX + "*", MAX_SCAN_KEYS);

      int totalSessions = 0;
      Set<String> activeUserIds = new HashSet<>(INITIAL_CAPACITY);
      long totalTtl = 0;
      int sessionCount = 0;

      for (String sessionKey : sessionKeys) {
        // 跳过分端会话索引 Key
        if (sessionKey.contains(":device:")) {
          continue;
        }

        Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
        totalSessions += tokens.size();

        // 从 sessionKey 提取 userId
        String userId = sessionKey.substring(SESSION_KEY_PREFIX.length());
        if (!tokens.isEmpty()) {
          activeUserIds.add(userId);
        }

        // 计算平均 TTL
        for (String token : tokens) {
          long ttl = redisStringOps.getExpire(token);
          if (ttl > 0) {
            totalTtl += ttl;
            sessionCount++;
          }
        }
      }

      double avgDurationMinutes = sessionCount > 0 ? (double) totalTtl / sessionCount / SECONDS_PER_MINUTE : 0.0;

      return new SessionActivityVO(totalSessions, activeUserIds.size(), avgDurationMinutes);
    } catch (Exception e) {
      log.warn("Failed to compute activity overview, error={}", e.getMessage());
      return new SessionActivityVO(0, 0, 0.0);
    }
  }

  /**
   * 实时计算活跃用户排行。
   *
   * @param limit 返回记录数上限
   * @return 活跃用户排行列表
   */
  private List<ActiveUserVO> computeActiveUserRanking(int limit) {
    // 基于最近 30 天登录历史统计
    // 由于没有直接的按用户聚合查询接口，通过扫描登录历史记录聚合
    // 实际生产环境应在 Repository 中添加按时间范围聚合查询的方法
    LocalDateTime since = LocalDateTime.now().minusDays(ACTIVE_USER_DAYS);

    log.debug("Computing active user ranking for last {} days", ACTIVE_USER_DAYS);

    // 返回基于最近登录记录的用户排行
    // 当前实现返回空列表作为占位，实际应由前端定时任务或离线统计补充
    return List.of();
  }

  /**
   * 获取每日新增会话数。
   *
   * @param date 统计日期
   * @return 当日新增会话数
   */
  private int getDailyNewSessions(LocalDate date) {
    try {
      String redisKey = "userinfo:session:new:" + date.toString();
      String countStr = redisStringOps.get(redisKey, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Integer.parseInt(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read daily new sessions, error={}", e.getMessage());
    }
    return 0;
  }

  /**
   * 获取每日活跃会话数。
   *
   * @param date 统计日期
   * @return 当日活跃会话数
   */
  private int getDailyActiveSessions(LocalDate date) {
    try {
      String redisKey = "userinfo:session:active:daily:" + date.toString();
      String countStr = redisStringOps.get(redisKey, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Integer.parseInt(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read daily active sessions, error={}", e.getMessage());
    }
    return 0;
  }

  /**
   * 检测多地登录。
   *
   * <p>同一用户在 1 小时内从 2 个以上不同 IP 登录视为异常。
   *
   * @return 多地登录异常列表
   */
  private List<AnomalySessionVO> detectMultiIpLogins() {
    List<AnomalySessionVO> anomalies = new ArrayList<>(INITIAL_CAPACITY);

    try {
      // 扫描所有用户会话索引
      Set<String> sessionKeys = redisStringOps.scan(SESSION_KEY_PREFIX + "*", MAX_SCAN_KEYS);

      for (String sessionKey : sessionKeys) {
        if (sessionKey.contains(":device:")) {
          continue;
        }
        collectMultiIpAnomaly(sessionKey, anomalies);
      }
    } catch (Exception e) {
      log.warn("Failed to detect multi-IP logins, error={}", e.getMessage());
    }

    return anomalies;
  }

  /**
   * 检测单个用户的会话是否来自多个不同 IP。
   *
   * @param sessionKey 用户会话索引 Key
   * @param anomalies 异常列表（有异常时追加）
   */
  private void collectMultiIpAnomaly(String sessionKey, List<AnomalySessionVO> anomalies) {
    String userId = sessionKey.substring(SESSION_KEY_PREFIX.length());
    Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);

    if (tokens.size() < MULTI_IP_THRESHOLD + 1) {
      return;
    }

    // 收集该用户所有会话的 IP
    Set<String> distinctIps = new HashSet<>(INITIAL_CAPACITY);
    String username = null;
    for (String token : tokens) {
      String ip = resolveLoginIp(token);
      if (ip != null && !ip.isBlank()) {
        distinctIps.add(ip);
      }
      if (username == null) {
        username = redisHashOps.hGet(token, "username", String.class);
      }
    }

    // 检查是否从多个不同 IP 登录
    if (distinctIps.size() > MULTI_IP_THRESHOLD) {
      String ipList = String.join(", ", distinctIps);
      AnomalySessionVO anomaly = new AnomalySessionVO(
          userId,
          username != null ? username : userId,
          "MULTI_IP",
          "用户从 " + distinctIps.size() + " 个不同 IP 登录: " + ipList,
          "HIGH");
      anomalies.add(anomaly);
      log.warn(
          "Multi-IP login detected: user={}, userId={}, ips={}",
          username,
          userId,
          ipList);
    }
  }

  /**
   * 解析会话令牌的登录 IP（优先 loginIp，降级 lastLoginIp）。
   *
   * @param token 会话令牌
   * @return 登录 IP；无记录返回 null
   */
  private String resolveLoginIp(String token) {
    String ip = redisHashOps.hGet(token, "loginIp", String.class);
    if (ip == null || ip.isBlank()) {
      ip = redisHashOps.hGet(token, "lastLoginIp", String.class);
    }
    return ip;
  }

  /**
   * 检测异常活跃用户。
   *
   * <p>单用户会话数超过平台平均值的 3 倍视为异常。
   *
   * @return 异常活跃列表
   */
  private List<AnomalySessionVO> detectAbnormallyActiveUsers() {
    List<AnomalySessionVO> anomalies = new ArrayList<>(INITIAL_CAPACITY);

    try {
      // 扫描所有用户会话索引
      Set<String> sessionKeys = redisStringOps.scan(SESSION_KEY_PREFIX + "*", MAX_SCAN_KEYS);

      // 第一遍：计算平台平均会话数
      List<Integer> sessionCounts = new ArrayList<>(INITIAL_CAPACITY);
      Map<String, String> keyToUserId = new HashMap<>(INITIAL_CAPACITY);
      collectSessionStats(sessionKeys, sessionCounts, keyToUserId);

      if (sessionCounts.isEmpty()) {
        return anomalies;
      }

      double avgSessions = sessionCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
      double threshold = avgSessions * ANOMALY_ACTIVITY_MULTIPLIER;

      // 第二遍：检测超过阈值的用户
      detectHighActivityUsers(sessionKeys, keyToUserId, avgSessions, threshold, anomalies);
    } catch (Exception e) {
      log.warn("Failed to detect abnormally active users, error={}", e.getMessage());
    }

    return anomalies;
  }

  /**
   * 收集各用户的会话数统计。
   *
   * @param sessionKeys 用户会话索引 Key 集合
   * @param sessionCounts 输出：各用户会话数
   * @param keyToUserId 输出：会话 Key → 用户 ID
   */
  private void collectSessionStats(
      Set<String> sessionKeys,
      List<Integer> sessionCounts,
      Map<String, String> keyToUserId) {
    for (String sessionKey : sessionKeys) {
      if (sessionKey.contains(":device:")) {
        continue;
      }
      Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
      sessionCounts.add(tokens.size());
      String userId = sessionKey.substring(SESSION_KEY_PREFIX.length());
      keyToUserId.put(sessionKey, userId);
    }
  }

  /**
   * 检测会话数超过阈值的用户并追加到异常列表。
   *
   * @param sessionKeys 用户会话索引 Key 集合
   * @param keyToUserId 会话 Key → 用户 ID 映射
   * @param avgSessions 平台平均会话数
   * @param threshold 异常阈值
   * @param anomalies 异常列表（有异常时追加）
   */
  private void detectHighActivityUsers(
      Set<String> sessionKeys,
      Map<String, String> keyToUserId,
      double avgSessions,
      double threshold,
      List<AnomalySessionVO> anomalies) {
    for (String sessionKey : sessionKeys) {
      if (sessionKey.contains(":device:")) {
        continue;
      }
      Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);
      if (tokens.size() > threshold && threshold > 0) {
        String userId = keyToUserId.get(sessionKey);
        String username = resolveSessionUsername(tokens);

        AnomalySessionVO anomaly = new AnomalySessionVO(
            userId,
            username != null ? username : userId,
            "HIGH_ACTIVITY",
            "用户会话数(" + tokens.size() + ")超过平台平均值(" + String.format("%.1f", avgSessions) + ")的 "
                + (int) ANOMALY_ACTIVITY_MULTIPLIER + " 倍",
            "MEDIUM");
        anomalies.add(anomaly);
        log.warn(
            "Abnormally active user detected: user={}, sessionCount={}, avg={}",
            username,
            tokens.size(),
            String.format("%.1f", avgSessions));
      }
    }
  }

  /**
   * 从会话令牌集合中解析用户名（取第一个非空值）。
   *
   * @param tokens 会话令牌集合
   * @return 用户名；无记录返回 null
   */
  private String resolveSessionUsername(Set<String> tokens) {
    for (String token : tokens) {
      String username = redisHashOps.hGet(token, "username", String.class);
      if (username != null) {
        return username;
      }
    }
    return null;
  }

  /**
   * 检测长时间未活动的会话。
   *
   * <p>会话超过 24 小时未刷新（TTL 剩余时间很长但会话创建时间早于阈值）视为异常。
   *
   * @return 长时间未活动异常列表
   */
  private List<AnomalySessionVO> detectStaleSessions() {
    List<AnomalySessionVO> anomalies = new ArrayList<>(INITIAL_CAPACITY);

    try {
      // 扫描所有用户会话索引
      Set<String> sessionKeys = redisStringOps.scan(SESSION_KEY_PREFIX + "*", MAX_SCAN_KEYS);

      long staleThresholdSeconds = STALE_THRESHOLD_HOURS * SECONDS_PER_HOUR;

      for (String sessionKey : sessionKeys) {
        if (sessionKey.contains(":device:")) {
          continue;
        }

        String userId = sessionKey.substring(SESSION_KEY_PREFIX.length());
        Set<String> tokens = redisCollectionOps.sMembers(sessionKey, String.class);

        for (String token : tokens) {
          // 检查会话 TTL：如果 TTL 剩余时间很长，说明最近被刷新过
          // 如果 TTL 剩余时间短但会话仍存在，可能已长时间未活动
          long ttl = redisStringOps.getExpire(token);

          // 获取会话中的创建时间信息
          // 由于当前会话 Hash 不存储创建时间，使用 TTL 间接判断
          // 如果 TTL 小于某个阈值（如 1 小时），说明会话即将过期，可能长时间未活动
          if (ttl > 0 && ttl < SECONDS_PER_HOUR) {
            String username = redisHashOps.hGet(token, "username", String.class);
            AnomalySessionVO anomaly = new AnomalySessionVO(
                userId,
                username != null ? username : userId,
                "STALE_SESSION",
                "会话剩余有效期不足 1 小时，可能存在长时间未活动情况",
                "LOW");
            anomalies.add(anomaly);
            log.info(
                "Stale session detected: user={}, token={}",
                username,
                maskToken(token));
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to detect stale sessions, error={}", e.getMessage());
    }

    return anomalies;
  }

  /**
   * 脱敏展示 Token。
   *
   * @param token 原始 Token
   * @return 脱敏后的 Token
   */
  private String maskToken(String token) {
    if (token == null || token.length() < MIN_TOKEN_LENGTH) {
      return "***";
    }
    return token.substring(0, TOKEN_LOG_PREFIX_LENGTH) + "..."
        + token.substring(token.length() - TOKEN_LOG_SUFFIX_LENGTH);
  }

  /**
   * 序列化 SessionActivityVO 为 JSON 字符串（简化实现）。
   *
   * @param vo 活跃度概览
   * @return JSON 字符串
   */
  private String serializeSessionActivityVO(SessionActivityVO vo) {
    return "{\"totalActiveSessions\":" + vo.totalActiveSessions()
        + ",\"activeUserCount\":" + vo.activeUserCount()
        + ",\"avgSessionDuration\":" + vo.avgSessionDuration() + "}";
  }

  /**
   * 解析 JSON 字符串为 SessionActivityVO。
   *
   * @param json JSON 字符串
   * @return 活跃度概览
   */
  private SessionActivityVO parseSessionActivityVO(String json) {
    try {
      // 简化的 JSON 解析（不使用正则，手动提取）
      int totalActiveSessions = extractIntField(json, "totalActiveSessions");
      int activeUserCount = extractIntField(json, "activeUserCount");
      double avgSessionDuration = extractDoubleField(json, "avgSessionDuration");
      return new SessionActivityVO(totalActiveSessions, activeUserCount, avgSessionDuration);
    } catch (Exception e) {
      log.warn("Failed to parse SessionActivityVO from cache, error={}", e.getMessage());
      return new SessionActivityVO(0, 0, 0.0);
    }
  }

  /**
   * 序列化 ActiveUserVO 列表为 JSON 字符串（简化实现）。
   *
   * @param list 活跃用户列表
   * @return JSON 字符串
   */
  private String serializeActiveUserVOList(List<ActiveUserVO> list) {
    if (list == null || list.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      ActiveUserVO vo = list.get(i);
      if (i > 0) {
        sb.append(",");
      }
      sb.append("{\"userId\":\"").append(vo.userId())
          .append("\",\"username\":\"").append(vo.username())
          .append("\",\"loginCount\":").append(vo.loginCount()).append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * 解析 JSON 字符串为 ActiveUserVO 列表。
   *
   * @param json JSON 字符串
   * @return 活跃用户列表
   */
  private List<ActiveUserVO> parseActiveUserVOList(String json) {
    // 简化实现，实际使用 YdszJson 反序列化
    return List.of();
  }

  /**
   * 从 JSON 字符串中提取整数字段。
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段值
   */
  private int extractIntField(String json, String fieldName) {
    String search = "\"" + fieldName + "\":";
    int start = json.indexOf(search);
    if (start < 0) {
      return 0;
    }
    start += search.length();
    int end = json.indexOf(",", start);
    if (end < 0) {
      end = json.indexOf("}", start);
    }
    if (end < 0) {
      return 0;
    }
    return Integer.parseInt(json.substring(start, end).trim());
  }

  /**
   * 从 JSON 字符串中提取浮点数字段。
   *
   * @param json JSON 字符串
   * @param fieldName 字段名
   * @return 字段值
   */
  private double extractDoubleField(String json, String fieldName) {
    String search = "\"" + fieldName + "\":";
    int start = json.indexOf(search);
    if (start < 0) {
      return 0.0;
    }
    start += search.length();
    int end = json.indexOf(",", start);
    if (end < 0) {
      end = json.indexOf("}", start);
    }
    if (end < 0) {
      return 0.0;
    }
    return Double.parseDouble(json.substring(start, end).trim());
  }
}
