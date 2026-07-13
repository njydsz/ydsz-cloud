package com.njydsz.pmis.message.server.service.impl.core;

import com.njydsz.pmis.message.server.service.core.DeliveryTimeOptimizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

/**
 * P1-1: 智能推送时间优化器实现。
 *
 * <p>基于 Redis 存储用户活跃度画像：
 * <ul>
 *   <li>活跃度 Bitmap: {@code pmis:activity:{userId}} → Bitmap(24*7=168 bits, hour-of-week)</li>
 *   <li>活跃计数: {@code pmis:activity:count:{userId}} → 最近 7 天活跃次数</li>
 *   <li>小时维度计数: {@code pmis:activity:hourly:{userId}} → Hash(hour→count, 0-23)</li>
 * </ul>
 *
 * <p>推荐策略：
 * <ol>
 *   <li>统计用户每小时活跃次数，找出最高活跃时段</li>
 *   <li>如果当前时间在活跃时段内（±1小时），返回当前时间</li>
 *   <li>否则返回今天内最近下一个活跃时段的开始时间</li>
 *   <li>如果今天没有更多活跃时段，返回明天的最高活跃时段</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTimeOptimizerImpl implements DeliveryTimeOptimizer {

    /** Redis 模板（用户活跃度画像） */
    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀 */
    private static final String ACTIVITY_HOURLY_PREFIX = "pmis:activity:hourly:";
    private static final String ACTIVITY_COUNT_PREFIX = "pmis:activity:count:";

    /** 默认活跃评分有效期（天） */
    private static final int ACTIVITY_EXPIRE_DAYS = 7;

    @Override
    public void recordActivity(String userId, String channel) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            String hourKey = String.valueOf(now.getHour());

            // 更新小时维度活跃计数（Hash: hour → count）
            String hourlyKey = ACTIVITY_HOURLY_PREFIX + userId;
            redisTemplate.opsForHash().increment(hourlyKey, hourKey, 1);
            redisTemplate.expire(hourlyKey, Duration.ofDays(ACTIVITY_EXPIRE_DAYS));

            // 更新总活跃计数
            String countKey = ACTIVITY_COUNT_PREFIX + userId;
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(ACTIVITY_EXPIRE_DAYS));

            log.debug("[DeliveryTime] 记录活跃: userId={} hour={} channel={}", userId, now.getHour(), channel);
        } catch (Exception e) {
            log.warn("[DeliveryTime] 记录活跃失败,降级忽略: userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    public LocalDateTime getOptimalDeliveryTime(String userId, String channel) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            String hourlyKey = ACTIVITY_HOURLY_PREFIX + userId;
            Map<Object, Object> hourlyCounts = redisTemplate.opsForHash().entries(hourlyKey);
            if (hourlyCounts == null || hourlyCounts.isEmpty()) {
                return null; // 无活跃数据
            }

            // 解析并找出最活跃的时段
            Map<Integer, Long> hourCounts = new HashMap<>();
            int bestHour = -1;
            long bestCount = 0;
            for (Map.Entry<Object, Object> entry : hourlyCounts.entrySet()) {
                try {
                    int hour = Integer.parseInt(String.valueOf(entry.getKey()));
                    long count = Long.parseLong(String.valueOf(entry.getValue()));
                    hourCounts.put(hour, count);
                    if (count > bestCount) {
                        bestCount = count;
                        bestHour = hour;
                    }
                } catch (NumberFormatException ignored) {
                    // 跳过无效数据
                }
            }

            if (bestHour < 0) {
                return null;
            }

            LocalDateTime now = LocalDateTime.now();
            int currentHour = now.getHour();

            // 如果当前时间在最佳时段 ±1 小时内，返回当前时间
            if (Math.abs(currentHour - bestHour) <= 1) {
                return now;
            }

            // 如果最佳时段在今天还未到来，返回今天的最佳时段
            if (bestHour > currentHour) {
                return now.toLocalDate().atTime(bestHour, 0);
            }

            // 否则返回明天的最佳时段
            return now.toLocalDate().plusDays(1).atTime(bestHour, 0);
        } catch (Exception e) {
            log.warn("[DeliveryTime] 获取最佳推送时间失败: userId={} err={}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public int getActivityScore(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        try {
            String countKey = ACTIVITY_COUNT_PREFIX + userId;
            String countStr = redisTemplate.opsForValue().get(countKey);
            if (countStr == null) {
                return 0;
            }
            long count = Long.parseLong(countStr);
            // 活跃度评分公式：min(count * 5, 100)，即 20 次活跃即满分
            return (int) Math.min(count * 5, 100);
        } catch (Exception e) {
            log.warn("[DeliveryTime] 获取活跃度评分失败: userId={} err={}", userId, e.getMessage());
            return 0;
        }
    }
}
