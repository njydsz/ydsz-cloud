package com.njydsz.pmis.message.server.metric;


import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息发送监控指标。
 *
 * <p>基于 Micrometer {@link MeterRegistry} 采集发送计数、耗时、重试、死信、回执等指标，
 * 供 Prometheus / Grafana 监控。所有记录方法均 try-catch 降级，监控失败不影响业务。
 *
 * <p><b>P1-2 架构优化</b>：继承 {@link AbstractModuleMetrics}，消除重复的
 * Counter/Timer 缓存和降级模式代码。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class MessageMetrics extends AbstractModuleMetrics {

    public MessageMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "pmis.message.");
    }

    /**
     * 记录一次发送结果与耗时。
     *
     * @param channel 通道
     * @param status  发送状态（SUCCESS/FAILED）
     * @param costMs  耗时毫秒
     */
    public void recordSend(String channel, String status, long costMs) {
        incrementCounter("send.total", "channel", safe(channel), "status", safe(status));
        recordTimer("send.duration", costMs, "channel", safe(channel));
    }

    /**
     * 记录一次重试。
     *
     * @param channel 通道
     */
    public void recordRetry(String channel) {
        incrementCounter("retry.total", "channel", safe(channel));
    }

    /**
     * 记录一条死信。
     *
     * @param channel 通道
     */
    public void recordDead(String channel) {
        incrementCounter("dead.total", "channel", safe(channel));
    }

    /**
     * 记录一次回执回调。
     *
     * @param channel     通道
     * @param receiptType 回执类型
     */
    public void recordReceipt(String channel, String receiptType) {
        incrementCounter("receipt.total", "channel", safe(channel), "receiptType", safe(receiptType));
    }

    /**
     * P2-4: 记录通道级错误(HTTP 状态码非 2xx / 业务 errcode 非 0 / 异常)。
     *
     * <p>与 {@link #recordSend} 的区别：recordSend 是业务级指标(由 MessageService 记录),
     * recordChannelError 是通道级指标(由 ChannelRouter 记录),用于按 errorType 维度监控
     * 各 HTTP 通道的失败原因(如 CIRCUIT_BREAKER/EXCEPTION/BUSINESS_ERROR)。
     *
     * @param channel   通道
     * @param errorType 错误类型(CIRCUIT_BREAKER/EXCEPTION/BUSINESS_ERROR)
     */
    public void recordChannelError(String channel, String errorType) {
        incrementCounter("channel.error", "channel", safe(channel), "errorType", safe(errorType));
    }
}
