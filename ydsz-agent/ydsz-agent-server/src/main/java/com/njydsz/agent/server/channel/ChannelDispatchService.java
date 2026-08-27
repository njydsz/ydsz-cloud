package com.njydsz.agent.server.channel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.agent.domain.channel.ChannelAdapter;
import com.njydsz.agent.domain.channel.ChannelAdapter.ChannelStatus;
import com.njydsz.agent.domain.channel.ChannelRequest;
import com.njydsz.agent.domain.channel.ChannelResponse;
import com.njydsz.agent.domain.channel.ChannelType;

import lombok.extern.slf4j.Slf4j;

/**
 * 渠道调度服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>将请求路由到对应的渠道适配器</li>
 *   <li>实现渠道级错误隔离（熔断器模式）</li>
 *   <li>统计各渠道的请求量和失败率</li>
 *   <li>自动降级和恢复</li>
 * </ul>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class ChannelDispatchService {

    private final ChannelAdapterRegistry registry;

    /** 渠道请求计数器 */
    private final Map<ChannelType, AtomicLong> requestCounters = new ConcurrentHashMap<>();

    /** 渠道失败计数器 */
    private final Map<ChannelType, AtomicLong> failureCounters = new ConcurrentHashMap<>();

    /** 渠道最后错误信息 */
    private final Map<ChannelType, String> lastErrors = new ConcurrentHashMap<>();

    /** 熔断器阈值：失败率超过此值触发熔断 */
    private static final double CIRCUIT_BREAKER_THRESHOLD = 0.5;

    /** 熔断器窗口：连续失败次数触发熔断 */
    private static final int CIRCUIT_BREAKER_FAILURE_COUNT = 10;

    public ChannelDispatchService(ChannelAdapterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为 null");
    }

    /**
     * 调度请求到对应渠道。
     *
     * @param request 渠道请求
     * @return 渠道响应
     */
    public ChannelResponse dispatch(ChannelRequest request) {
        ChannelType channelType = request.getChannelType();

        // 检查熔断器
        if (isCircuitBroken(channelType)) {
            log.warn("[ChannelDispatch] 渠道已熔断，拒绝请求: type={}", channelType);
            return ChannelResponse.failure(
                    request.getRequestId(),
                    channelType,
                    "CHANNEL_CIRCUIT_BREAK",
                    "渠道暂时不可用，请稍后重试"
            );
        }

        // 获取适配器
        ChannelAdapter adapter = registry.getAdapter(channelType)
                .orElse(null);

        if (adapter == null) {
            log.error("[ChannelDispatch] 未找到渠道适配器: type={}", channelType);
            return ChannelResponse.failure(
                    request.getRequestId(),
                    channelType,
                    "CHANNEL_NOT_FOUND",
                    "不支持的渠道类型: " + channelType
            );
        }

        // 执行请求（带错误隔离）
        incrementRequestCount(channelType);
        try {
            ChannelResponse response = adapter.handleRequest(request);

            if (!response.isSuccess()) {
                incrementFailureCount(channelType);
                lastErrors.put(channelType, response.getErrorMessage());
            }

            return response;

        } catch (Exception e) {
            // 捕获所有异常，防止渠道异常影响全局
            incrementFailureCount(channelType);
            lastErrors.put(channelType, e.getMessage());

            log.error("[ChannelDispatch] 渠道执行异常: type={}, error={}",
                    channelType, e.getMessage(), e);

            return ChannelResponse.failure(
                    request.getRequestId(),
                    channelType,
                    "CHANNEL_EXECUTION_ERROR",
                    "渠道执行异常: " + e.getMessage()
            );
        }
    }

    /**
     * 获取渠道状态。
     *
     * @param type 渠道类型
     * @return 渠道状态
     */
    public ChannelStatus getChannelStatus(ChannelType type) {
        long total = getRequestCount(type);
        long failures = getFailureCount(type);
        double failureRate = total > 0 ? (double) failures / total : 0.0;

        boolean healthy = registry.isAvailable(type) && !isCircuitBroken(type);

        return new ChannelStatus(
                type,
                healthy,
                total,
                failures,
                failureRate,
                lastErrors.get(type)
        );
    }

    /**
     * 检查渠道是否处于熔断状态。
     *
     * @param type 渠道类型
     * @return 是否熔断
     */
    private boolean isCircuitBroken(ChannelType type) {
        long total = getRequestCount(type);
        long failures = getFailureCount(type);

        // 请求量不足时不触发熔断
        if (total < CIRCUIT_BREAKER_FAILURE_COUNT) {
            return false;
        }

        double failureRate = (double) failures / total;
        return failureRate >= CIRCUIT_BREAKER_THRESHOLD;
    }

    /**
     * 重置渠道统计（用于恢复）。
     *
     * @param type 渠道类型
     */
    public void resetChannelStats(ChannelType type) {
        requestCounters.remove(type);
        failureCounters.remove(type);
        lastErrors.remove(type);
        log.info("[ChannelDispatch] 渠道统计已重置: type={}", type);
    }

    /**
     * 增加请求计数。
     */
    private void incrementRequestCount(ChannelType type) {
        requestCounters.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 增加失败计数。
     */
    private void incrementFailureCount(ChannelType type) {
        failureCounters.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取请求计数。
     */
    private long getRequestCount(ChannelType type) {
        AtomicLong counter = requestCounters.get(type);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 获取失败计数。
     */
    private long getFailureCount(ChannelType type) {
        AtomicLong counter = failureCounters.get(type);
        return counter != null ? counter.get() : 0;
    }
}
