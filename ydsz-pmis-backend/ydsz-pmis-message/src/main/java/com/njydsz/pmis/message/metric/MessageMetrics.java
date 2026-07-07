package com.njydsz.pmis.message.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 消息发送监控指标。
 *
 * <p>基于 Micrometer {@link MeterRegistry} 采集发送计数、耗时、重试、死信、回执等指标，
 * 供 Prometheus / Grafana 监控。所有记录方法均 try-catch 降级，监控失败不影响业务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(MeterRegistry.class)
public class MessageMetrics {

    /** 指标前缀 */
    private static final String METER_PREFIX = "pmis.message.";

    private final MeterRegistry meterRegistry;

    /**
     * 记录一次发送结果与耗时。
     *
     * @param channel 通道
     * @param status  发送状态（SUCCESS/FAILED）
     * @param costMs  耗时毫秒
     */
    public void recordSend(String channel, String status, long costMs) {
        try {
            meterRegistry.counter(METER_PREFIX + "send.total",
                    "channel", channel == null ? "unknown" : channel,
                    "status", status == null ? "unknown" : status).increment();
            Timer.builder(METER_PREFIX + "send.duration")
                    .tag("channel", channel == null ? "unknown" : channel)
                    .register(meterRegistry)
                    .record(java.time.Duration.ofMillis(costMs));
        } catch (Exception e) {
            log.debug("[MessageMetrics] recordSend 降级忽略: {}", e.getMessage());
        }
    }

    /**
     * 记录一次重试。
     *
     * @param channel 通道
     */
    public void recordRetry(String channel) {
        try {
            meterRegistry.counter(METER_PREFIX + "retry.total",
                    "channel", channel == null ? "unknown" : channel).increment();
        } catch (Exception e) {
            log.debug("[MessageMetrics] recordRetry 降级忽略: {}", e.getMessage());
        }
    }

    /**
     * 记录一条死信。
     *
     * @param channel 通道
     */
    public void recordDead(String channel) {
        try {
            meterRegistry.counter(METER_PREFIX + "dead.total",
                    "channel", channel == null ? "unknown" : channel).increment();
        } catch (Exception e) {
            log.debug("[MessageMetrics] recordDead 降级忽略: {}", e.getMessage());
        }
    }

    /**
     * 记录一次回执回调。
     *
     * @param channel     通道
     * @param receiptType 回执类型
     */
    public void recordReceipt(String channel, String receiptType) {
        try {
            meterRegistry.counter(METER_PREFIX + "receipt.total",
                    "channel", channel == null ? "unknown" : channel,
                    "receiptType", receiptType == null ? "unknown" : receiptType).increment();
        } catch (Exception e) {
            log.debug("[MessageMetrics] recordReceipt 降级忽略: {}", e.getMessage());
        }
    }
}
