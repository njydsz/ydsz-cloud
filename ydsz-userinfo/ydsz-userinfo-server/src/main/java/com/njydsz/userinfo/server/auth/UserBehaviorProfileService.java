package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 用户行为画像服务（P1-1 UEBA 自适应风险引擎）。
 *
 * <p>为每个用户维护行为基线：常用登录时间段、常用 IP 段、设备指纹集合。
 * 当本次登录偏离基线时，动态增加风险评分。
 *
 * <p><b>行为基线数据结构（Redis Hash）：</b>
 *
 * <ul>
 *   <li>key: {@code userinfo:behavior:profile:{userId}}</li>
 *   <li>fields: {@code commonHours}（常用小时位图）、{@code knownIps}（已知 IP Set）、
 *       {@code knownDevices}（设备指纹 Set）、{@code lastLoginTime}、{@code totalLogins}</li>
 *   <li>TTL: 90 天（无登录后自动过期）</li>
 * </ul>
 *
 * <p><b>自适应评分逻辑：</b>
 *
 * <ul>
 *   <li>在基线构建期（前 N 次登录），使用默认权重</li>
 *   <li>在基线稳定后（≥ N 次登录），根据以下偏离度动态评测风险：
 *     <ul>
 *       <li>登录时间不在常用时段 → 加权风险</li>
 *       <li>IP 不在已知 IP 集合 → 加权风险</li>
 *       <li>设备不在已知设备集合 → 加权风险（与现有 RiskScoringService 互补）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>使用方式：</b>在 {@link RiskScoringService#evaluateRisk} 后调用
 * {@link #evaluateBehaviorDeviation(String, String, String)} 获取 UBEA 评分加成。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorProfileService {

  /** 行为基线 Redis Hash key 前缀 */
  private static final String PROFILE_KEY_PREFIX = "userinfo:behavior:profile:";

  /** 已知 IP Set key 前缀 */
  private static final String KNOWN_IPS_KEY_PREFIX = "userinfo:behavior:known-ips:";

  /** 已知设备 Set key 前缀 */
  private static final String KNOWN_DEVICES_KEY_PREFIX = "userinfo:behavior:known-devices:";

  /** 用户行为数据 TTL（秒）：90 天 */
  private static final long PROFILE_TTL_SECONDS = 7776000L;

  /** 基线稳定所需的最小登录次数 */
  private static final int BASELINE_STABLE_THRESHOLD = 10;

  /** 设备指纹基础长度（UA 前 N 字符） */
  private static final int DEVICE_FINGERPRINT_BASE_LENGTH = 64;

  /** 新用户免报阈值（登录次数低于此值不产生偏离告警） */
  private static final int NEW_USER_GRACE_THRESHOLD = 3;

  /** 常用时段记录窗口：记录最近 30 天的登录小时分布 */
  private static final int HOUR_DISTRIBUTION_SIZE = 24;

  /** 已知 IP 集合最大容量（防止无限增长） */
  private static final int MAX_KNOWN_IPS = 50;

  /** 已知设备集合最大容量 */
  private static final int MAX_KNOWN_DEVICES = 20;

  /** 行为偏离基础风险分（每项偏离增加的分值） */
  private static final int DEVIATION_BASE_SCORE = 15;

  /** 时间偏离权重（0-100）：登录时间不在常用时段时增加的额外风险分比例 */
  private static final int TIME_DEVIATION_WEIGHT = 15;

  /** IP 偏离权重（0-100）：IP 不在已知集合时增加的额外风险分比例 */
  private static final int IP_DEVIATION_WEIGHT = 20;

  /** 设备偏离权重（0-100）：设备不在已知集合时增加的额外风险分比例 */
  private static final int DEVICE_DEVIATION_WEIGHT = 15;

  /** 行为偏离因子列表初始容量 */
  private static final int BEHAVIOR_FACTORS_INITIAL_CAPACITY = 4;

  private final RedisStringOps redisStringOps;
  private final RedisHashOps redisHashOps;
  private final RedisCollectionOps redisCollectionOps;

  /**
   * 记录用户登录行为（更新行为基线）。
   *
   * <p>每次登录成功时调用，更新用户的行为基线数据。
   *
   * @param userId 用户 ID
   * @param loginIp 登录 IP
   * @param userAgent User-Agent 字符串
   * @param loginTime 登录时间
   */
  public void recordSuccessfulLogin(String userId, String loginIp, String userAgent,
      LocalDateTime loginTime) {
    String profileKey = PROFILE_KEY_PREFIX + userId;

    try {
      // 1. 更新登录次数和最后登录时间
      redisHashOps.hSet(profileKey, "totalLogins",
          String.valueOf(getTotalLogins(userId) + 1));
      redisHashOps.hSet(profileKey, "lastLoginTime", loginTime.toString());
      redisHashOps.hSet(profileKey, "lastLoginIp", loginIp != null ? loginIp : "");

      // 2. 更新常用时段分布
      updateHourDistribution(userId, loginTime.getHour());

      // 3. 更新已知 IP 集合
      if (loginIp != null && !loginIp.isBlank()) {
        String ipsKey = KNOWN_IPS_KEY_PREFIX + userId;
        redisCollectionOps.sAdd(ipsKey, loginIp);
        trimSet(ipsKey, MAX_KNOWN_IPS);
      }

      // 4. 更新已知设备集合
      String deviceFingerprint = extractDeviceFingerprint(userAgent);
      if (deviceFingerprint != null) {
        String devicesKey = KNOWN_DEVICES_KEY_PREFIX + userId;
        redisCollectionOps.sAdd(devicesKey, deviceFingerprint);
        trimSet(devicesKey, MAX_KNOWN_DEVICES);
      }

      // 5. 刷新 TTL
      redisStringOps.expire(profileKey, PROFILE_TTL_SECONDS);
      redisStringOps.expire(KNOWN_IPS_KEY_PREFIX + userId, PROFILE_TTL_SECONDS);
      redisStringOps.expire(KNOWN_DEVICES_KEY_PREFIX + userId, PROFILE_TTL_SECONDS);

    } catch (Exception e) {
      log.warn("记录行为基线失败: userId={}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 评估本次登录的行为偏离风险。
   *
   * <p>对比本次登录与用户历史基线，计算行为偏离风险加成。
   *
   * @param userId 用户 ID
   * @param loginIp 登录 IP
   * @param userAgent User-Agent 字符串
   * @return 行为偏离风险评分结果（含加分和风险因子列表）
   */
  public BehaviorDeviationResult evaluateBehaviorDeviation(String userId, String loginIp,
      String userAgent) {
    List<String> factors = new ArrayList<>(BEHAVIOR_FACTORS_INITIAL_CAPACITY);
    int totalDeviation = 0;

    // 基线稳定度检查
    long totalLogins = getTotalLogins(userId);
    if (totalLogins < BASELINE_STABLE_THRESHOLD) {
      // 基线构建期，降低偏离评分要求（避免误报新用户）
      if (totalLogins < NEW_USER_GRACE_THRESHOLD) {
        return new BehaviorDeviationResult(0, factors, false);
      }
    }

    // 1. 时间偏离检测
    int hourDeviation = evaluateTimeDeviation(userId, LocalDateTime.now().getHour());
    if (hourDeviation > 0) {
      totalDeviation += hourDeviation;
      factors.add("非常规时段登录(+" + hourDeviation + ")");
    }

    // 2. IP 偏离检测
    int ipDeviation = evaluateIpDeviation(userId, loginIp);
    if (ipDeviation > 0) {
      totalDeviation += ipDeviation;
      factors.add("未知IP(+" + ipDeviation + ")");
    }

    // 3. 设备偏离检测
    int deviceDeviation = evaluateDeviceDeviation(userId, userAgent);
    if (deviceDeviation > 0) {
      totalDeviation += deviceDeviation;
      factors.add("未知设备(+" + deviceDeviation + ")");
    }

    boolean isBaselineStable = totalLogins >= BASELINE_STABLE_THRESHOLD;

    return new BehaviorDeviationResult(totalDeviation, factors, isBaselineStable);
  }

  /**
   * 获取用户行为摘要（用于管理界面/审计）。
   *
   * @param userId 用户 ID
   * @return 行为摘要 Map
   */
  public Map<String, String> getBehaviorSummary(String userId) {
    String profileKey = PROFILE_KEY_PREFIX + userId;
    Map<String, String> all = redisHashOps.hGetAll(profileKey, String.class);
    Set<String> ips = redisCollectionOps.sMembers(KNOWN_IPS_KEY_PREFIX + userId, String.class);
    Map<String, String> result = new HashMap<>(all);
    result.put("knownIpCount", String.valueOf(ips.size()));
    return result;
  }

  /**
   * 获取用户总登录次数。
   *
   * @param userId 用户 ID
   * @return 登录次数，无记录返回 0
   */
  public long getTotalLogins(String userId) {
    String profileKey = PROFILE_KEY_PREFIX + userId;
    try {
      String value = redisHashOps.hGet(profileKey, "totalLogins", String.class);
      return value != null ? Long.parseLong(value) : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * 更新时间分布基线（将当前小时标记为"常用"）。
   *
   * @param userId 用户 ID
   * @param hour 当前小时（0-23）
   */
  private void updateHourDistribution(String userId, int hour) {
    String profileKey = PROFILE_KEY_PREFIX + userId;
    // 使用位图标记常用小时：每位为 1 表示对应小时有过登录
    try {
      String current = redisHashOps.hGet(profileKey, "hourBitmap", String.class);
      long bitmap = current != null ? Long.parseLong(current) : 0L;
      bitmap |= (1L << hour);
      redisHashOps.hSet(profileKey, "hourBitmap", String.valueOf(bitmap));
    } catch (Exception e) {
      log.warn("更新时段分布失败: userId={}, hour={}", userId, hour);
    }
  }

  /**
   * 评估时间偏离。
   *
   * @param userId 用户 ID
   * @param currentHour 当前小时（0-23）
   * @return 偏离分数（0 表示正常）
   */
  private int evaluateTimeDeviation(String userId, int currentHour) {
    String profileKey = PROFILE_KEY_PREFIX + userId;
    try {
      String bitmapStr = redisHashOps.hGet(profileKey, "hourBitmap", String.class);
      if (bitmapStr == null) {
        return 0;
      }
      long bitmap = Long.parseLong(bitmapStr);
      // 检查当前小时是否在常用位图中
      if ((bitmap & (1L << currentHour)) == 0) {
        // 非常规时段
        return TIME_DEVIATION_WEIGHT;
      }
    } catch (Exception e) {
      log.warn("评估时间偏离失败: userId={}", userId);
    }
    return 0;
  }

  /**
   * 评估 IP 偏离。
   *
   * @param userId 用户 ID
   * @param loginIp 登录 IP
   * @return 偏离分数
   */
  private int evaluateIpDeviation(String userId, String loginIp) {
    if (loginIp == null || loginIp.isBlank()) {
      return 0;
    }
    String ipsKey = KNOWN_IPS_KEY_PREFIX + userId;
    try {
      Set<String> knownIps = redisCollectionOps.sMembers(ipsKey, String.class);
      if (knownIps.isEmpty()) {
        return 0; // 无基线数据
      }
      if (!knownIps.contains(loginIp)) {
        return IP_DEVIATION_WEIGHT;
      }
    } catch (Exception e) {
      log.warn("评估 IP 偏离失败: userId={}", userId);
    }
    return 0;
  }

  /**
   * 评估设备偏离。
   *
   * @param userId 用户 ID
   * @param userAgent User-Agent 字符串
   * @return 偏离分数
   */
  private int evaluateDeviceDeviation(String userId, String userAgent) {
    String fingerprint = extractDeviceFingerprint(userAgent);
    if (fingerprint == null) {
      return 0;
    }
    String devicesKey = KNOWN_DEVICES_KEY_PREFIX + userId;
    try {
      // 使用 sIsMember 检查设备指纹是否在已知集合中
      if (!redisCollectionOps.sIsMember(devicesKey, fingerprint)) {
        // 集合非空但设备不在其中 → 偏离
        if (redisCollectionOps.sSize(devicesKey) > 0) {
          return DEVICE_DEVIATION_WEIGHT;
        }
      }
    } catch (Exception e) {
      log.warn("评估设备偏离失败: userId={}", userId);
    }
    return 0;
  }

  /**
   * 提取设备指纹（简化实现：取 User-Agent 的前 32 字符作为指纹）。
   *
   * <p>生产环境应使用更精确的指纹算法（如结合浏览器特征 hash）。
   *
   * @param userAgent User-Agent 字符串
   * @return 设备指纹，UA 为空返回 null
   */
  private String extractDeviceFingerprint(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return null;
    }
    return userAgent.length() > DEVICE_FINGERPRINT_BASE_LENGTH
        ? userAgent.substring(0, DEVICE_FINGERPRINT_BASE_LENGTH)
        : userAgent;
  }

  /**
   * 控制 Set 集合大小（保持最近 N 个元素）。
   *
   * <p>Redis Set 不支持直接 LRU，此处通过随机淘汰实现近似效果。
   * 精确实现可使用 Sorted Set + 时间戳。
   *
   * @param key Set key
   * @param maxSize 最大元素数
   */
  private void trimSet(String key, int maxSize) {
    try {
      Long currentSize = redisCollectionOps.sSize(key);
      if (currentSize != null && currentSize > maxSize) {
        // 使用 SRANDMEMBER + SREM 随机淘汰多余元素
        long toRemove = currentSize - maxSize;
        for (long i = 0; i < toRemove; i++) {
          String member = redisCollectionOps.sRandomMember(key, String.class);
          if (member != null) {
            redisCollectionOps.sRem(key, member);
          }
        }
      }
    } catch (Exception e) {
      log.warn("裁剪集合失败: key={}, maxSize={}", key, maxSize);
    }
  }

  /**
   * 行为偏离风险评分结果。
   *
   * @param deviationScore 总偏离风险分（0-50）
   * @param factors 偏离因子列表
   * @param isBaselineStable 基线是否已稳定（≥ BASELINE_STABLE_THRESHOLD 次登录）
   */
  public record BehaviorDeviationResult(
      int deviationScore,
      List<String> factors,
      boolean isBaselineStable) {

    /**
     * 判断是否存在显著行为偏离。     *
     * @return true 表示偏离分数 ≥ DEVIATION_BASE_SCORE
     */
    public boolean hasSignificantDeviation() {
      return deviationScore >= DEVIATION_BASE_SCORE;
    }
  }
}
