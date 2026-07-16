package com.njydsz.common.queue.dedup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 去重 Key 定时清理调度器
 *
 * <p>定期清理内存去重器中的过期记录，避免内存泄漏。
 * Redis 去重器已使用 TTL 自动过期，无需额外清理。
 *
 * <p><b>清理策略：</b>
 * <ul>
 *   <li>每 10 分钟执行一次清理</li>
 *   <li>清理 {@link MessageDeduplicator} 中超时的去重记录</li>
 *   <li>记录清理前后的记录数量变化</li>
 * </ul>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>仅对内存去重器有效，Redis 去重器依赖 TTL 自动过期</li>
 *   <li>清理间隔可通过 spring.task.scheduling 配置调整</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DedupCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DedupCleanupScheduler.class);

    private final MessageDeduplicator memoryDeduplicator;

    public DedupCleanupScheduler(MessageDeduplicator memoryDeduplicator) {
        this.memoryDeduplicator = memoryDeduplicator;
    }

    /**
     * 定时清理过期去重 Key
     * <p>每 10 分钟执行一次，清理内存中超时的去重记录
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void cleanupExpiredKeys() {
        if (memoryDeduplicator == null) {
            return;
        }
        try {
            int beforeCount = memoryDeduplicator.getRecordCount();
            if (beforeCount == 0) {
                return;
            }
            memoryDeduplicator.cleanupExpired();
            int afterCount = memoryDeduplicator.getRecordCount();
            int cleaned = beforeCount - afterCount;
            if (cleaned > 0) {
                log.debug("[DedupCleanupScheduler] 已清理 {} 条过期去重记录（{} -> {}）",
                        cleaned, beforeCount, afterCount);
            }
        } catch (Exception e) {
            log.warn("[DedupCleanupScheduler] 清理过期去重记录失败: {}", e.getMessage(), e);
        }
    }
}
