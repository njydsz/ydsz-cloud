package com.njydsz.userinfo.server.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.LoginFailDistributionVO;
import com.njydsz.userinfo.domain.vo.LoginSuccessRateVO;
import com.njydsz.userinfo.domain.vo.MfaCoverageVO;
import com.njydsz.userinfo.domain.vo.RiskLevelDistributionVO;
import com.njydsz.userinfo.domain.vo.SecurityDashboardVO;
import com.njydsz.userinfo.domain.vo.SecurityEventVO;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

/**
 * 安全仪表盘服务。
 *
 * <p>为管理员安全仪表盘提供聚合安全指标数据，包括：
 *
 * <ul>
 *   <li>仪表盘总览：用户统计、在线会话、MFA覆盖率、今日登录成功率等</li>
 *   <li>登录成功率趋势：按日期范围查询成功/失败分布</li>
 *   <li>登录失败原因分布：按失败原因聚合统计</li>
 *   <li>MFA覆盖率统计：平台用户MFA启用比例</li>
 *   <li>风险等级分布：按风险等级聚合用户数量</li>
 *   <li>最近安全事件：异常登录、账号锁定等安全事件</li>
 * </ul>
 *
 * <p><b>数据策略：</b>
 *
 * <ul>
 *   <li>实时数据（在线会话数、今日登录次数）优先从 Redis 读取</li>
 *   <li>历史数据（登录趋势、失败分布）从数据库查询</li>
 *   <li>仪表盘总览数据使用缓存（TTL 5 分钟）减少数据库压力</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityDashboardService {

  /** 仪表盘缓存 Key */
  private static final String DASHBOARD_CACHE_KEY = "userinfo:security:dashboard";

  /** 仪表盘缓存 TTL（秒）：5 分钟 */
  private static final long DASHBOARD_CACHE_TTL = 300;

  /** MFA 启用用户数 Redis Key */
  private static final String MFA_ENABLED_COUNT_KEY = "userinfo:mfa:enabled:count";

  /** 今日登录成功次数 Redis Key 前缀 */
  private static final String TODAY_LOGIN_SUCCESS_KEY = "userinfo:login:success:today";

  /** 今日登录失败次数 Redis Key 前缀 */
  private static final String TODAY_LOGIN_FAIL_KEY = "userinfo:login:fail:today";

  private final UserAccountRepository userAccountRepository;
  private final UserLoginHistoryRepository userLoginHistoryRepository;
  private final RedisStringOps redisStringOps;
  private final UserInfoMetrics userInfoMetrics;

  /**
   * 获取完整仪表盘数据。
   *
   * <p>优先从 Redis 缓存读取，缓存未命中时实时计算并写入缓存（TTL 5 分钟）。
   *
   * @return 安全仪表盘总览数据
   */
  public SecurityDashboardVO getDashboard() {
    // 尝试从缓存读取
    try {
      String cached = redisStringOps.get(DASHBOARD_CACHE_KEY, String.class);
      if (cached != null && !cached.isBlank()) {
        log.debug("Security dashboard cache hit");
        return YdszJson.fromJson(cached, SecurityDashboardVO.class);
      }
    } catch (Exception e) {
      log.warn("Failed to read dashboard cache, error={}", e.getMessage(), e);
    }

    // 实时计算
    SecurityDashboardVO dashboard = computeDashboard();

    // 写入缓存
    try {
      redisStringOps.set(DASHBOARD_CACHE_KEY, YdszJson.toJson(dashboard), DASHBOARD_CACHE_TTL);
    } catch (Exception e) {
      log.warn("Failed to write dashboard cache, error={}", e.getMessage(), e);
    }

    return dashboard;
  }

  /**
   * 获取登录成功率趋势。
   *
   * <p>查询指定日期范围内每日的登录成功/失败次数，计算成功率。
   * 优先从 Redis 读取预聚合数据，Redis 未命中时从数据库查询。
   *
   * @param start 起始日期（含）
   * @param end 结束日期（含）
   * @return 每日登录成功率列表
   */
  public List<LoginSuccessRateVO> getLoginSuccessRate(LocalDate start, LocalDate end) {
    if (start == null || end == null || start.isAfter(end)) {
      log.warn("Invalid date range: start={}, end={}", start, end);
      return List.of();
    }

    List<LoginSuccessRateVO> result = new ArrayList<>(16);
    LocalDate current = start;

    while (!current.isAfter(end)) {
      LocalDateTime dayStart = current.atStartOfDay();
      LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();

      long successCount = countLoginsByResult(dayStart, dayEnd, "SUCCESS");
      long failCount = countLoginsByResult(dayStart, dayEnd, "FAILED");
      long total = successCount + failCount;
      double successRate = total > 0 ? (double) successCount / total : 0.0;

      result.add(new LoginSuccessRateVO(current, successCount, failCount, successRate));
      current = current.plusDays(1);
    }

    return result;
  }

  /**
   * 获取登录失败原因分布。
   *
   * <p>统计指定日期内各失败原因的分布情况，用于饼图展示。
   * 优先从 Redis 读取预聚合数据，Redis 未命中时从数据库查询。
   *
   * @param date 统计日期
   * @return 失败原因分布列表
   */
  public List<LoginFailDistributionVO> getLoginFailDistribution(LocalDate date) {
    if (date == null) {
      date = LocalDate.now();
    }

    LocalDateTime dayStart = date.atStartOfDay();
    LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

    // 查询当日所有失败记录
    Map<String, Integer> reasonCountMap = new HashMap<>(8);
    int totalFails = (int) countLoginsByResult(dayStart, dayEnd, "FAILED");

    if (totalFails == 0) {
      return List.of();
    }

    // 按失败原因分类统计（基于 failReason 字段）
    reasonCountMap.put("密码错误", countLoginsByFailReason(dayStart, dayEnd, "PASSWORD_INCORRECT"));
    reasonCountMap.put("账号锁定", countLoginsByFailReason(dayStart, dayEnd, "ACCOUNT_LOCKED"));
    reasonCountMap.put("MFA失败", countLoginsByFailReason(dayStart, dayEnd, "MFA_INVALID"));
    reasonCountMap.put("IP封禁", countLoginsByFailReason(dayStart, dayEnd, "IP_BLOCKED"));
    reasonCountMap.put("Token无效", countLoginsByFailReason(dayStart, dayEnd, "TOKEN_INVALID"));

    List<LoginFailDistributionVO> result = new ArrayList<>(8);
    int accountedFails = 0;
    for (Map.Entry<String, Integer> entry : reasonCountMap.entrySet()) {
      if (entry.getValue() > 0) {
        accountedFails += entry.getValue();
        double percentage = (double) entry.getValue() / totalFails;
        result.add(new LoginFailDistributionVO(entry.getKey(), entry.getValue(), percentage));
      }
    }
    // 其他原因（未分类的失败）
    int otherFails = totalFails - accountedFails;
    if (otherFails > 0) {
      double percentage = (double) otherFails / totalFails;
      result.add(new LoginFailDistributionVO("其他", otherFails, percentage));
    }

    return result;
  }

  /**
   * 获取 MFA 覆盖率统计。
   *
   * <p>统计平台用户中已启用 MFA 的比例。MFA 启用用户数从 Redis 计数器读取。
   *
   * @return MFA 覆盖率统计
   */
  public MfaCoverageVO getMfaCoverage() {
    long totalUsers = userAccountRepository.count(new UserAccountPageQuery());

    long mfaEnabledUsers = 0;
    try {
      String countStr = redisStringOps.get(MFA_ENABLED_COUNT_KEY, String.class);
      if (countStr != null && !countStr.isBlank()) {
        mfaEnabledUsers = Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read MFA enabled count from Redis, error={}", e.getMessage(), e);
    }

    double coverageRate = totalUsers > 0 ? (double) mfaEnabledUsers / totalUsers : 0.0;
    return new MfaCoverageVO(totalUsers, mfaEnabledUsers, coverageRate);
  }

  /**
   * 获取风险等级分布。
   *
   * <p>基于数据库实时数据评估用户风险等级：
   *
   * <ul>
   *   <li><b>高风险</b>：当前处于锁定或封禁状态的用户</li>
   *   <li><b>中风险</b>：近 24 小时内至少有一次登录失败记录的用户（去重）</li>
   *   <li><b>低风险</b>：总启用用户数 - 高风险 - 中风险</li>
   * </ul>
   *
   * @return 风险等级分布
   */
  public RiskLevelDistributionVO getRiskLevelDistribution() {
    // 高风险：当前被锁定或封禁的用户
    long lockedUsers = countLockedUsers();
    long bannedUsers = countBannedUsers();
    long highRisk = lockedUsers + bannedUsers;

    // 中风险：近 24 小时内有登录失败记录的去重用户数
    LocalDateTime dayStart = LocalDateTime.now().minusHours(24);
    LocalDateTime dayEnd = LocalDateTime.now();
    long mediumRisk = 0;
    try {
      mediumRisk = userLoginHistoryRepository.countDistinctUsersWithFailures(dayStart, dayEnd);
    } catch (Exception e) {
      log.warn("Failed to count users with recent failures: {}", e.getMessage(), e);
    }

    // 低风险：总启用用户数 - 高风险 - 中风险
    long totalUsers = userAccountRepository.count(new UserAccountPageQuery());
    long lowRisk = Math.max(totalUsers - highRisk - mediumRisk, 0);

    return new RiskLevelDistributionVO((int) highRisk, (int) mediumRisk, (int) lowRisk);
  }

  /**
   * 获取最近安全事件。
   *
   * <p>查询最近的登录失败记录，转换为安全事件展示。
   *
   * @param limit 返回记录数上限
   * @return 最近安全事件列表
   */
  public List<SecurityEventVO> getRecentSecurityEvents(int limit) {
    if (limit <= 0) {
      limit = 20;
    }
    limit = Math.min(limit, 100);

    List<SecurityEventVO> events = new ArrayList<>(16);

    try {
      // 查询最近的登录失败记录
      LocalDateTime since = LocalDateTime.now().minusHours(24);
      List<UserLoginHistoryVO> recentFails = findRecentFailedLogins(since, limit);

      for (UserLoginHistoryVO record : recentFails) {
        String eventType = classifySecurityEvent(record);
        events.add(new SecurityEventVO(
            eventType,
            record.getUsername(),
            record.getLoginIp(),
            record.getCreatedAt(),
            buildEventDescription(record, eventType)));
      }
    } catch (Exception e) {
      log.warn("Failed to query recent security events, error={}", e.getMessage(), e);
    }

    return events;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 实时计算仪表盘数据。
   *
   * @return 安全仪表盘总览数据
   */
  private SecurityDashboardVO computeDashboard() {
    // 用户统计
    long totalUsers = userAccountRepository.count(new UserAccountPageQuery());

    UserAccountPageQuery activeQuery = new UserAccountPageQuery();
    activeQuery.setStatus("1");
    long activeUsers = userAccountRepository.count(activeQuery);

    // 在线会话数（从 Redis 计数器读取）
    long onlineUsers = getOnlineSessionCount();

    // MFA 启用用户数
    long mfaEnabledUsers = 0;
    try {
      String countStr = redisStringOps.get(MFA_ENABLED_COUNT_KEY, String.class);
      if (countStr != null && !countStr.isBlank()) {
        mfaEnabledUsers = Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read MFA count, error={}", e.getMessage(), e);
    }

    // 锁定用户数
    long lockedUsers = countLockedUsers();

    // 封禁用户数
    long bannedUsers = countBannedUsers();

    // 今日登录统计
    long todayLoginCount = getTodayLoginSuccessCount();
    long todayFailCount = getTodayLoginFailCount();
    long todayTotal = todayLoginCount + todayFailCount;
    double todayLoginSuccessRate = todayTotal > 0 ? (double) todayLoginCount / todayTotal : 0.0;

    // 平均风险评分（简化计算：基于锁定和封禁用户比例）
    double riskScoreAverage = calculateAverageRiskScore(totalUsers, lockedUsers, bannedUsers);

    return new SecurityDashboardVO(
        totalUsers,
        activeUsers,
        onlineUsers,
        mfaEnabledUsers,
        lockedUsers,
        bannedUsers,
        todayLoginCount,
        todayLoginSuccessRate,
        riskScoreAverage);
  }

  /**
   * 从 Redis 计数器读取当前在线会话总数。
   *
   * @return 当前在线会话数
   */
  private long getOnlineSessionCount() {
    try {
      String value = redisStringOps.get("userinfo:session:total", String.class);
      if (value != null && !value.isBlank()) {
        return Long.parseLong(value);
      }
    } catch (Exception e) {
      log.warn("Failed to read online session count, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 统计当前处于锁定状态的用户数。
   *
   * <p>锁定状态判断：lockedUntil 字段非空且晚于当前时间。
   * 从数据库实时查询，确保数据准确性。
   *
   * @return 锁定用户数
   */
  private long countLockedUsers() {
    try {
      // 使用 Redis 缓存（TTL 5 分钟）
      String countStr = redisStringOps.get("userinfo:security:locked:count", String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read locked user count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      long count = userAccountRepository.countLockedUsers();
      // 回填缓存（TTL 5 分钟）
      try {
        redisStringOps.set("userinfo:security:locked:count", String.valueOf(count), 300);
      } catch (Exception ex) {
        log.warn("Failed to cache locked user count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query locked user count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 统计当前处于封禁状态的用户数。
   *
   * <p>封禁状态判断：banType 非空且（永久封禁或临时封禁未过期）。
   * 从数据库实时查询，确保数据准确性。
   *
   * @return 封禁用户数
   */
  private long countBannedUsers() {
    try {
      String countStr = redisStringOps.get("userinfo:security:banned:count", String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read banned user count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      long count = userAccountRepository.countBannedUsers();
      // 回填缓存（TTL 5 分钟）
      try {
        redisStringOps.set("userinfo:security:banned:count", String.valueOf(count), 300);
      } catch (Exception ex) {
        log.warn("Failed to cache banned user count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query banned user count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 获取今日登录成功次数。
   *
   * <p>优先从 Redis 读取，Redis 未命中时从数据库查询。
   *
   * @return 今日登录成功次数
   */
  private long getTodayLoginSuccessCount() {
    // 优先从 Redis 读取
    try {
      String today = LocalDate.now().toString();
      String countStr = redisStringOps.get(TODAY_LOGIN_SUCCESS_KEY + ":" + today, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read today login success count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      LocalDateTime dayStart = LocalDate.now().atStartOfDay();
      LocalDateTime dayEnd = LocalDate.now().plusDays(1).atStartOfDay();
      long count = userLoginHistoryRepository.countByResultAndTimeRange(dayStart, dayEnd, "SUCCESS");
      // 回填缓存（TTL 5 分钟）
      try {
        String today = LocalDate.now().toString();
        redisStringOps.set(TODAY_LOGIN_SUCCESS_KEY + ":" + today, String.valueOf(count), 300);
      } catch (Exception ex) {
        log.warn("Failed to cache today login success count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query today login success count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 获取今日登录失败次数。
   *
   * <p>优先从 Redis 读取，Redis 未命中时从数据库查询。
   *
   * @return 今日登录失败次数
   */
  private long getTodayLoginFailCount() {
    // 优先从 Redis 读取
    try {
      String today = LocalDate.now().toString();
      String countStr = redisStringOps.get(TODAY_LOGIN_FAIL_KEY + ":" + today, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read today login fail count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      LocalDateTime dayStart = LocalDate.now().atStartOfDay();
      LocalDateTime dayEnd = LocalDate.now().plusDays(1).atStartOfDay();
      long count = userLoginHistoryRepository.countByResultAndTimeRange(dayStart, dayEnd, "FAILED");
      // 回填缓存（TTL 5 分钟）
      try {
        String today = LocalDate.now().toString();
        redisStringOps.set(TODAY_LOGIN_FAIL_KEY + ":" + today, String.valueOf(count), 300);
      } catch (Exception ex) {
        log.warn("Failed to cache today login fail count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query today login fail count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 计算平均风险评分。
   *
   * <p>简化计算：基于锁定和封禁用户比例估算平台平均风险。
   *
   * @param totalUsers 总用户数
   * @param lockedUsers 锁定用户数
   * @param bannedUsers 封禁用户数
   * @return 平均风险评分（0-100）
   */
  private double calculateAverageRiskScore(long totalUsers, long lockedUsers, long bannedUsers) {
    if (totalUsers <= 0) {
      return 0.0;
    }
    // 高风险用户权重 80，中风险用户权重 40，低风险用户权重 10
    double weightedScore = (bannedUsers * 80.0 + lockedUsers * 40.0
        + (totalUsers - bannedUsers - lockedUsers) * 10.0);
    return Math.min(weightedScore / totalUsers, 100.0);
  }

  /**
   * 按登录结果统计指定时间范围内的登录次数。
   *
   * <p>优先从 Redis 读取预聚合的统计数据，Redis 未命中时从数据库查询并回填缓存。
   *
   * @param start 起始时间
   * @param end 结束时间
   * @param result 登录结果（SUCCESS/FAILED）
   * @return 登录次数
   */
  private long countLoginsByResult(LocalDateTime start, LocalDateTime end, String result) {
    // 优先从 Redis 读取
    try {
      String dateKey = start.toLocalDate().toString();
      String redisKey = "userinfo:login:count:" + result.toLowerCase() + ":" + dateKey;
      String countStr = redisStringOps.get(redisKey, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Long.parseLong(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read login count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      long count = userLoginHistoryRepository.countByResultAndTimeRange(start, end, result);
      // 回填缓存（TTL 10 分钟）
      try {
        String dateKey = start.toLocalDate().toString();
        String redisKey = "userinfo:login:count:" + result.toLowerCase() + ":" + dateKey;
        redisStringOps.set(redisKey, String.valueOf(count), 600);
      } catch (Exception ex) {
        log.warn("Failed to cache login count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query login count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  /**
   * 按失败原因统计指定时间范围内的登录失败次数。
   *
   * <p>优先从 Redis 读取预聚合的统计数据，Redis 未命中时从数据库查询。
   *
   * @param start 起始时间
   * @param end 结束时间
   * @param failReason 失败原因
   * @return 失败次数
   */
  private int countLoginsByFailReason(LocalDateTime start, LocalDateTime end, String failReason) {
    // 优先从 Redis 读取
    try {
      String dateKey = start.toLocalDate().toString();
      String redisKey = "userinfo:login:fail:reason:" + failReason + ":" + dateKey;
      String countStr = redisStringOps.get(redisKey, String.class);
      if (countStr != null && !countStr.isBlank()) {
        return Integer.parseInt(countStr);
      }
    } catch (Exception e) {
      log.warn("Failed to read fail reason count from Redis, error={}", e.getMessage(), e);
    }

    // Redis 未命中，从数据库查询
    try {
      int count = userLoginHistoryRepository.countByFailReasonAndTimeRange(start, end, failReason);
      // 回填缓存（TTL 10 分钟）
      try {
        String dateKey = start.toLocalDate().toString();
        String redisKey = "userinfo:login:fail:reason:" + failReason + ":" + dateKey;
        redisStringOps.set(redisKey, String.valueOf(count), 600);
      } catch (Exception ex) {
        log.warn("Failed to cache fail reason count, error={}", ex.getMessage());
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to query fail reason count from DB, error={}", e.getMessage(), e);
    }
    return 0;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 查询指定时间之后的最近登录失败记录。
   *
   * @param since 起始时间
   * @param limit 返回记录数上限
   * @return 登录失败记录列表
   */
  private List<UserLoginHistoryVO> findRecentFailedLogins(LocalDateTime since, int limit) {
    log.debug("Querying recent failed logins since={}, limit={}", since, limit);
    return userLoginHistoryRepository.findRecentFailedLogins(since, limit);
  }

  /**
   * 根据登录记录分类安全事件类型。
   *
   * @param record 登录历史记录
   * @return 安全事件类型编码
   */
  private String classifySecurityEvent(UserLoginHistoryVO record) {
    if (record.getFailReason() == null) {
      return "LOGIN_FAIL";
    }
    return switch (record.getFailReason()) {
      case "PASSWORD_INCORRECT" -> "LOGIN_FAIL_PASSWORD";
      case "ACCOUNT_LOCKED" -> "ACCOUNT_LOCKED";
      case "MFA_INVALID" -> "LOGIN_FAIL_MFA";
      case "IP_BLOCKED" -> "IP_BLOCKED";
      case "TOKEN_INVALID" -> "TOKEN_INVALID";
      default -> "LOGIN_FAIL";
    };
  }

  /**
   * 构建安全事件描述。
   *
   * @param record 登录历史记录
   * @param eventType 事件类型
   * @return 事件描述
   */
  private String buildEventDescription(UserLoginHistoryVO record, String eventType) {
    return switch (eventType) {
      case "LOGIN_FAIL_PASSWORD" ->
          "用户 " + record.getUsername() + " 从 IP " + record.getLoginIp() + " 登录失败：密码错误";
      case "ACCOUNT_LOCKED" ->
          "用户 " + record.getUsername() + " 账号已被锁定";
      case "LOGIN_FAIL_MFA" ->
          "用户 " + record.getUsername() + " 从 IP " + record.getLoginIp() + " MFA 验证失败";
      case "IP_BLOCKED" ->
          "IP " + record.getLoginIp() + " 因多次失败被临时封禁";
      case "TOKEN_INVALID" ->
          "用户 " + record.getUsername() + " Token 验证失败";
      default ->
          "用户 " + record.getUsername() + " 从 IP " + record.getLoginIp() + " 登录失败";
    };
  }
}
