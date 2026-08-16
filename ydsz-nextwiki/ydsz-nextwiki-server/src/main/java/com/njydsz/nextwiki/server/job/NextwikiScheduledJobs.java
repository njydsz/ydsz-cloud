package com.njydsz.nextwiki.server.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.service.TrashDomainService;

/**
 * NextWiki 定时任务
 * <p>
 * 自动清理回收站过期条目、搜索索引重建。
 * 使用分布式锁确保多实例部署时同一任务不会被并发执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextwikiScheduledJobs {

    private final TrashDomainService trashDomainService;
    private final SearchDomainService searchDomainService;

    /**
     * 每天凌晨 2 点清理过期回收站条目
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @DistributedScheduled(lockKey = "nextwiki:cleanup-trash")
    public void cleanupExpiredTrash() {
        log.info("[NextwikiScheduledJobs] 开始清理过期回收站条目");
        int cleaned = trashDomainService.cleanupExpiredItems();
        log.info("[NextwikiScheduledJobs] 清理完成: count={}", cleaned);
    }

    /**
     * 每周日凌晨 3 点重建搜索索引
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @DistributedScheduled(lockKey = "nextwiki:rebuild-index")
    public void rebuildSearchIndex() {
        log.info("[NextwikiScheduledJobs] 开始重建搜索索引");
        searchDomainService.rebuildAllIndices();
        log.info("[NextwikiScheduledJobs] 搜索索引重建完成");
    }
}
