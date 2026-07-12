package com.njydsz.pmis.common.feign.monitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Feign 调用指标收集器。
 *
 * <p>收集并聚合 Feign 调用的关键指标，为监控和告警提供数据支撑。
 *
 * <p>收集的指标包括：
 * <ul>
 *   <li><b>请求总数</b>：按服务和方法统计</li>
 *   <li><b>成功次数</b>：HTTP 2xx 响应</li>
 *   <li><b>失败次数</b>：HTTP 4xx/5xx 响应及异常</li>
 *   <li><b>总耗时</b>：用于计算平均耗时</li>
 *   <li><b>最大耗时</b>：记录最慢调用</li>
 *   <li><b>最小耗时</b>：记录最快调用</li>
 * </ul>
 *
 * <p>使用方式：
 * <ul>
 *   <li>通过 AOP 或拦截器在 Feign 调用前后采集数据</li>
 *   <li>定时上报到监控系统（如 Prometheus、InfluxDB）</li>
 *   <li>通过 JMX 暴露实时指标</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class FeignMetricsCollector {

    /** 服务指标映射 */
    private final ConcurrentHashMap<String, ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
    /** 过期阈值（毫秒），超过此时间未更新的指标将被清理 */
    private static final long STALE_THRESHOLD_MS = 60 * 60 * 1000L;

    /**
     * 记录一次 Feign 请求发起。
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     */
    public void recordRequest(String serviceName, String methodName) {
        getServiceMetrics(serviceName).recordRequest(methodName);
        cleanupStaleEntries();
    }

    /**
     * 记录一次 Feign 调用成功。
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param elapsedTime 耗时（毫秒）
     */
    public void recordSuccess(String serviceName, String methodName, long elapsedTime) {
        getServiceMetrics(serviceName).recordSuccess(methodName, elapsedTime);
    }

    /**
     * 记录一次 Feign 调用失败（HTTP 4xx/5xx）。
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param elapsedTime 耗时（毫秒）
     * @param statusCode  HTTP 状态码
     */
    public void recordFailure(String serviceName, String methodName, long elapsedTime, int statusCode) {
        getServiceMetrics(serviceName).recordFailure(methodName, elapsedTime, statusCode);
    }

    /**
     * 记录一次 Feign 调用异常。
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param elapsedTime 耗时（毫秒）
     * @param throwable   异常信息
     */
    public void recordError(String serviceName, String methodName, long elapsedTime, Throwable throwable) {
        getServiceMetrics(serviceName).recordError(methodName, elapsedTime, throwable);
    }

    private ServiceMetrics getServiceMetrics(String serviceName) {
        return serviceMetricsMap.computeIfAbsent(serviceName, k -> new ServiceMetrics());
    }

    private void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        serviceMetricsMap.entrySet().removeIf(entry ->
                now - entry.getValue().getLastUpdateTime() > STALE_THRESHOLD_MS
        );
    }

    /**
     * 获取所有服务的指标快照。
     *
     * @return Feign 指标快照
     */
    public FeignMetricsSnapshot getSnapshot() {
        FeignMetricsSnapshot snapshot = new FeignMetricsSnapshot();
        serviceMetricsMap.forEach((serviceName, metrics) -> {
            ServiceMetricsSnapshot serviceSnapshot = metrics.getSnapshot();
            snapshot.addServiceMetrics(serviceName, serviceSnapshot);
        });
        return snapshot;
    }

    public void reset() {
        serviceMetricsMap.clear();
    }

    /**
     * 服务维度的指标统计。
     *
     * <p>按方法维度聚合单个服务的调用指标。
     */
    public static class ServiceMetrics {
        /** 方法指标映射 */
        private final ConcurrentHashMap<String, MethodMetrics> methodMetricsMap = new ConcurrentHashMap<>();
        /** 最后更新时间戳 */
        private final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());

        public void recordRequest(String methodName) {
            getMethodMetrics(methodName).recordRequest();
            lastUpdateTime.set(System.currentTimeMillis());
        }

        public void recordSuccess(String methodName, long elapsedTime) {
            getMethodMetrics(methodName).recordSuccess(elapsedTime);
            lastUpdateTime.set(System.currentTimeMillis());
        }

        public void recordFailure(String methodName, long elapsedTime, int statusCode) {
            getMethodMetrics(methodName).recordFailure(elapsedTime, statusCode);
            lastUpdateTime.set(System.currentTimeMillis());
        }

        public void recordError(String methodName, long elapsedTime, Throwable throwable) {
            getMethodMetrics(methodName).recordError(elapsedTime);
            lastUpdateTime.set(System.currentTimeMillis());
        }

        public long getLastUpdateTime() {
            return lastUpdateTime.get();
        }

        private MethodMetrics getMethodMetrics(String methodName) {
            return methodMetricsMap.computeIfAbsent(methodName, k -> new MethodMetrics());
        }

        public ServiceMetricsSnapshot getSnapshot() {
            ServiceMetricsSnapshot snapshot = new ServiceMetricsSnapshot();
            methodMetricsMap.forEach((methodName, metrics) -> {
                MethodMetricsSnapshot methodSnapshot = metrics.getSnapshot();
                snapshot.addMethodMetrics(methodName, methodSnapshot);
            });
            return snapshot;
        }
    }

    /**
     * 方法维度的指标统计。
     *
     * <p>记录单个方法的请求次数、成功/失败次数、耗时等指标。
     */
    public static class MethodMetrics {
        /** 总请求数 */
        private final LongAdder totalRequests = new LongAdder();
        /** 成功次数 */
        private final LongAdder totalSuccesses = new LongAdder();
        /** 失败次数（HTTP 4xx/5xx） */
        private final LongAdder totalFailures = new LongAdder();
        /** 异常次数 */
        private final LongAdder totalErrors = new LongAdder();
        /** 总耗时（毫秒） */
        private final AtomicLong totalElapsedTime = new AtomicLong(0);
        /** 最大耗时（毫秒） */
        private final AtomicLong maxElapsedTime = new AtomicLong(0);
        /** 最小耗时（毫秒） */
        private final AtomicLong minElapsedTime = new AtomicLong(Long.MAX_VALUE);
        /** 按 HTTP 状态码统计的次数 */
        private final ConcurrentHashMap<Integer, LongAdder> statusCodeCounts = new ConcurrentHashMap<>();

        public void recordRequest() {
            totalRequests.increment();
        }

        public void recordSuccess(long elapsedTime) {
            totalSuccesses.increment();
            recordElapsedTime(elapsedTime);
        }

        public void recordFailure(long elapsedTime, int statusCode) {
            totalFailures.increment();
            statusCodeCounts.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
            recordElapsedTime(elapsedTime);
        }

        public void recordError(long elapsedTime) {
            totalErrors.increment();
            recordElapsedTime(elapsedTime);
        }

        private void recordElapsedTime(long elapsedTime) {
            totalElapsedTime.addAndGet(elapsedTime);
            maxElapsedTime.updateAndGet(current -> Math.max(current, elapsedTime));
            minElapsedTime.updateAndGet(current -> Math.min(current, elapsedTime));
        }

        public MethodMetricsSnapshot getSnapshot() {
            long total = totalRequests.sum();
            long success = totalSuccesses.sum();
            long failure = totalFailures.sum();
            long error = totalErrors.sum();
            long elapsed = totalElapsedTime.get();
            return new MethodMetricsSnapshot(
                    total,
                    success,
                    failure,
                    error,
                    elapsed,
                    maxElapsedTime.get(),
                    minElapsedTime.get() == Long.MAX_VALUE ? 0 : minElapsedTime.get(),
                    calculateSuccessRate(total, success)
            );
        }

        private double calculateSuccessRate(long total, long success) {
            if (total == 0) {
                return 0.0;
            }
            return (double) success / total * 100.0;
        }
    }

    /**
     * Feign 指标聚合快照，包含所有服务的指标数据。
     */
    public static class FeignMetricsSnapshot {
        /** 服务指标映射 */
        private final ConcurrentHashMap<String, ServiceMetricsSnapshot> serviceMetrics = new ConcurrentHashMap<>();

        public void addServiceMetrics(String serviceName, ServiceMetricsSnapshot snapshot) {
            serviceMetrics.put(serviceName, snapshot);
        }

        public ConcurrentHashMap<String, ServiceMetricsSnapshot> getServiceMetrics() {
            return serviceMetrics;
        }
    }

    /**
     * 服务指标快照，包含该服务下所有方法的指标数据。
     */
    public static class ServiceMetricsSnapshot {
        /** 方法指标映射 */
        private final ConcurrentHashMap<String, MethodMetricsSnapshot> methodMetrics = new ConcurrentHashMap<>();

        public void addMethodMetrics(String methodName, MethodMetricsSnapshot snapshot) {
            methodMetrics.put(methodName, snapshot);
        }

        public ConcurrentHashMap<String, MethodMetricsSnapshot> getMethodMetrics() {
            return methodMetrics;
        }
    }

    /**
     * 方法指标快照，包含单个方法的聚合统计数据。
     */
    public static class MethodMetricsSnapshot {
        /** 总请求数 */
        private final long totalRequests;
        /** 成功次数 */
        private final long successCount;
        /** 失败次数 */
        private final long failureCount;
        /** 异常次数 */
        private final long errorCount;
        /** 总耗时（毫秒） */
        private final long totalElapsedTime;
        /** 最大耗时（毫秒） */
        private final long maxElapsedTime;
        /** 最小耗时（毫秒） */
        private final long minElapsedTime;
        /** 成功率（百分比） */
        private final double successRate;

        public MethodMetricsSnapshot(long totalRequests, long successCount, long failureCount,
                                    long errorCount, long totalElapsedTime, long maxElapsedTime,
                                    long minElapsedTime, double successRate) {
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errorCount = errorCount;
            this.totalElapsedTime = totalElapsedTime;
            this.maxElapsedTime = maxElapsedTime;
            this.minElapsedTime = minElapsedTime;
            this.successRate = successRate;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public long getTotalElapsedTime() {
            return totalElapsedTime;
        }

        public long getMaxElapsedTime() {
            return maxElapsedTime;
        }

        public long getMinElapsedTime() {
            return minElapsedTime;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public long getAverageElapsedTime() {
            long total = successCount + failureCount + errorCount;
            if (total == 0) {
                return 0;
            }
            return totalElapsedTime / total;
        }
    }
}
