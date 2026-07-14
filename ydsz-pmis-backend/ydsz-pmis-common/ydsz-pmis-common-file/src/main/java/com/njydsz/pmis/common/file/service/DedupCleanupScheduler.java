package com.njydsz.pmis.common.file.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 去重记录清理调度器
 *
 * <p>定时清理过期的文件去重（秒传）MD5 映射记录。
 * 由 {@link com.njydsz.pmis.common.file.config.FileConfiguration} 通过 @Bean 注册，
 * 仅当 {@link FileDedupService} 存在时生效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class DedupCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DedupCleanupScheduler.class);

    private final FileDedupService fileDedupService;

    public DedupCleanupScheduler(FileDedupService fileDedupService) {
        this.fileDedupService = fileDedupService;
    }

    /**
     * 定时清理过期的去重映射记录（每天凌晨3点执行）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredDedupEntries() {
        log.debug("开始清理过期的文件去重映射记录");
        try {
            fileDedupService.cleanupExpiredEntries();
            log.debug("文件去重映射记录清理完成");
        } catch (Exception e) {
            log.error("文件去重映射记录清理失败: {}", e.getMessage(), e);
        }
    }
}
