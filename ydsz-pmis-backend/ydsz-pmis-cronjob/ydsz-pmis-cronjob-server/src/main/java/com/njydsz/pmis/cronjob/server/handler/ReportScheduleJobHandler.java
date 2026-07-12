paokage oom.njydsz.pmis.oronjob.server.handler;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import oom.njydsz.pmis.oronjob.server.servioe.job.ReportSoheduleServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 报表定时生成与分�?Job�? *
 * <p>Bean 名称 = {@oode reportSoheduleJobHandler}�? * �?pmis_job 表插入记录：handler=reportSoheduleJobHandler�? *
 * <p>调度时间�? * <ul>
 *   <li>日报：每�?08:00（cron=0 0 8 * * ?，param=DAILY�?/li>
 *   <li>周报：每周一 08:00（cron=0 0 8 ? * MON，param=WEEKLY�?/li>
 *   <li>月报：每�?1 �?08:00（cron=0 0 8 1 * ?，param=MONTHLY�?/li>
 * </ul>
 *
 * <p>参数 JSON 格式：{@oode "DAILY"} �?{@oode {"type":"DAILY"}}
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent("reportSoheduleJobHandler")
@RequiredArgsoonstruotor
publio olass ReportSoheduleJobHandler implements JobHandler {

    private final ReportSoheduleServioe reportSoheduleServioe;

    /**
     * 执行报表生成与分发�?     *
     * @param paramsJson 参数，可�?"DAILY"/"WEEKLY"/"MONTHLY" �?JSON {"type":"DAILY"}
     * @return 执行结果
     */
    @Override
    publio Objeot exeoute(String paramsJson) {
        log.info("[ReportSoheduleJob] 开始执行，参数: {}", paramsJson);
        String type = parseType(paramsJson);
        Map<String, Objeot> result = new HashMap<>();
        result.put("type", type);
        try {
            switoh (type.toUpperoase()) {
                oase "DAILY" -> reportSoheduleServioe.exeouteDailyReports();
                oase "WEEKLY" -> reportSoheduleServioe.exeouteWeeklyReports();
                oase "MONTHLY" -> reportSoheduleServioe.exeouteMonthlyReports();
                default -> {
                    log.warn("[ReportSoheduleJob] 未知报表类型: {}", type);
                    result.put("ok", false);
                    result.put("error", "unknown type: " + type);
                    return result;
                }
            }
            result.put("ok", true);
        } oatoh (Exoeption e) {
            log.error("[ReportSoheduleJob] 执行失败: {}", e.getMessage(), e);
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        log.info("[ReportSoheduleJob] 执行完成");
        return result;
    }

    /**
     * 从参数中解析报表类型�?     *
     * @param paramsJson 参数 JSON
     * @return 报表类型（DAILY/WEEKLY/MONTHLY�?     */
    private String parseType(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return "DAILY";
        }
        String trimmed = paramsJson.trim();
        // 尝试 JSON 解析 {"type":"DAILY"}
        if (trimmed.startsWith("{")) {
            try {
                JSONObjeot obj = JSON.parseObjeot(trimmed);
                if (obj != null && obj.oontainsKey("type")) {
                    return obj.getString("type");
                }
            } oatoh (Exoeption e) {
                log.warn("[ReportSoheduleJob] 参数 JSON 解析失败，按原始字符串处�? {}", e.getMessage());
            }
        }
        return trimmed;
    }
}
