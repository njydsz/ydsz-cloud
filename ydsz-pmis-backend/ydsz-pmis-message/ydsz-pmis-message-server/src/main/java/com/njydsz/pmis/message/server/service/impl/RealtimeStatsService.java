package com.njydsz.pmis.message.server.service.impl.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-11: 实时统计预聚合服务。
 *
 * <p>将消息发送指标实时写入 Redis，供看板查询和告警判断：
 * <ul>
 *   <li>每分钟维度：{@code pmis:stats:realtime:{yyyyMMddHHmm}} → Hash(channel, count)</li>
 *   <li>延迟分位数：{@code pmis:stats:latency:{channel}} → Sorted Set(score=costMs, member=msgId)</li>
 *   <li>渠道错误计数：{@code pmis:stats:errors:{channel}:{yyyyMMdd}} → INCR</li>
 * </ul>
 *
 * <p>定时任务每分钟将上一分钟的预聚合数据持久化到数据库统计表（可选）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeStatsService {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 记录一次消息发送到实时统计。
     *
     * @param channel 通道
     * @param status  状态（SUCCESS/FAILED/RETRY/RATE_LIMITED）
     * @param costMs  耗时（毫秒）
     */
    public void recordSend(String channel, String status, long costMs) {
        try {
            String minuteKey = "pmis:stats:realtime:" + LocalDateTime.now().format(MINUTE_FMT);
            // 按状态+通道计数
            redisTemplate.opsForHash().increment(minuteKey, channel + ":" + status, 1);
            redisTemplate.expire(minuteKey, Duration.ofHours(2));
            // 记录延迟到 Sorted Set（保留最近 10000 条用于分位数计算）
            if ("SUCCESS".equals(status) && costMs > 0) {
                String latencyKey = "pmis:stats:latency:" + channel;
                String member = channel + ":" + System.nanoTime();
                redisTemplate.opsForZSet().add(latencyKey, member, costMs);
                redisTemplate.expire(latencyKey, Duration.ofMinutes(30));
                // 限制 Sorted Set 大小
                Long size = redisTemplate.opsForZSet().size(latencyKey);
                if (size != null && size > 10000) {
                    redisTemplate.opsForZSet().removeRange(latencyKey, 0, (int) (size - 10000) - 1);
                }
            }
            // 错误计数
            if (!"SUCCESS".equals(status)) {
                String errorKey = "pmis:stats:errors:" + channel + ":" + LocalDateTime.now().format(DAY_FMT);
                redisTemplate.opsForValue().increment(errorKey);
                redisTemplate.expire(errorKey, Duration.ofDays(7));
            }
        } catch (Exception e) {
            log.debug("[RealtimeStats] 记录失败(忽略): {}", e.getMessage());
        }
    }

    /**
     * 获取当前分钟各通道的实时发送统计。
     *
     * @return key=channel:status, value=count
     */
    public Map<String, String> getRealtimeStats() {
        String minuteKey = "pmis:stats:realtime:" + LocalDateTime.now().format(MINUTE_FMT);
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(minuteKey);
        Map<String, String> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    /**
     * 计算指定通道的延迟分位数（P50/P95/P99）。
     *
     * @param channel 通道
     * @return 分位数数组 [P50, P95, P99]（毫秒），无数据时返回 [0, 0, 0]
     */
    public double[] getLatencyPercentiles(String channel) {
        String latencyKey = "pmis:stats:latency:" + channel;
        try {
            Long size = redisTemplate.opsForZSet().size(latencyKey);
            if (size == null || size == 0) {
                return new double[]{0, 0, 0};
            }
            double p50 = getPercentile(latencyKey, size, 0.50);
            double p95 = getPercentile(latencyKey, size, 0.95);
            double p99 = getPercentile(latencyKey, size, 0.99);
            return new double[]{p50, p95, p99};
        } catch (Exception e) {
            log.warn("[RealtimeStats] 延迟分位数查询失败: channel={} err={}", channel, e.getMessage(), e);
            return new double[]{0, 0, 0};
        }
    }

    /**
     * 从 Sorted Set 中计算指定分位数的值。
     */
    private double getPercentile(String key, long size, double percentile) {
        long index = (long) Math.ceil(size * percentile) - 1;
        if (index < 0) index = 0;
        var range = redisTemplate.opsForZSet().rangeWithScores(key, index, index);
        if (range != null && !range.isEmpty()) {
            return range.iterator().next().getScore();
        }
        return 0;
    }

    /**
     * 获取当日各通道错误计数。
     *
     * @return key=channel, value=errorCount
     */
    public Map<String, Long> getDailyErrorCounts() {
        String daySuffix = LocalDateTime.now().format(DAY_FMT);
        Map<String, Long> result = new HashMap<>();
        for (String channel : new String[]{"SMS", "EMAIL", "PUSH", "INAPP", "DINGTALK", "WECOM", "WECOM_APP", "FEISHU", "WEBHOOK"}) {
            String key = "pmis:stats:errors:" + channel + ":" + daySuffix;
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                try {
                    result.put(channel, Long.parseLong(val));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
