package com.njydsz.pmis.cronjob.core.scheduler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * P2-14: 日历调度（工作日/节假日感知）。
 *
 * <p>为 CRON 调度提供工作日/节假日过滤能力：
 * <ul>
 *   <li>{@code WORKDAY}：仅工作日执行（跳过周末和节假日）</li>
 *   <li>{@code WEEKEND}：仅周末执行</li>
 *   <li>{@code HOLIDAY}：仅节假日执行</li>
 *   <li>{@code ALL}（默认）：不限制，每天均可执行</li>
 * </ul>
 *
 * <h3>配置方式</h3>
 * <p>在任务的 {@code params_json} 中添加：
 * <pre>{@code
 * {
 *   "calendarType": "WORKDAY",
 *   "holidays": ["2024-01-01", "2024-02-10", "2024-02-11", ...]
 * }
 * }</pre>
 *
 * <h3>节假日数据来源</h3>
 * <ul>
 *   <li>任务级配置：paramsJson.holidays 数组（手动维护）</li>
 *   <li>未来扩展：接入第三方节假日 API 或国务院假期公开数据</li>
 * </ul>
 *
 * <p>对标 PowerJob 的日历调度能力。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class CalendarScheduleFilter {

    /** 默认工作日类型 */
    public static final String DEFAULT_CALENDAR_TYPE = "ALL";

    /**
     * 判断当前日期是否应该执行任务。
     *
     * @param calendarType 日历类型：ALL / WORKDAY / WEEKEND / HOLIDAY
     * @param holidays     节假日列表（Set<LocalDate>，可为 null）
     * @param date         待判断的日期
     * @return true 应执行；false 跳过
     */
    public boolean shouldExecute(String calendarType, Set<LocalDate> holidays, LocalDate date) {
        if (calendarType == null || calendarType.isBlank() || "ALL".equalsIgnoreCase(calendarType)) {
            return true;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        boolean isHoliday = holidays != null && holidays.contains(date);

        switch (calendarType.toUpperCase()) {
            case "WORKDAY":
                // 工作日 = 非周末 且 非节假日
                if (isWeekend || isHoliday) {
                    log.debug("[Calendar] WORKDAY 策略跳过: date={} isWeekend={} isHoliday={}",
                            date, isWeekend, isHoliday);
                    return false;
                }
                return true;

            case "WEEKEND":
                if (!isWeekend) {
                    log.debug("[Calendar] WEEKEND 策略跳过: date={} isWeekend={}", date, isWeekend);
                    return false;
                }
                return true;

            case "HOLIDAY":
                if (!isHoliday) {
                    log.debug("[Calendar] HOLIDAY 策略跳过: date={} isHoliday={}", date, isHoliday);
                    return false;
                }
                return true;

            default:
                log.warn("[Calendar] 未知日历类型, 按 ALL 处理: type={}", calendarType);
                return true;
        }
    }

    /**
     * 从 paramsJson 解析节假日列表。
     *
     * @param paramsJson 参数 JSON 字符串
     * @return 节假日 Set；无配置时返回空 Set
     */
    public Set<LocalDate> parseHolidays(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Set.of();
        }
        try {
            JSONObject params = JSON.parseObject(paramsJson);
            JSONArray holidaysArr = params.getJSONArray("holidays");
            if (holidaysArr == null || holidaysArr.isEmpty()) {
                return Set.of();
            }
            Set<LocalDate> holidays = new HashSet<>();
            for (int i = 0; i < holidaysArr.size(); i++) {
                try {
                    holidays.add(LocalDate.parse(holidaysArr.getString(i)));
                } catch (Exception e) {
                    log.warn("[Calendar] 节假日日期解析失败: value={}", holidaysArr.getString(i));
                }
            }
            return holidays;
        } catch (Exception e) {
            log.debug("[Calendar] paramsJson 解析失败: reason={}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 从 paramsJson 解析日历类型。
     *
     * @param paramsJson 参数 JSON 字符串
     * @return 日历类型；无配置时返回 ALL
     */
    public String parseCalendarType(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return DEFAULT_CALENDAR_TYPE;
        }
        try {
            JSONObject params = JSON.parseObject(paramsJson);
            String type = params.getString("calendarType");
            return type != null ? type.toUpperCase() : DEFAULT_CALENDAR_TYPE;
        } catch (Exception e) {
            return DEFAULT_CALENDAR_TYPE;
        }
    }
}
