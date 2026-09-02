package com.njydsz.message.server.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.message.domain.dto.UserReachProfileDTO;
import com.njydsz.message.server.service.core.ReachStrategyService;

/**
 * 智能触达策略服务实现。
 *
 * <p>P1-8: 基于 Redis 缓存的用户画像数据，综合评分各通道的触达能力。
 *
 * <p>评分公式（满分 100）：
 *
 * <ul>
 *   <li>通道活跃度（40%）：用户在该通道的历史活跃程度
 *   <li>历史打开率（30%）：该通道的历史消息打开率
 *   <li>用户偏好（20%）：用户设置的通道优先级
 *   <li>通道成本（10%）：低成本通道加分
 * </ul>
 *
 * <p>免打扰判断：基于用户偏好中的 DND 配置，结合用户时区判断当前是否在免打扰时段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReachStrategyServiceImpl implements ReachStrategyService {
  /** 默认活跃度得分 */
  private static final double DEFAULT_ACTIVITY_SCORE = 0.5;

  /** 默认打开率 */
  private static final double DEFAULT_OPEN_RATE = 0.3;

  /** 默认偏好得分 */
  private static final double DEFAULT_PREF_SCORE = 0.5;

  /** 默认成本得分 */
  private static final double DEFAULT_COST_SCORE = 0.5;

  /** 活跃度权重 */
  private static final double ACTIVITY_WEIGHT = 0.4;

  /** 打开率权重 */
  private static final double OPEN_RATE_WEIGHT = 0.3;

  /** 偏好权重 */
  private static final double PREF_SCORE_WEIGHT = 0.2;

  /** 成本权重 */
  private static final double COST_SCORE_WEIGHT = 0.1;

  /** 得分放大倍数 */
  private static final double SCORE_SCALE = 100;


  /** Redis 画像缓存 key 前缀 */
  private static final String PROFILE_KEY_PREFIX = "ydsz:reach:profile:";

  /** 默认时区 */
  private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

  /** 通道成本权重（越低成本越高分） */
  private static final Map<String, Double> CHANNEL_COST =
      Map.of(
          "INAPP", 0.1,
          "WEBHOOK", 0.2,
          "DINGTALK", 0.3,
          "WECOM", 0.3,
          "FEISHU", 0.3,
          "EMAIL", 0.4,
          "PUSH", 0.6,
          "SMS", 1.0);

  private final RedisHashOps redisHashOps;

  /** OD-1: DND 逻辑委托给 DndService，消除重复实现 */
  private final DndService dndService;

  /**
   * 获取用户触达画像。
   *
   * <p>优先从 Redis 缓存加载由外部系统写入的画像数据（设备类型/时区/免打扰时段/打开率/通道活跃度）， 解析失败或缓存缺失时降级返回 {@link
   * #defaultProfile()} 默认画像，保证调用方永远拿到可用对象而非 null。
   *
   * @param userId 用户 ID，为空时直接返回默认画像
   * @return 用户触达画像（不会为 null）
   */
  @Override
  public UserReachProfileDTO getProfile(String userId) {
    if (!StringUtils.hasText(userId)) {
      return defaultProfile();
    }
    try {
      // 从 Redis 加载画像（外部系统写入）
      Map<String, String> raw = redisHashOps.hGetAll(PROFILE_KEY_PREFIX + userId, String.class);
      if (raw == null || raw.isEmpty()) {
        return defaultProfile();
      }
      UserReachProfileDTO profile = new UserReachProfileDTO();
      profile.setUserId(userId);
      profile.setDeviceType((String) raw.get("deviceType"));
      profile.setTimezone((String) raw.getOrDefault("timezone", DEFAULT_TIMEZONE));
      profile.setDndStart((String) raw.get("dndStart"));
      profile.setDndEnd((String) raw.get("dndEnd"));
      String openRateStr = (String) raw.get("openRate");
      if (StringUtils.hasText(openRateStr)) {
        profile.setOpenRate(Double.parseDouble(openRateStr));
      }
      String clickRateStr = (String) raw.get("clickRate");
      if (StringUtils.hasText(clickRateStr)) {
        profile.setClickRate(Double.parseDouble(clickRateStr));
      }
      // 解析通道活跃度
      Map<String, Integer> scores = new HashMap<>(16);
      for (Map.Entry<String, String> e : raw.entrySet()) {
        String key = e.getKey();
        if (key.startsWith("score:")) {
          String channel = key.substring("score:".length());
          try {
            scores.put(channel, Integer.parseInt(e.getValue()));
          } catch (NumberFormatException ex) {
            log.debug("[ReachStrategy] 通道活跃度评分解析失败，跳过: channel={}, value={}", key, ex.getMessage());
          }
        }
      }
      profile.setChannelActivityScores(scores);
      return profile;
    } catch (Exception e) {
      log.warn("[ReachStrategy] 画像加载失败,使用默认: userId={} err={}", userId, e.getMessage(), e);
      return defaultProfile();
    }
  }

  /**
   * 选择最优触达通道列表（已按评分降序排列）。
   *
   * <p>基于用户画像对候选通道逐一评分（活跃度/打开率/偏好/成本）， 返回按综合评分从高到低排序的全部通道；空候选直接返回空列表，不会抛异常。
   *
   * @param userId 用户 ID
   * @param channels 候选通道列表（如 SMS/EMAIL/INAPP 等）
   * @param bizType 业务类型，当前留作后续策略扩展点
   * @return 排序后的通道列表（评分高的在前）；无候选时返回空列表
   */
  @Override
  public List<String> selectOptimalChannels(String userId, List<String> channels, String bizType) {
    if (channels == null || channels.isEmpty()) {
      return List.of();
    }
    UserReachProfileDTO profile = getProfile(userId);
    // 计算每个通道的综合评分
    List<ChannelScore> scored = new ArrayList<>(channels.size());
    for (String channel : channels) {
      double score = calculateChannelScore(channel, profile);
      scored.add(new ChannelScore(channel, score));
    }
    // 按评分降序排列
    scored.sort(Comparator.comparingDouble(ChannelScore::score).reversed());
    return scored.stream().map(ChannelScore::channel).toList();
  }

  /**
   * 判断用户当前是否处于免打扰时段。
   *
   * <p>委托 {@code DndService} 进行跨天免打扰窗口判断，避免重复实现（OD-1）。
   *
   * @param userId 用户 ID
   * @return true 表示当前处于免打扰时段，调用方应延迟发送
   */
  @Override
  public boolean isInDndPeriod(String userId) {
    // OD-1: 委托给 DndService，消除重复的跨天窗口判断逻辑
    return dndService.shouldDelay(userId, null);
  }

  /**
   * 获取推荐的发送时间窗口。
   *
   * <p>当前返回固定推荐窗口 09:00-21:00；后续可结合用户活跃画像动态计算最优时段。
   *
   * @param userId 用户 ID
   * @return 推荐发送时间窗口字符串（格式 HH:mm-HH:mm）
   */
  @Override
  public String getOptimalTimeWindow(String userId) {
    // 默认推荐 09:00-21:00
    return "09:00-21:00";
  }

  /**
   * 计算单个通道的综合评分。
   *
   * <p>评分 = 活跃度 * 0.4 + 打开率 * 0.3 + 偏好 * 0.2 + 成本 * 0.1
   *
   * @param channel 通道标识（如 SMS、EMAIL、INAPP）
   * @param profile 用户触达画像（含活跃度/打开率/偏好等数据）
   * @return 综合评分（0-100，越高越推荐）
   */
  private double calculateChannelScore(String channel, UserReachProfileDTO profile) {
    // 活跃度评分（0-100 → 0-1）
    double activityScore = DEFAULT_ACTIVITY_SCORE; // 默认中等
    if (profile.getChannelActivityScores() != null) {
      Integer score = profile.getChannelActivityScores().get(channel);
      if (score != null) {
        activityScore = Math.min(1.0, score / 100.0);
      }
    }
    // 打开率
    double openRate = profile.getOpenRate() != null ? profile.getOpenRate() : DEFAULT_OPEN_RATE;
    // 偏好评分：在偏好列表中越靠前分越高
    double prefScore = DEFAULT_PREF_SCORE;
    if (profile.getChannelPreferences() != null) {
      int idx = profile.getChannelPreferences().indexOf(channel);
      if (idx >= 0) {
        int total = profile.getChannelPreferences().size();
        prefScore = total > 0 ? (total - idx) / (double) total : DEFAULT_COST_SCORE;
      }
    }
    // 成本评分（越低成本越高分）
    double costScore = 1.0 - CHANNEL_COST.getOrDefault(channel, DEFAULT_COST_SCORE);
    // 综合评分
    return (activityScore * ACTIVITY_WEIGHT + openRate * OPEN_RATE_WEIGHT
        + prefScore * PREF_SCORE_WEIGHT + costScore * COST_SCORE_WEIGHT) * SCORE_SCALE;
  }

  /**
   * 返回默认画像（Redis 缓存无数据时兜底使用）。
   *
   * @return 带有默认值的用户触达画像
   */
  private UserReachProfileDTO defaultProfile() {
    UserReachProfileDTO profile = new UserReachProfileDTO();
    profile.setChannelActivityScores(Map.of());
    profile.setOpenRate(DEFAULT_OPEN_RATE);
    profile.setClickRate(0.1);
    profile.setTimezone(DEFAULT_TIMEZONE);
    return profile;
  }

  /** 通道评分内部记录（通道标识 + 评分）。 */
  private record ChannelScore(String channel, double score) {}
}
