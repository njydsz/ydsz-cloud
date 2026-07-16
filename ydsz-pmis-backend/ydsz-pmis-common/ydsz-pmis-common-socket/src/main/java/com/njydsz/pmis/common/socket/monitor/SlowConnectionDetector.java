package com.njydsz.pmis.common.socket.monitor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;
import com.njydsz.pmis.common.socket.metric.WebSocketMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 慢连接检测器（P2-2）。
 *
 * <p>记录每次推送操作耗时，超过阈值则标记为慢连接，
 * 并上报 Micrometer 指标和告警日志。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class SlowConnectionDetector {

    private final WebSocketProperties properties;
    private final WebSocketMetrics webSocketMetrics;

    /** 慢连接计数（按 sessionId 分组） */
    private final Map<String, AtomicLong> slowConnectionCounts = new ConcurrentHashMap<>();

    /**
     * 记录推送耗时，检测慢连接。
     *
     * @param sessionId  Session ID（可为 null）
     * @param durationMs 推送耗时（毫秒）
     */
    public void recordPushDuration(String sessionId, long durationMs) {
        if (!properties.getSlowConnection().isEnabled()) {
            webSocketMetrics.recordPushDuration(sessionId != null ? "USER" : "BROADCAST",
                    Duration.ofMillis(durationMs));
            return;
        }

        long threshold = properties.getSlowConnection().getThresholdMs();
        if (durationMs > threshold) {
            webSocketMetrics.recordPushDuration("SLOW", Duration.ofMillis(durationMs));
            if (sessionId != null) {
                slowConnectionCounts.computeIfAbsent(sessionId, k -> new AtomicLong(0))
                        .incrementAndGet();
                log.warn("[WS-SlowConn] 慢连接检测: sessionId={}, durationMs={}, threshold={}, slowCount={}",
                        sessionId, durationMs, threshold,
                        slowConnectionCounts.get(sessionId).get());
            } else {
                log.warn("[WS-SlowConn] 慢广播检测: durationMs={}, threshold={}",
                        durationMs, threshold);
            }
        } else {
            String pushType = sessionId != null ? "USER" : "BROADCAST";
            webSocketMetrics.recordPushDuration(pushType, Duration.ofMillis(durationMs));
        }
    }

    /**
     * 获取指定 Session 的慢连接次数。
     *
     * @param sessionId Session ID
     * @return 慢连接次数
     */
    public long getSlowCount(String sessionId) {
        AtomicLong count = slowConnectionCounts.get(sessionId);
        return count == null ? 0 : count.get();
    }

    /**
     * 清理已断开 Session 的慢连接记录。
     *
     * @param sessionId Session ID
     */
    public void cleanup(String sessionId) {
        slowConnectionCounts.remove(sessionId);
    }

    /**
     * 获取慢连接 Session 总数。
     *
     * @return 慢连接 Session 数
     */
    public int getSlowSessionCount() {
        return slowConnectionCounts.size();
    }
}
