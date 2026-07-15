package com.njydsz.pmis.common.lock.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.pmis.common.json.Json;

/**
 * 分布式锁指标导出器
 *
 * <p>将锁指标导出为 JSON 格式，供外部系统（如监控面板、日志聚合系统）使用。
 *
 * <p>使用项目统一的 {@link Json} 引擎序列化，替代手写 JSON 序列化逻辑，
 * 确保与全项目 JSON 处理保持一致。
 *
 * <p>导出的 JSON 包含：
 * <ul>
 *   <li>锁状态（成功次数、失败次数、释放次数等）</li>
 *   <li>指标详情（竞争次数、活跃锁、超时次数、续期次数等）</li>
 *   <li>统计信息（平均等待时间、平均持有时间）</li>
 *   <li>导出时间戳</li>
 * </ul>
 *
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

        return Json.toJson(root);
    }
}
