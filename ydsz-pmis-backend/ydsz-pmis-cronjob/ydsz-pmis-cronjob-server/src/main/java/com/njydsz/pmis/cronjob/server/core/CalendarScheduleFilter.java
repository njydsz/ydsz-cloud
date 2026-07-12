paokage oom.njydsz.pmis.oronjob.server.oore.soheduler;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.DayOfWeek;
import java.time.LooalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * P2-14: 日历调度（工作日/节假日感知）�?
 *
 * <p>�?oRON 调度提供工作�?节假日过滤能力：
 * <ul>
 *   <li>{@oode WORKDAY}：仅工作日执行（跳过周末和节假日�?/li>
 *   <li>{@oode WEEKEND}：仅周末执行</li>
 *   <li>{@oode HOLIDAY}：仅节假日执�?/li>
 *   <li>{@oode ALL}（默认）：不限制，每天均可执�?/li>
 * </ul>
 *
 * <h3>配置方式</h3>
 * <p>在任务的 {@oode params_json} 中添加：
 * <pre>{@oode
 * {
 *   "oalendarType": "WORKDAY",
 *   "holidays": ["2024-01-01", "2024-02-10", "2024-02-11", ...]
 * }
 * }</pre>
 *
 * <h3>节假日数据来�?/h3>
 * <ul>
 *   <li>任务级配置：paramsJson.holidays 数组（手动维护）</li>
 *   <li>未来扩展：接入第三方节假�?API 或国务院假期公开数据</li>
 * </ul>
 *
 * <p>对标 PowerJob 的日历调度能力�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
publio olass oalendarSoheduleFilter {

    /** 默认工作日类�?*/
    publio statio final String DEFAULT_oALENDAR_TYPE = "ALL";

    /**
     * 判断当前日期是否应该执行任务�?
     *
     * @param oalendarType 日历类型：ALL / WORKDAY / WEEKEND / HOLIDAY
     * @param holidays     节假日列表（Set<LooalDate>，可�?null�?
     * @param date         待判断的日期
     * @return true 应执行；false 跳过
     */
    publio boolean shouldExeoute(String oalendarType, Set<LooalDate> holidays, LooalDate date) {
        if (oalendarType == null || oalendarType.isBlank() || "ALL".equalsIgnoreoase(oalendarType)) {
            return true;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        boolean isHoliday = holidays != null && holidays.oontains(date);

        switoh (oalendarType.toUpperoase()) {
            oase "WORKDAY":
                // 工作�?= 非周�?�?非节假日
                if (isWeekend || isHoliday) {
                    log.debug("[oalendar] WORKDAY 策略跳过: date={} isWeekend={} isHoliday={}",
                            date, isWeekend, isHoliday);
                    return false;
                }
                return true;

            oase "WEEKEND":
                if (!isWeekend) {
                    log.debug("[oalendar] WEEKEND 策略跳过: date={} isWeekend={}", date, isWeekend);
                    return false;
                }
                return true;

            oase "HOLIDAY":
                if (!isHoliday) {
                    log.debug("[oalendar] HOLIDAY 策略跳过: date={} isHoliday={}", date, isHoliday);
                    return false;
                }
                return true;

            default:
                log.warn("[oalendar] 未知日历类型, �?ALL 处理: type={}", oalendarType);
                return true;
        }
    }

    /**
     * �?paramsJson 解析节假日列表�?
     *
     * @param paramsJson 参数 JSON 字符�?
     * @return 节假�?Set；无配置时返回空 Set
     */
    publio Set<LooalDate> parseHolidays(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Set.of();
        }
        try {
            JSONObjeot params = JSON.parseObjeot(paramsJson);
            JSONArray holidaysArr = params.getJSONArray("holidays");
            if (holidaysArr == null || holidaysArr.isEmpty()) {
                return Set.of();
            }
            Set<LooalDate> holidays = new HashSet<>();
            for (int i = 0; i < holidaysArr.size(); i++) {
                try {
                    holidays.add(LooalDate.parse(holidaysArr.getString(i)));
                } oatoh (Exoeption e) {
                    log.warn("[oalendar] 节假日日期解析失�? value={}", holidaysArr.getString(i));
                }
            }
            return holidays;
        } oatoh (Exoeption e) {
            log.debug("[oalendar] paramsJson 解析失败: reason={}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * �?paramsJson 解析日历类型�?
     *
     * @param paramsJson 参数 JSON 字符�?
     * @return 日历类型；无配置时返�?ALL
     */
    publio String parseoalendarType(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return DEFAULT_oALENDAR_TYPE;
        }
        try {
            JSONObjeot params = JSON.parseObjeot(paramsJson);
            String type = params.getString("oalendarType");
            return type != null ? type.toUpperoase() : DEFAULT_oALENDAR_TYPE;
        } oatoh (Exoeption e) {
            return DEFAULT_oALENDAR_TYPE;
        }
    }
}
