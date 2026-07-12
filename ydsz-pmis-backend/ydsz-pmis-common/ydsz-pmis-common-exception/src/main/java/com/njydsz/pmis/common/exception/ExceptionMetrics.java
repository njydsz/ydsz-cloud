package com.njydsz.pmis.common.exception;

/**
 * 异常指标
 *
 * <p>异常指标的快照数据。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExceptionMetrics {

    private final long totalExceptions;
    private final java.util.Map<String, Long> exceptionCounts;
    private final java.util.Map<String, Long> errorCodeCounts;
    private final long timestamp;

    public ExceptionMetrics(long totalExceptions,
                            java.util.Map<String, Long> exceptionCounts,
                            java.util.Map<String, Long> errorCodeCounts) {
        this.totalExceptions = totalExceptions;
        this.exceptionCounts = exceptionCounts;
        this.errorCodeCounts = errorCodeCounts;
        this.timestamp = System.currentTimeMillis();
    }

    public long getTotalExceptions() {
        return totalExceptions;
    }

    public java.util.Map<String, Long> getExceptionCounts() {
        return exceptionCounts;
    }

    public java.util.Map<String, Long> getErrorCodeCounts() {
        return errorCodeCounts;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 从记录器创建快照
     *
     * @param recorder 记录器
     * @return 快照
     */
    public static ExceptionMetrics snapshot(ExceptionMetricsRecorder recorder) {
        return new ExceptionMetrics(
                recorder.getTotalExceptions(),
                recorder.getExceptionCounts(),
                recorder.getErrorCodeCounts()
        );
    }
}
