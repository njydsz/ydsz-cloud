package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.DailyReconcileService;
import com.njydsz.pmis.common.job.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 每日对账 Job（P6-1 每日自动对账）
 *
 * <p>每日凌晨 02:00 触发，校验成本/收入/开票/回款/工时成本/利润快照等维度双向一致性。
 *
 * <p>Job 配置示例：
 * <pre>
 *   job_key:   dailyReconcileJob
 *   handler:   dailyReconcileJobHandler
 *   cron:      0 0 2 * * ?
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("dailyReconcileJobHandler")
@RequiredArgsConstructor
public class DailyReconcileJobHandler implements JobHandler {

    private final DailyReconcileService dailyReconcileService;

    @Override
    public Object execute(String paramsJson) {
        long start = System.currentTimeMillis();
        // 默认对前一日数据
        LocalDate target = parseDate(paramsJson, LocalDate.now().minusDays(1));
        log.info("[DailyReconcileJob] 开始执行对账: date={}", target);
        try {
            int n = dailyReconcileService.runDaily(target);
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> result = new HashMap<>();
            result.put("reconcileDate", target.toString());
            result.put("recordCount", n);
            result.put("costMs", cost);
            log.info("[DailyReconcileJob] 对账完成: date={} records={} costMs={}", target, n, cost);
            return result;
        } catch (Exception e) {
            log.error("[DailyReconcileJob] 对账失败: date={} err={}", target, e.getMessage(), e);
            throw new RuntimeException("DailyReconcile failed for " + target + ": " + e.getMessage(), e);
        }
    }

    private LocalDate parseDate(String paramsJson, LocalDate dflt) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return dflt;
        }
        try {
            String s = paramsJson.replaceAll("[{}\" ]", "");
            for (String kv : s.split(",")) {
                String[] pair = kv.split("[:=]");
                if (pair.length == 2 && ("date".equalsIgnoreCase(pair[0])
                        || "reconcileDate".equalsIgnoreCase(pair[0]))) {
                    return LocalDate.parse(pair[1]);
                }
            }
        } catch (Exception e) {
            log.debug("[DailyReconcileJob] 参数解析失败, 使用默认 date={}", dflt);
        }
        return dflt;
    }
}
