package com.njydsz.pmis.cronjob.web.controller.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobDailyStatsDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.infra.mapper.log.JobDailyStatsMapper;
import com.njydsz.pmis.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.pmis.cronjob.server.metrics.CronjobMetrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 任务执行统计 Controller（P2-3 执行历史趋势可视化）。
 *
 * <p>提供每日统计趋势查询与区间汇总查询，供前端折线图/汇总卡片展示。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "任务执行统计")
@RestController
@RequestMapping("/cronjob/stats")
@RequiredArgsConstructor
public class JobStatsController {

    /** 每日统计 Mapper */
    private final JobDailyStatsMapper jobDailyStatsMapper;
    /** P1-2: 日志 Mapper（仪表盘实时数据查询） */
    private final JobLogMapper jobLogMapper;
    /** P1-2: 任务 Mapper（任务总数统计） */
    private final JobMapper jobMapper;
    /** P1-2: Prometheus 指标（可选注入） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    /**
     * 查询指定任务的每日统计（趋势图数据源）。
     *
     * @param jobId     任务 ID
     * @param startDate 起始日期（含，格式 yyyy-MM-dd）
     * @param endDate   结束日期（含，格式 yyyy-MM-dd）
     * @return 每日统计列表（按日期升序）
     */
    @Operation(summary = "查询每日执行统计趋势")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/daily")
    public BaseResponse<List<JobDailyStatsDO>> daily(
            @RequestParam String jobId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return BaseResponse.ok(jobDailyStatsMapper.selectByJobIdAndDateRange(jobId, startDate, endDate));
    }

    /**
     * 查询指定任务在日期范围内的汇总统计。
     *
     * <p>汇总字段：总触发次数 / 总成功次数 / 总失败次数 / 平均耗时（毫秒）。
     *
     * @param jobId     任务 ID
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 汇总统计 Map
     */
    @Operation(summary = "查询执行统计汇总")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/summary")
    public BaseResponse<Map<String, Object>> summary(
            @RequestParam String jobId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<JobDailyStatsDO> list = jobDailyStatsMapper.selectByJobIdAndDateRange(jobId, startDate, endDate);
        long fireCount = 0L;
        long successCount = 0L;
        long failCount = 0L;
        long timeoutCount = 0L;
        long totalDuration = 0L;
        long durationSamples = 0L;
        for (JobDailyStatsDO s : list) {
            if (s.getFireCount() != null) {
                fireCount += s.getFireCount();
            }
            if (s.getSuccessCount() != null) {
                successCount += s.getSuccessCount();
            }
            if (s.getFailCount() != null) {
                failCount += s.getFailCount();
            }
            if (s.getTimeoutCount() != null) {
                timeoutCount += s.getTimeoutCount();
            }
            if (s.getAvgDurationMs() != null) {
                totalDuration += s.getAvgDurationMs();
                durationSamples++;
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("jobId", jobId);
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        summary.put("fireCount", fireCount);
        summary.put("successCount", successCount);
        summary.put("failCount", failCount);
        summary.put("timeoutCount", timeoutCount);
        summary.put("avgDurationMs", durationSamples > 0 ? totalDuration / durationSamples : 0L);
        return BaseResponse.ok(summary);
    }

    // ==================== P1-2: 运维监控仪表盘增强 ====================

    /**
     * P1-2: 获取全局监控仪表盘数据。
     *
     * <p>返回调度引擎的整体运行状态概览，包括：
     * <ul>
     *   <li>任务总数/正常运行/已暂停/异常</li>
     *   <li>今日执行统计（触发/成功/失败/成功率）</li>
     *   <li>当前运行中任务数</li>
     *   <li>系统负载评分</li>
     *   <li>最近失败任务列表（Top 10）</li>
     * </ul>
     *
     * @return 仪表盘数据
     */
    @Operation(summary = "全局监控仪表盘")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/dashboard")
    public BaseResponse<Map<String, Object>> dashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        // 1. 任务状态分布
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("total", jobMapper.selectCount(null));
        taskStats.put("normal", jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "NORMAL")));
        taskStats.put("paused", jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "PAUSED")));
        taskStats.put("error", jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "ERROR")));
        taskStats.put("autoPaused", jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "AUTO_PAUSED")));
        dashboard.put("taskStats", taskStats);

        // 2. 今日执行统计
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> todayExec = new HashMap<>();
        Long todayTotal = jobLogMapper.selectCount(new LambdaQueryWrapper<JobLogDO>().ge(JobLogDO::getStartTime, todayStart));
        Long todaySuccess = jobLogMapper.selectCount(new LambdaQueryWrapper<JobLogDO>().ge(JobLogDO::getStartTime, todayStart).eq(JobLogDO::getStatus, "SUCCESS"));
        Long todayFailed = jobLogMapper.selectCount(new LambdaQueryWrapper<JobLogDO>().ge(JobLogDO::getStartTime, todayStart).eq(JobLogDO::getStatus, "FAILED"));
        Long todayRunning = jobLogMapper.selectCount(new LambdaQueryWrapper<JobLogDO>().eq(JobLogDO::getStatus, "RUNNING"));
        todayExec.put("total", todayTotal);
        todayExec.put("success", todaySuccess);
        todayExec.put("failed", todayFailed);
        todayExec.put("running", todayRunning);
        todayExec.put("successRate", todayTotal != null && todayTotal > 0 ? String.format("%.1f%%", todaySuccess * 100.0 / todayTotal) : "N/A");
        dashboard.put("todayExec", todayExec);

        // 3. Prometheus 指标
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics != null) {
            Map<String, Object> systemMetrics = new HashMap<>();
            systemMetrics.put("running", todayRunning);
            dashboard.put("systemMetrics", systemMetrics);
        }

        return BaseResponse.ok(dashboard);
    }

    /**
     * P1-2: 获取最近失败任务列表。
     *
     * @param limit 返回条数（默认 10）
     * @return 失败日志列表
     */
    @Operation(summary = "最近失败任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/recent-failures")
    public BaseResponse<List<JobLogDO>> recentFailures(@RequestParam(defaultValue = "10") int limit) {
        return BaseResponse.ok(jobLogMapper.selectList(
                new LambdaQueryWrapper<JobLogDO>()
                        .eq(JobLogDO::getStatus, "FAILED")
                        .orderByDesc(JobLogDO::getStartTime)
                        .last("LIMIT " + Math.min(limit, 100))));
    }

    /**
     * P1-2: 获取任务执行热力图数据。
     *
     * <p>按小时聚合统计任务执行分布，用于识别高峰时段。
     *
     * @param date 查询日期（默认今天）
     * @return 24 小时执行分布
     */
    @Operation(summary = "执行热力图（按小时分布）")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/heatmap")
    public BaseResponse<List<Map<String, Object>>> heatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        List<Map<String, Object>> heatmap = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourStart = queryDate.atTime(hour, 0);
            LocalDateTime hourEnd = queryDate.atTime(hour, 59, 59);
            Long count = jobLogMapper.selectCount(
                    new LambdaQueryWrapper<JobLogDO>()
                            .ge(JobLogDO::getStartTime, hourStart)
                            .le(JobLogDO::getStartTime, hourEnd));
            Map<String, Object> entry = new HashMap<>();
            entry.put("hour", hour);
            entry.put("count", count);
            heatmap.add(entry);
        }
        return BaseResponse.ok(heatmap);
    }
}
