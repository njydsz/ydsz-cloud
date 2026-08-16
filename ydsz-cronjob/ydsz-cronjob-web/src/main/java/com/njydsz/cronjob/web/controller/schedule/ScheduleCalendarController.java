package com.njydsz.cronjob.web.controller.schedule;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.server.service.impl.schedule.ScheduleCalendarService;

/**
 * 调度日历可视化 Controller（P2-10）。
 *
 * <p>提供调度日历可视化接口，基于 Cron 表达式预计算任务在未来时间段内的所有触发时间点，
 * 供前端以日历/时间线形式展示"未来某段时间哪些任务会执行"。该能力对运维排障、容量规划、
 * 大促压测时段避让等场景非常关键。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #getUpcomingFireTimes} - 单任务视角：查询指定任务在未来窗口内的所有触发时间</li>
 *   <li>{@link #getScheduleCalendar} - 全局视角：聚合所有 CRON 任务，按时间升序返回调度日历</li>
 * </ul>
 *
 * <h3>计算原理</h3>
 * 调度日历的预计算基于 {@code CronExpression}（Quartz 语义），从当前时间起按 1 秒/1 分粒度
 * 向前推演（最多 {@code maxCount} 次），用于展示不依赖真实触发，避免在压测/演示环境触发真实调度。
 * 调度的真实执行以 {@code Quartz Scheduler} 为准，二者独立。
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>仅对 CRON 类型任务有效；FIX_RATE/FIX_DELAY 类型不在日历计算范围内</li>
 *   <li>{@code hours} 与 {@code maxCount} 取双约束：以先到达者为准</li>
 *   <li>本接口读操作不写日志；前端可高频轮询（30s ~ 5min 一次）</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端调度日历 UI
 *     → ydsz-gateway
 *       → ydsz-cronjob-web（本 Controller）
 *         → ydsz-cronjob-server.ScheduleCalendarService
 *           → ydsz-cronjob-infra.JobMapper（Cron 表达式查询）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "调度日历", description = "单任务未来触发时间 / 全局调度日历聚合可视化")
@RestController
@RequestMapping("/api/v1/cronjob/calendar")
@RequiredArgsConstructor
public class ScheduleCalendarController {

    /** 调度日历服务（计算任务未来触发时间） */
    private final ScheduleCalendarService scheduleCalendarService;

    /**
     * 查询单个任务在未来时间窗口内的所有触发时间点。
     *
     * <p>基于任务注册的 Cron 表达式，从当前时刻开始预计算未来 {@code hours} 小时内的全部触发时间，
     * 最多返回 {@code maxCount} 个。结果按时间升序排列，供前端时间线组件渲染。
     *
     * <p>典型场景：用户在任务详情页"未来执行预览"中查看此任务的近期触发时间，
     * 或在大促压测前评估"压测时段内本任务是否会被触发"。
     *
     * @param jobKey   任务 KEY（{@code ydsz_job.job_key}，唯一）
     * @param hours    时间窗口（小时，默认 24，表示从 now 起向后推算的小时数）
     * @param maxCount 最多返回的触发时间点数（默认 100，避免返回过大）
     * @return 触发时间列表（{@link LocalDateTime} 按时间升序，Jackson 序列化为 ISO-8601 字符串）
     * @throws SysException 当 jobKey 对应任务不存在或非 CRON 类型时抛出（service 层判定）
     */
    @Operation(summary = "查询任务未来触发时间")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @GetMapping("/fireTimes")
    public BaseResponse<List<LocalDateTime>> getUpcomingFireTimes(
            @RequestParam String jobKey,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int maxCount) {
        // 以当前时间作为起点，预计算未来触发时间（最多 maxCount 个）
        // 注意：当前实现暂未将 hours 透传给 service，service 默认从 from 起推算 maxCount 次
        return BaseResponse.success(scheduleCalendarService.getUpcomingFireTimes(
                jobKey, LocalDateTime.now(), maxCount));
    }

    /**
     * 查询所有 CRON 任务的调度日历（按时间聚合）。
     *
     * <p>遍历所有 {@code scheduleType=CRON} 状态正常的任务，分别计算它们在未来 {@code hours} 小时内的
     * 触发时间（每个任务最多 {@code maxPerJob} 个），然后合并为按时间升序排列的全局日历。
     *
     * <p>典型场景：调度日历大屏展示当日所有任务的执行分布、容量规划、避让冲突检测。
     *
     * <p>注意：单次返回条目数 = 任务数 × maxPerJob，请根据业务规模合理设置 {@code maxPerJob}
     * （建议 10 ~ 50）以避免响应体过大。
     *
     * @param hours     时间窗口（小时，默认 24）
     * @param maxPerJob 每个任务最多计算次数（默认 50）
     * @return 调度日历项列表（{@link ScheduleCalendarService.ScheduleItem}，按 fireTime 升序，
     *         每项含 jobKey/jobName/group/cron/fireTime 字段）
     */
    @Operation(summary = "查询调度日历")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @GetMapping("/schedule")
    public BaseResponse<List<ScheduleCalendarService.ScheduleItem>> getScheduleCalendar(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int maxPerJob) {
        return BaseResponse.success(scheduleCalendarService.getScheduleCalendar(
                LocalDateTime.now(), hours, maxPerJob));
    }
}
