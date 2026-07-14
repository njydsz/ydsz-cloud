package com.njydsz.pmis.cronjob.server.handler;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.json.YdszJson;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.job.JobHandler;
import com.njydsz.pmis.cronjob.server.service.job.ReportScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 报表定时生成与分发 Job。
 *
 * <p>Bean 名称 = {@code reportScheduleJobHandler}，
 * 在 pmis_job 表插入记录：handler=reportScheduleJobHandler。
 *
 * <p>调度时间：
 * <ul>
 *   <li>日报：每天 08:00（cron=0 0 8 * * ?，param=DAILY）</li>
 *   <li>周报：每周一 08:00（cron=0 0 8 ? * MON，param=WEEKLY）</li>
 *   <li>月报：每月 1 日 08:00（cron=0 0 8 1 * ?，param=MONTHLY）</li>
 * </ul>
 *
 * <p>参数 JSON 格式：{@code "DAILY"} 或 {@code {"type":"DAILY"}}
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("reportScheduleJobHandler")
@RequiredArgsConstructor
public class ReportScheduleJobHandler implements JobHandler {

    private final ReportScheduleService reportScheduleService;

    /**
     * 执行报表生成与分发。
     *
     * @param paramsJson 参数，可为 "DAILY"/"WEEKLY"/"MONTHLY" 或 JSON {"type":"DAILY"}
     * @return 执行结果
     */
    @Override
    public Object execute(String paramsJson) {
        log.info("[ReportScheduleJob] 开始执行，参数: {}", paramsJson);
        String type = parseType(paramsJson);
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        try {
            switch (type.toUpperCase()) {
                case "DAILY" -> reportScheduleService.executeDailyReports();
                case "WEEKLY" -> reportScheduleService.executeWeeklyReports();
                case "MONTHLY" -> reportScheduleService.executeMonthlyReports();
                default -> {
                    log.warn("[ReportScheduleJob] 未知报表类型: {}", type);
                    result.put("ok", false);
                    result.put("error", "unknown type: " + type);
                    return result;
                }
            }
            result.put("ok", true);
        } catch (Exception e) {
            log.error("[ReportScheduleJob] 执行失败: {}", e.getMessage(), e);
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        log.info("[ReportScheduleJob] 执行完成");
        return result;
    }

    /**
     * 从参数中解析报表类型。
     *
     * @param paramsJson 参数 JSON
     * @return 报表类型（DAILY/WEEKLY/MONTHLY）
     */
    private String parseType(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return "DAILY";
        }
        String trimmed = paramsJson.trim();
        // 尝试 JSON 解析 {"type":"DAILY"}
        if (trimmed.startsWith("{")) {
            try {
                Map<String, Object> obj = YdszJson.parseMap(trimmed);
                if (obj != null && obj.containsKey("type")) {
                    return obj.getString("type");
                }
            } catch (Exception e) {
                log.warn("[ReportScheduleJob] 参数 JSON 解析失败，按原始字符串处理: {}", e.getMessage());
            }
        }
        return trimmed;
    }
}
