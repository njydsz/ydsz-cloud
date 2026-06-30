package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.OpsTicketService;
import com.njydsz.pmis.execution.service.WarrantyService;
import com.njydsz.pmis.scheduler.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 售后巡检 Job（P7-3 自动巡检）
 *
 * <p>每日凌晨 03:00 触发，扫描：
 * <ol>
 *   <li>即将到期质保期（提前 30 天提醒）→ 标记 EXPIRING_SOON</li>
 *   <li>已过期质保期 → 标记 EXPIRED</li>
 *   <li>运维工单 SLA 违约（响应/解决超时）→ 触发红色预警</li>
 * </ol>
 *
 * <p>Job 配置示例：
 * <pre>
 *   job_key:   afterSalesScanJob
 *   handler:   afterSalesScanJobHandler
 *   cron:      0 0 3 * * ?
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("afterSalesScanJobHandler")
@RequiredArgsConstructor
public class AfterSalesScanJobHandler implements JobHandler {

    private final WarrantyService warrantyService;
    private final OpsTicketService opsTicketService;

    private static final int DEFAULT_NOTICE_DAYS = 30;

    @Override
    public Object execute(String paramsJson) {
        long start = System.currentTimeMillis();
        int noticeDays = parseNoticeDays(paramsJson);
        LocalDate today = LocalDate.now();

        int expiring = 0;
        int expired = 0;
        int slaBreaches = 0;

        try {
            expiring = warrantyService.scanExpiring(today, noticeDays);
        } catch (Exception e) {
            log.warn("[AfterSalesScanJob] 扫描即将到期质保期失败: {}", e.getMessage());
        }
        try {
            expired = warrantyService.scanOverdue(today);
        } catch (Exception e) {
            log.warn("[AfterSalesScanJob] 扫描已过期质保期失败: {}", e.getMessage());
        }
        try {
            slaBreaches = opsTicketService.scanSlaBreaches(today);
        } catch (Exception e) {
            log.warn("[AfterSalesScanJob] 扫描 SLA 违约工单失败: {}", e.getMessage());
        }

        long cost = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("today", today.toString());
        result.put("noticeDays", noticeDays);
        result.put("expiringCount", expiring);
        result.put("expiredCount", expired);
        result.put("slaBreachCount", slaBreaches);
        result.put("costMs", cost);
        log.info("[AfterSalesScanJob] 巡检完成: today={} expiring={} expired={} slaBreaches={} costMs={}",
                today, expiring, expired, slaBreaches, cost);
        return result;
    }

    private int parseNoticeDays(String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return DEFAULT_NOTICE_DAYS;
        }
        try {
            String s = paramsJson.replaceAll("[{}\" ]", "");
            for (String kv : s.split(",")) {
                String[] pair = kv.split("[:=]");
                if (pair.length == 2 && "noticeDays".equalsIgnoreCase(pair[0])) {
                    return Integer.parseInt(pair[1]);
                }
            }
        } catch (Exception e) {
            log.debug("[AfterSalesScanJob] 参数解析失败, 使用默认 noticeDays={}", DEFAULT_NOTICE_DAYS);
        }
        return DEFAULT_NOTICE_DAYS;
    }
}
