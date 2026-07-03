package com.njydsz.pmis.iam.job;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.iam.dto.PasswordScanResultDTO;
import com.njydsz.pmis.iam.service.PasswordScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 密码巡检 JobHandler（P3-3 运维安全增强）
 *
 * <p>由 ydsz-pmis-cronjob 通过 Feign 触发，或 XXL-JOB 直接调用。
 * 建议 cron：每日 03:00 触发；扫描结果写入审计日志并通知 PMO 邮箱。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("PasswordScanJobHandler")
@RequiredArgsConstructor
public class PasswordScanJobHandler implements JobHandler {

    private final PasswordScanService passwordScanService;

    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        try {
            int expireDays = 90;
            if (paramsJson != null && !paramsJson.isBlank()) {
                try {
                    expireDays = Integer.parseInt(paramsJson.trim());
                } catch (NumberFormatException ignore) {
                    // 忽略，使用默认
                }
            }
            PasswordScanResultDTO result = passwordScanService.scan(expireDays);
            long cost = System.currentTimeMillis() - start;
            log.info("[PasswordScan] cron 扫描完成 totalActive={} expired={} expiringSoon={} initial={} cost={}ms",
                    result.getTotalActive(), result.getExpiredCount(),
                    result.getExpiringSoonCount(), result.getInitialPasswordCount(), cost);

            // 风险提示：有过期账号时返回 ALERT，让调度器转发邮件
            if (result.getExpiredCount() > 0 || result.getInitialPasswordCount() > 0) {
                return String.format("{\"expired\":%d,\"expiringSoon\":%d,\"initial\":%d,\"status\":\"ALERT\"}",
                        result.getExpiredCount(), result.getExpiringSoonCount(),
                        result.getInitialPasswordCount());
            }
            return "OK";
        } catch (Exception e) {
            log.error("[PasswordScan] cron 扫描失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
