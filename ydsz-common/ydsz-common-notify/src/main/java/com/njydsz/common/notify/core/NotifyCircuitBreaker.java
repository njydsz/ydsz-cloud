package com.njydsz.common.notify.core;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.safe.ratelimit.circuitbreaker.AbstractCircuitBreaker;

/**
 * 通知渠道熔断器
 *
 * <p>基于连续失败计数实现轻量级熔断保护，防止渠道故障时持续尝试导致资源浪费。
 *
 * <p><b>熔断状态机：</b>
 * <ul>
 *   <li><b>CLOSED</b>（正常）：请求正常通过，记录失败次数</li>
 *   <li><b>OPEN</b>（熔断）：连续失败超过阈值，拒绝所有请求，等待恢复时间</li>
 *   <li><b>HALF_OPEN</b>（半开）：恢复时间到达后，放行单个探测请求；
 *       探测成功则回到 CLOSED，探测失败则重新进入 OPEN</li>
 * </ul>
 *
 * <p><b>线程安全：</b>继承 {@link AbstractCircuitBreaker}，状态转换由基类 CAS 保证原子性。
 *
 * <h3>v1.4.0 变更</h3>
 * <p>自 v1.4.0 起，继承 {@link AbstractCircuitBreaker}（ydsz-common-safe），
 * 复用标准三态状态机，移除自研 AtomicReference + CAS 状态管理代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyCircuitBreaker extends AbstractCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(NotifyCircuitBreaker.class);

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_RECOVERY_TIMEOUT_MS = 60_000L;

    private final NotifyChannel channel;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * 使用默认参数创建熔断器
     *
     * @param channel 通知渠道
     */
    public NotifyCircuitBreaker(NotifyChannel channel) {
        this(channel, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RECOVERY_TIMEOUT_MS);
    }

    /**
     * 创建熔断器
     *
     * @param channel           通知渠道
     * @param failureThreshold  连续失败阈值
     * @param recoveryTimeoutMs 恢复等待时间（毫秒）
     */
    public NotifyCircuitBreaker(NotifyChannel channel, int failureThreshold, long recoveryTimeoutMs) {
        super(new Config("notify-" + channel.getName(),
                failureThreshold,
                recoveryTimeoutMs,
                1));
        this.channel = channel;
    }

    @Override
    protected boolean evaluateThreshold() {
        return consecutiveFailures.get() >= (int) config.getFailureThreshold();
    }

    @Override
    protected void onSuccessRecord() {
        // 连续失败策略：任何一次成功即重置连续失败计数
        consecutiveFailures.set(0);
    }

    @Override
    protected void onFailureRecord() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= (int) config.getFailureThreshold()) {
            log.warn("[NotifyCircuitBreaker] 渠道[{}]连续失败 {} 次达到阈值",
                    channel.getName(), failures);
        }
    }

    @Override
    protected void resetStats() {
        consecutiveFailures.set(0);
        log.info("[NotifyCircuitBreaker] 渠道[{}]熔断器恢复，切换到 CLOSED 状态", channel.getName());
    }

    /**
     * 获取连续失败次数
     *
     * @return 连续失败计数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取关联的通知渠道
     *
     * @return 通知渠道
     */
    public NotifyChannel getChannel() {
        return channel;
    }
}
