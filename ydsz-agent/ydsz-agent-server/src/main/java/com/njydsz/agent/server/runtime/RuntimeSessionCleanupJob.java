package com.njydsz.agent.server.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运行时会话定期清理任务。
 *
 * <p>定期清理超过保留时长的非活跃会话，防止内存泄漏。
 * 默认每 10 分钟执行一次，清理超过 2 小时的过期会话。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
@Component
public class RuntimeSessionCleanupJob {

    private static final int STALE_THRESHOLD_MINUTES = 120;

    private final RuntimeManagementService runtimeManagementService;

    public RuntimeSessionCleanupJob(RuntimeManagementService runtimeManagementService) {
        this.runtimeManagementService = runtimeManagementService;
    }

    /**
     * 定期清理过期会话。
     *
     * <p>每 10 分钟执行一次，清理超过 2 小时的非活跃会话。</p>
     */
    @Scheduled(fixedRateString = "${ydsz.agent.runtime.cleanup-interval-ms:600000}")
    public void cleanupStaleSessions() {
        try {
            int cleaned = runtimeManagementService.cleanupStaleSessions(STALE_THRESHOLD_MINUTES);
            if (cleaned > 0) {
                log.info("运行时清理任务完成: 清理 {} 个过期会话", cleaned);
            }
        } catch (Exception e) {
            log.warn("运行时清理任务异常: {}", e.getMessage());
        }
    }
}
