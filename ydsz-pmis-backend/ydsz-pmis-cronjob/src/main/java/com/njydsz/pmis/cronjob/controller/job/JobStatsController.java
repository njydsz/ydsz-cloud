package com.njydsz.pmis.cronjob.controller.job;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.cronjob.entity.log.JobDailyStatsDO;
import com.njydsz.pmis.cronjob.mapper.log.JobDailyStatsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 查询指定任务的每日统计（趋势图数据源）。
     *
     * @param jobId     任务 ID
     * @param startDate 起始日期（含，格式 yyyy-MM-dd）
     * @param endDate   结束日期（含，格式 yyyy-MM-dd）
     * @return 每日统计列表（按日期升序）
     */
    @Operation(summary = "查询每日执行统计趋势")
    @PrePermission(PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/daily")
    public Result<List<JobDailyStatsDO>> daily(
            @RequestParam String jobId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(jobDailyStatsMapper.selectByJobIdAndDateRange(jobId, startDate, endDate));
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
    @PrePermission(PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
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
        return Result.ok(summary);
    }
}
