package com.njydsz.cronjob.web.controller.schedule;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.server.service.impl.schedule.ScheduleCalendarService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.LocalDateTimeVO;

/**
 * 调度日历 Controller（P2-10）。
 *
 * <p>提供调度日历可视化接口，预计算任务在未来时间段内的触发时间点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "调度日历")
@RestController
@RequestMapping("/cronjob/calendar")
@RequiredArgsConstructor
public class ScheduleCalendarController {

    /** 调度日历服务 */
    private final ScheduleCalendarService scheduleCalendarService;

    /**
     * 查询单个任务的未来触发时间列表。
     *
     * @param jobKey   任务 KEY
     * @param hours    时间窗口（小时，默认 24）
     * @param maxCount 最多计算次数（默认 100）
     * @return 触发时间列表
     */
    @Operation(summary = "查询任务未来触发时间")
    @GetMapping("/fireTimes")
    public BaseResponse<List<LocalDateTimeVO>> getUpcomingFireTimes(
            @RequestParam String jobKey,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int maxCount) {
        return BaseResponse.success(scheduleCalendarService.getUpcomingFireTimes(
                jobKey, LocalDateTime.now(), maxCount));
    }

    /**
     * 查询所有 CRON 任务的调度日历。
     *
     * @param hours     时间窗口（小时，默认 24）
     * @param maxPerJob 每个任务最多计算次数（默认 50）
     * @return 调度日历项列表（按时间排序）
     */
    @Operation(summary = "查询调度日历")
    @GetMapping("/schedule")
    public BaseResponse<List<ScheduleCalendarService.ScheduleItem>> getScheduleCalendar(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int maxPerJob) {
        return BaseResponse.success(scheduleCalendarService.getScheduleCalendar(
                LocalDateTime.now(), hours, maxPerJob));
    }
}
