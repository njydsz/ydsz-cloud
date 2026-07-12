package com.njydsz.pmis.common.lock.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分布式锁指标导出器
 *
 * <p>将锁指标导出为 JSON 格式，供外部系统（如监控面板、日志聚合系统）使用。
 *
 * <p>导出的 JSON 包含：
 * <ul>
 *   <li>锁状态（成功次数、失败次数、释放次数等）</li>
 *   <li>指标详情（竞争次数、活跃锁、超时次数、续期次数等）</li>
 *   <li>统计信息（平均等待时间、平均持有时间）</li>
 *   <li>导出时间戳</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class LockMetricsExporter {

    /**
     * 锁指标收集器
     */
    private final LockMetrics lockMetrics;

    /**
     * 构造锁指标导出器
     *
     * @param lockMetrics 锁指标收集器
     */
    public LockMetricsExporter(LockMetrics lockMetrics) {
        this.lockMetrics = lockMetrics;
    }

    /**
     * 导出锁指标为 JSON 字符串
     *
     * @return JSON 格式的指标数据
     */
    public String exportToJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", Instant.now().toString());

        // 锁状态
        Map<String, Object> lockStatus = new LinkedHashMap<>();
        lockStatus.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
        lockStatus.put("acquireFailCount", lockMetrics.getAcquireFailCount());
        lockStatus.put("releaseCount", lockMetrics.getReleaseCount());
        lockStatus.put("activeLocks", lockMetrics.getActiveLocks());
        root.put("lockStatus", lockStatus);

        // 指标详情
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("competitionCount", lockMetrics.getCompetitionCount());
        metrics.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
        metrics.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
        root.put("metrics", metrics);

        // 统计信息
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("averageWaitTimeMillis", String.format("%.2f", lockMetrics.getAverageWaitTimeMillis()));
        statistics.put("averageHoldTimeMillis", String.format("%.2f", lockMetrics.getAverageHoldTimeMillis()));
        root.put("statistics", statistics);

        return toJson(root);
    }

    /**
     * 简单的 Map 转 JSON（不依赖第三方 JSON 库）
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append('"');
            sb.append(':');
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 将值转换为 JSON 字符串表示
     *
     * @param value 要转换的值
     * @return JSON 格式的字符串
     */
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return '"' + escapeJson((String) value) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return toJson(map);
        }
        return '"' + escapeJson(value.toString()) + '"';
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
