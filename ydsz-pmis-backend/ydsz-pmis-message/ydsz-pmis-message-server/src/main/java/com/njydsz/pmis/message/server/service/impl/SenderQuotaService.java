package com.njydsz.pmis.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * P2-20: Sender 配额管理。
 *
 * <p>限制每个发送方（业务系统/模块）的日/小时发送配额，防止单一业务方
 * 大量发送消息耗尽全局资源（如 SMS 费用、邮件信誉）。
 *
 * <p>配额维度：
 * <ul>
 *   <li>日配额：每发送方每天最多发送 N 条</li>
 *   <li>小时配额：每发送方每小时最多发送 N 条</li>
 *   <li>按通道独立计数：SMS/EMAIL 分别统计</li>
 * </ul>
 *
 * <p>Redis Key 格式：
 * <ul>
 *   <li>日配额：{@code quota:daily:{senderId}:{channel}:{date}}</li>
 *   <li>小时配额：{@code quota:hourly:{senderId}:{channel}:{hour}}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SenderQuotaService {

    private final StringRedisTemplate redisTemplate;

    private static final String DAILY_KEY_PREFIX = "quota:daily:";
    private static final String HOURLY_KEY_PREFIX = "quota:hourly:";

    /** 默认日配额 */
    private static final long DEFAULT_DAILY_LIMIT = 10000L;

    /** 默认小时配额 */
    private static final long DEFAULT_HOURLY_LIMIT = 1000L;

    /**
     * 检查发送方配额是否允许发送。
     *
     * @param senderId 发送方 ID
     * @param channel  通道类型
     * @return true 表示配额允许，false 表示配额已用尽
     */
    public boolean checkQuota(String senderId, String channel) {
        if (senderId == null || senderId.isBlank()) {
            return true;
        }
        String today = LocalDate.now().toString();
        String dailyKey = DAILY_KEY_PREFIX + senderId + ":" + channel + ":" + today;
        String dailyCountStr = redisTemplate.opsForValue().get(dailyKey);
        long dailyCount = dailyCountStr != null ? Long.parseLong(dailyCountStr) : 0;
        if (dailyCount >= DEFAULT_DAILY_LIMIT) {
            log.warn("[Quota] 日配额已用尽: senderId={} channel={} count={} limit={}",
                    senderId, channel, dailyCount, DEFAULT_DAILY_LIMIT);
            return false;
        }
        String hourKey = HOURLY_KEY_PREFIX + senderId + ":" + channel + ":" + today + ":" +
                String.format("%02d", LocalTime.now().getHour());
        String hourCountStr = redisTemplate.opsForValue().get(hourKey);
        long hourCount = hourCountStr != null ? Long.parseLong(hourCountStr) : 0;
        if (hourCount >= DEFAULT_HOURLY_LIMIT) {
            log.warn("[Quota] 小时配额已用尽: senderId={} channel={} count={} limit={}",
                    senderId, channel, hourCount, DEFAULT_HOURLY_LIMIT);
            return false;
        }
        return true;
    }

    /**
     * 记录发送计数。
     *
     * @param senderId 发送方 ID
     * @param channel  通道类型
     */
    public void recordSend(String senderId, String channel) {
        if (senderId == null || senderId.isBlank()) {
            return;
        }
        String today = LocalDate.now().toString();
        String dailyKey = DAILY_KEY_PREFIX + senderId + ":" + channel + ":" + today;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(dailyKey, Duration.ofDays(2));
        }
        String hourKey = HOURLY_KEY_PREFIX + senderId + ":" + channel + ":" + today + ":" +
                String.format("%02d", LocalTime.now().getHour());
        Long hourCount = redisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1L) {
            redisTemplate.expire(hourKey, Duration.ofHours(2));
        }
    }

    /**
     * 获取发送方当日已发送数。
     *
     * @param senderId 发送方 ID
     * @param channel  通道类型
     * @return 已发送数
     */
    public long getDailyCount(String senderId, String channel) {
        String today = LocalDate.now().toString();
        String dailyKey = DAILY_KEY_PREFIX + senderId + ":" + channel + ":" + today;
        String countStr = redisTemplate.opsForValue().get(dailyKey);
        return countStr != null ? Long.parseLong(countStr) : 0;
    }
}
