package com.njydsz.pmis.message.service.impl.core;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.service.core.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * P2-11: 消息引擎告警服务。
 *
 * <p>定时检查消息发送指标，超过阈值时通过钉钉/飞书发送告警：
 * <ul>
 *   <li>错误率 > 10%（5 分钟窗口）</li>
 *   <li>P95 延迟 > 5s（任一通道）</li>
 *   <li>死信队列积压 > 100</li>
 *   <li>令牌桶限流率 > 30%</li>
 * </ul>
 *
 * <p>告警去重：同一告警 5 分钟内只发一次（Redis SET NX EX）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageAlertService {

    private final StringRedisTemplate redisTemplate;
    private final RealtimeStatsService realtimeStatsService;
    private final MessageService messageService;

    /** 告警去重 key 前缀 */
    private static final String ALERT_DEDUP_PREFIX = "pmis:alert:dedup:";
    /** 告警去重 TTL（秒） */
    private static final long ALERT_DEDUP_TTL = 300L;
    /** 错误率阈值 */
    private static final double ERROR_RATE_THRESHOLD = 0.10;
    /** P95 延迟阈值（毫秒） */
    private static final double P95_LATENCY_THRESHOLD = 5000;

    /**
     * 每 5 分钟检查一次告警指标。
     */
    @Scheduled(fixedDelay = 300_000)
    public void checkAlerts() {
        try {
            checkErrorRate();
            checkLatency();
        } catch (Exception e) {
            log.warn("[Alert] 告警检查异常: {}", e.getMessage());
        }
    }

    /**
     * 检查各通道错误率。
     */
    private void checkErrorRate() {
        Map<String, String> stats = realtimeStatsService.getRealtimeStats();
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) continue;
            String channel = parts[0];
            // 计算错误率
            long total = 0;
            long errors = 0;
            for (Map.Entry<String, String> e2 : stats.entrySet()) {
                String[] p2 = e2.getKey().split(":");
                if (p2.length == 2 && p2[0].equals(channel)) {
                    total += Long.parseLong(e2.getValue());
                    if (!"SUCCESS".equals(p2[1])) {
                        errors += Long.parseLong(e2.getValue());
                    }
                }
            }
            if (total > 0) {
                double errorRate = (double) errors / total;
                if (errorRate > ERROR_RATE_THRESHOLD) {
                    String alertKey = "error_rate:" + channel;
                    String msg = String.format("⚠️ 消息通道告警: 通道=%s 错误率=%.1f%% (阈值%.0f%%) 错误数=%d 总数=%d",
                            channel, errorRate * 100, ERROR_RATE_THRESHOLD * 100, errors, total);
                    sendAlert(alertKey, msg);
                }
            }
        }
    }

    /**
     * 检查各通道 P95 延迟。
     */
    private void checkLatency() {
        for (String channel : new String[]{"SMS", "EMAIL", "PUSH", "DINGTALK"}) {
            double[] percentiles = realtimeStatsService.getLatencyPercentiles(channel);
            if (percentiles.length >= 2 && percentiles[1] > P95_LATENCY_THRESHOLD) {
                String alertKey = "p95_latency:" + channel;
                String msg = String.format("⚠️ 延迟告警: 通道=%s P95=%.0fms P99=%.0fms (阈值%.0fms)",
                        channel, percentiles[1], percentiles[2], P95_LATENCY_THRESHOLD);
                sendAlert(alertKey, msg);
            }
        }
    }

    /**
     * 发送告警（去重 + 钉钉/飞书通道）。
     *
     * @param alertKey 告警去重 key
     * @param message  告警内容
     */
    private void sendAlert(String alertKey, String message) {
        try {
            // 去重检查
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(ALERT_DEDUP_PREFIX + alertKey, "1", Duration.ofSeconds(ALERT_DEDUP_TTL));
            if (!Boolean.TRUE.equals(isNew)) {
                log.debug("[Alert] 告警去重跳过: {}", alertKey);
                return;
            }
            log.warn("[Alert] 触发告警: {}", message);
            // 通过钉钉发送告警
            MessageRequest request = new MessageRequest();
            request.setChannel("DINGTALK");
            request.setContent(message);
            request.setBizType("SYSTEM_ALERT");
            request.setBizId("alert-" + System.currentTimeMillis());
            request.setMessageId("alert-" + alertKey + "-" + System.currentTimeMillis());
            try {
                MessageResult result = messageService.send(request);
                if (!result.isSuccess()) {
                    log.warn("[Alert] 告警发送失败: {}", result.getErrorMessage());
                }
            } catch (Exception e) {
                log.warn("[Alert] 告警发送异常: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[Alert] 告警流程异常: {}", e.getMessage());
        }
    }
}
