package com.njydsz.common.feign.circuitbreaker;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Feign 熔断器策略接口。
 *
 * <p>定义 Feign 调用的熔断器策略，支持自定义实现。
 *
 * <p><b>功能特性：</b>
 * <ul>
 *   <li>熔断器模式：失败率超过阈值时快速失败</li>
 *   <li>慢调用降级：调用耗时超过阈值时触发降级</li>
 *   <li>自动恢复：半开状态尝试恢复服务调用</li>
 * </ul>
 *
 * <p><b>熔断器状态转换：</b>
 * <pre>
 * CLOSED（正常） → 失败率超过阈值 → OPEN（熔断）
 * OPEN（熔断） → 等待时间超过 → HALF_OPEN（半开）
 * HALF_OPEN（半开） → 成功 → CLOSED（恢复）
 * HALF_OPEN（半开） → 失败 → OPEN（重新熔断）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface FeignCircuitBreakerStrategy {

    /**
     * 获取熔断器策略名称。
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 判断熔断器是否启用。
     *
     * @return true=启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 判断是否允许调用。
     *
     * @param serviceName 服务名称
     * @return true=允许调用，false=触发熔断拒绝调用
     */
    default boolean allowRequest(String serviceName) {
        return true;
    }

    /**
     * 记录调用成功。
     *
     * @param serviceName 服务名称
     * @param elapsedTime 耗时（毫秒）
     */
    default void recordSuccess(String serviceName, long elapsedTime) {
    }

    /**
     * 记录调用失败。
     *
     * @param serviceName 服务名称
     * @param elapsedTime 耗时（毫秒）
     * @param throwable 异常信息
     */
    default void recordFailure(String serviceName, long elapsedTime, Throwable throwable) {
    }

    /**
     * 获取当前熔断器状态。
     *
     * @param serviceName 服务名称
     * @return 熔断器状态
     */
    default CircuitBreakerState getState(String serviceName) {
        return CircuitBreakerState.CLOSED;
    }

    /**
     * 获取熔断器指标。
     *
     * @param serviceName 服务名称
     * @return 熔断器指标
     */
    default CircuitBreakerMetrics getMetrics(String serviceName) {
        return new CircuitBreakerMetrics();
    }

    /**
     * 重置熔断器。
     *
     * @param serviceName 服务名称
     */
    default void reset(String serviceName) {
    }

    /**
     * 熔断器状态枚举。
     *
     * <ul>
     *   <li>{@code CLOSED} - 关闭状态，正常放行请求</li>
     *   <li>{@code OPEN} - 开启状态，拒绝所有请求</li>
     *   <li>{@code HALF_OPEN} - 半开状态，允许少量请求探测恢复</li>
     *   <li>{@code DISABLED} - 禁用状态，不进行熔断</li>
     *   <li>{@code FORCED_OPEN} - 强制开启状态，始终拒绝请求</li>
     * </ul>
     */
    enum CircuitBreakerState {
        CLOSED,
        OPEN,
        HALF_OPEN,
        DISABLED,
        FORCED_OPEN
    }

    /**
     * 熔断器指标数据。
     *
     * <p>包含熔断器的调用统计信息，用于监控和告警。
     * 使用 {@link LongAdder} 和 {@link AtomicLong} 保证线程安全。
     */
    class CircuitBreakerMetrics {
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successfulCalls = new LongAdder();
        private final LongAdder failedCalls = new LongAdder();
        private final LongAdder slowCalls = new LongAdder();
        private final AtomicLong failureRate = new AtomicLong();
        private final AtomicLong slowCallRate = new AtomicLong();
        private final AtomicLong averageDuration = new AtomicLong();
        private final AtomicLong maxDuration = new AtomicLong();

        public CircuitBreakerMetrics() {
        }

        /**
         * 获取总调用次数。
         *
         * <p>返回当前累计的总调用计数（成功 + 失败 + 慢调用）。
         *
         * @return 总调用次数
         */
        public long getTotalCalls() {
            return totalCalls.sum();
        }

        /**
         * 覆盖式设置总调用次数。
         *
         * <p>由于 {@code LongAdder} 无直接赋值 API，这里以 {@code reset()} 后 {@code add()} 整体替换原值（非累加），
         * 用于从外部指标源同步或重置统计。
         */
        public void setTotalCalls(long totalCalls) {
            this.totalCalls.reset();
            this.totalCalls.add(totalCalls);
        }

        /**
         * 原子递增总调用次数
         */
        public void incrementTotalCalls() {
            this.totalCalls.increment();
        }

        /**
         * 获取成功调用次数。
         *
         * @return 成功调用次数
         */
        public long getSuccessfulCalls() {
            return successfulCalls.sum();
        }

        /**
         * 覆盖式设置成功调用次数（重置后整体替换，语义同 {@link #setTotalCalls(long)}）。
         */
        public void setSuccessfulCalls(long successfulCalls) {
            this.successfulCalls.reset();
            this.successfulCalls.add(successfulCalls);
        }

        /**
         * 原子递增成功调用次数
         */
        public void incrementSuccessfulCalls() {
            this.successfulCalls.increment();
        }

        /**
         * 获取失败调用次数。
         *
         * @return 失败调用次数
         */
        public long getFailedCalls() {
            return failedCalls.sum();
        }

        /**
         * 覆盖式设置失败调用次数（重置后整体替换，语义同 {@link #setTotalCalls(long)}）。
         */
        public void setFailedCalls(long failedCalls) {
            this.failedCalls.reset();
            this.failedCalls.add(failedCalls);
        }

        /**
         * 原子递增失败调用次数
         */
        public void incrementFailedCalls() {
            this.failedCalls.increment();
        }

        /**
         * 获取慢调用次数。
         *
         * @return 慢调用次数
         */
        public long getSlowCalls() {
            return slowCalls.sum();
        }

        /**
         * 覆盖式设置慢调用次数（重置后整体替换，语义同 {@link #setTotalCalls(long)}）。
         */
        public void setSlowCalls(long slowCalls) {
            this.slowCalls.reset();
            this.slowCalls.add(slowCalls);
        }

        /**
         * 原子递增慢调用次数
         */
        public void incrementSlowCalls() {
            this.slowCalls.increment();
        }

        /**
         * 获取失败率（百分比）。
         *
         * <p>读取以 {@code doubleToLongBits} 打包存储的原子值并还原为 double。
         *
         * @return 失败率，取值范围 [0.0, 100.0]
         */
        public double getFailureRate() {
            return Double.longBitsToDouble(failureRate.get());
        }

        /**
         * 原子写入失败率（百分比）。
         *
         * <p>{@code AtomicLong} 无原生 double 支持，借 {@code Double.doubleToLongBits} 将双精度打包为 long 以保证原子性。
         */
        public void setFailureRate(double failureRate) {
            this.failureRate.set(Double.doubleToLongBits(failureRate));
        }

        /**
         * 获取慢调用率（百分比）。
         *
         * <p>读取以 {@code doubleToLongBits} 打包存储的原子值并还原为 double。
         *
         * @return 慢调用率，取值范围 [0.0, 100.0]
         */
        public double getSlowCallRate() {
            return Double.longBitsToDouble(slowCallRate.get());
        }

        /**
         * 原子写入慢调用率（百分比），存储方式同 {@link #setFailureRate(double)}。
         */
        public void setSlowCallRate(double slowCallRate) {
            this.slowCallRate.set(Double.doubleToLongBits(slowCallRate));
        }

        /**
         * 获取平均调用耗时。
         *
         * @return 平均耗时（毫秒）
         */
        public long getAverageDuration() {
            return averageDuration.get();
        }

        /**
         * 覆盖式设置平均调用耗时（毫秒）。
         */
        public void setAverageDuration(long averageDuration) {
            this.averageDuration.set(averageDuration);
        }

        /**
         * 获取最大调用耗时。
         *
         * @return 最大耗时（毫秒）
         */
        public long getMaxDuration() {
            return maxDuration.get();
        }

        /**
         * 覆盖式设置最大调用耗时（毫秒）。
         */
        public void setMaxDuration(long maxDuration) {
            this.maxDuration.set(maxDuration);
        }

        /**
         * 原子更新最大调用耗时——仅当新值大于当前值时写入。
         *
         * @param candidateDuration 候选耗时（毫秒）
         */
        public void updateMaxDuration(long candidateDuration) {
            long prev;
            do {
                prev = maxDuration.get();
                if (candidateDuration <= prev) {
                    return;
                }
            } while (!maxDuration.compareAndSet(prev, candidateDuration));
        }
    }
}
