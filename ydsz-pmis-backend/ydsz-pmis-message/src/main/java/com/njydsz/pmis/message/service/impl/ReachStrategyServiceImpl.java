package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.dto.UserReachProfileDTO;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.ReachStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能触达策略服务实现。
 *
 * <p>P1-8: 基于 Redis 缓存的用户画像数据，综合评分各通道的触达能力。
 *
 * <p>评分公式（满分 100）：
 * <ul>
 *   <li>通道活跃度（40%）：用户在该通道的历史活跃程度</li>
 *   <li>历史打开率（30%）：该通道的历史消息打开率</li>
 *   <li>用户偏好（20%）：用户设置的通道优先级</li>
 *   <li>通道成本（10%）：低成本通道加分</li>
 * </ul>
 *
 * <p>免打扰判断：基于用户偏好中的 DND 配置，结合用户时区判断当前是否在免打扰时段。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReachStrategyServiceImpl implements ReachStrategyService {

    /** Redis 画像缓存 key 前缀 */
    private static final String PROFILE_KEY_PREFIX = "pmis:reach:profile:";

    /** 默认时区 */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 通道成本权重（越低成本越高分） */
    private static final Map<String, Double> CHANNEL_COST = Map.of(
            "IN_APP", 0.1,
            "WEBHOOK", 0.2,
            "DINGTALK", 0.3,
            "WECOM", 0.3,
            "FEISHU", 0.3,
            "EMAIL", 0.4,
            "PUSH", 0.6,
            "SMS", 1.0
    );

    private final StringRedisTemplate redisTemplate;
    private final PreferenceService preferenceService;

    @Override
    public UserReachProfileDTO getProfile(String userId) {
        if (!StringUtils.hasText(userId)) {
            return defaultProfile();
        }
        try {
            // 从 Redis 加载画像（外部系统写入）
            Map<Object, Object> raw = redisTemplate.opsForHash()
                    .entries(PROFILE_KEY_PREFIX + userId);
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
            Map<String, Integer> scores = new HashMap<>();
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.startsWith("score:")) {
                    String channel = key.substring(6);
                    try {
                        scores.put(channel, Integer.parseInt(String.valueOf(e.getValue())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            profile.setChannelActivityScores(scores);
            return profile;
        } catch (Exception e) {
            log.warn("[ReachStrategy] 画像加载失败,使用默认: userId={} err={}", userId, e.getMessage());
            return defaultProfile();
        }
    }

    @Override
    public List<String> selectOptimalChannels(String userId, List<String> channels, String bizType) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        UserReachProfileDTO profile = getProfile(userId);
        // 计算每个通道的综合评分
        List<ChannelScore> scored = new ArrayList<>();
        for (String channel : channels) {
            double score = calculateChannelScore(channel, profile);
            scored.add(new ChannelScore(channel, score));
        }
        // 按评分降序排列
        scored.sort(Comparator.comparingDouble(ChannelScore::score).reversed());
        return scored.stream().map(ChannelScore::channel).toList();
    }

    @Override
    public boolean isInDndPeriod(String userId) {
        UserReachProfileDTO profile = getProfile(userId);
        if (!StringUtils.hasText(profile.getDndStart()) || !StringUtils.hasText(profile.getDndEnd())) {
            return false;
        }
        try {
            ZoneId zone = ZoneId.of(profile.getTimezone() != null ? profile.getTimezone() : DEFAULT_TIMEZONE);
            ZonedDateTime now = ZonedDateTime.now(zone);
            LocalTime currentTime = now.toLocalTime();
            LocalTime start = LocalTime.parse(profile.getDndStart());
            LocalTime end = LocalTime.parse(profile.getDndEnd());
            // 处理跨天情况（如 22:00-08:00）
            if (start.isBefore(end)) {
                return !currentTime.isBefore(start) && currentTime.isBefore(end);
            } else {
                return !currentTime.isBefore(start) || currentTime.isBefore(end);
            }
        } catch (Exception e) {
            log.warn("[ReachStrategy] DND 判断异常: userId={} err={}", userId, e.getMessage());
            return false;
        }
    }

    @Override
    public String getOptimalTimeWindow(String userId) {
        UserReachProfileDTO profile = getProfile(userId);
        // 默认推荐 09:00-21:00
        return "09:00-21:00";
    }

    /**
     * 计算单个通道的综合评分。
     *
     * <p>评分 = 活跃度 * 0.4 + 打开率 * 0.3 + 偏好 * 0.2 + 成本 * 0.1
     */
    private double calculateChannelScore(String channel, UserReachProfileDTO profile) {
        // 活跃度评分（0-100 → 0-1）
        double activityScore = 0.5; // 默认中等
        if (profile.getChannelActivityScores() != null) {
            Integer score = profile.getChannelActivityScores().get(channel);
            if (score != null) {
                activityScore = Math.min(1.0, score / 100.0);
            }
        }
        // 打开率
        double openRate = profile.getOpenRate() != null ? profile.getOpenRate() : 0.3;
        // 偏好评分：在偏好列表中越靠前分越高
        double prefScore = 0.5;
        if (profile.getChannelPreferences() != null) {
            int idx = profile.getChannelPreferences().indexOf(channel);
            if (idx >= 0) {
                int total = profile.getChannelPreferences().size();
                prefScore = total > 0 ? (total - idx) / (double) total : 0.5;
            }
        }
        // 成本评分（越低成本越高分）
        double costScore = 1.0 - CHANNEL_COST.getOrDefault(channel, 0.5);
        // 综合评分
        return (activityScore * 0.4 + openRate * 0.3 + prefScore * 0.2 + costScore * 0.1) * 100;
    }

    /**
     * 返回默认画像。
     */
    private UserReachProfileDTO defaultProfile() {
        UserReachProfileDTO profile = new UserReachProfileDTO();
        profile.setChannelActivityScores(Map.of());
        profile.setOpenRate(0.3);
        profile.setClickRate(0.1);
        profile.setTimezone(DEFAULT_TIMEZONE);
        return profile;
    }

    /** 通道评分内部记录 */
    private record ChannelScore(String channel, double score) {
    }
}
