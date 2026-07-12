package com.njydsz.pmis.common.exception;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异常指标记录器
 *
 * <p>统计异常发生次数和类型分布，用于监控和告警。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExceptionMetricsRecorder {

    private final Map<String, AtomicLong> exceptionCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCodeCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalExceptions = new AtomicLong();

    /**
     * 记录异常
     *
     * @param exceptionClass 异常类名
     * @param errorCode      错误码（可为 null）
     */
    public void record(String exceptionClass, String errorCode) {
        totalExceptions.incrementAndGet();
        exceptionCounts.computeIfAbsent(exceptionClass, k -> new AtomicLong()).incrementAndGet();
        if (errorCode != null && !errorCode.isEmpty()) {
            errorCodeCounts.computeIfAbsent(errorCode, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * 获取总异常数
     *
     * @return 总异常数
     */
    public long getTotalExceptions() {
        return totalExceptions.get();
    }

    /**
     * 获取按异常类名分组的统计
     *
     * @return 统计 Map
     */
    public Map<String, Long> getExceptionCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        exceptionCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * 获取按错误码分组的统计
     *
     * @return 统计 Map
     */
    public Map<String, Long> getErrorCodeCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorCodeCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * 重置统计
     */
    public void reset() {
        totalExceptions.set(0);
        exceptionCounts.clear();
        errorCodeCounts.clear();
    }
}
