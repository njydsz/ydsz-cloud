package com.njydsz.common.search.service;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引重建服务接口。
 * <p>全量/增量重建 ES 索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
public class IndexRebuildService {

    private final IndexSyncService indexSyncService;
    private final SearchEngineRegistry engineRegistry;
    private final SearchProviderRegistry providerRegistry;

    private volatile boolean rebuilding = false;
    private volatile int progress = 0;
    private volatile int total = 0;

    private final ThreadPoolTaskExecutor rebuildExecutor;

    public IndexRebuildService(IndexSyncService indexSyncService,
                                SearchEngineRegistry engineRegistry,
                                SearchProviderRegistry providerRegistry) {
        this.indexSyncService = indexSyncService;
        this.engineRegistry = engineRegistry;
        this.providerRegistry = providerRegistry;

        this.rebuildExecutor = new ThreadPoolTaskExecutor();
        this.rebuildExecutor.setCorePoolSize(1);
        this.rebuildExecutor.setMaxPoolSize(1);
        this.rebuildExecutor.setQueueCapacity(1);
        this.rebuildExecutor.setThreadNamePrefix("index-rebuild-");
        this.rebuildExecutor.setDaemon(true);
        this.rebuildExecutor.setWaitForTasksToCompleteOnShutdown(false);
        this.rebuildExecutor.initialize();
    }

    public int rebuildAll(String type, String tenantId) {
        if (rebuilding) {
            log.warn("[IndexRebuild] 重建任务正在执行中");
            return -1;
        }
        rebuilding = true;
        progress = 0;
        total = 0;
        try {
            Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
            if (idx.isEmpty()) {
                log.warn("[IndexRebuild] 主引擎不支持索引操作，无法重建");
                return -1;
            }
            if (type == null || type.isBlank()) {
                idx.get().deleteAllIndices(null);
            } else {
                idx.get().deleteAllIndices(type);
            }
            int count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;
            log.info("[IndexRebuild] 全量重建完成: type={}, total={}", type, count);
            return count;
        } catch (Exception e) {
            log.error("[IndexRebuild] 全量重建失败", e);
            return -1;
        } finally {
            rebuilding = false;
        }
    }

    public void rebuildAllAsync(String type, String tenantId) {
        rebuildExecutor.submit(() -> {
            try {
                rebuildAll(type, tenantId);
            } catch (Exception e) {
                log.error("[IndexRebuild] async rebuild failed", e);
            }
        });
    }

    /**
     * 蓝绿索引重建
     * <p>
     * 先将新数据索引到"绿色"索引（不影响当前搜索），
     * 索引完成后原子切换到新索引，删除旧索引。
     * <p>
     * 当前实现：先全量重建新索引数据，然后删除旧索引。
     * 真正的双表蓝绿切换需要引擎层支持（PG 可用 shadow 表，ES 可用 alias）。
     *
     * @param type     实体类型
     * @param tenantId 租户 ID
     * @return 索引文档数，失败返回 -1
     */
    public int rebuildWithBlueGreen(String type, String tenantId) {
        if (rebuilding) return -1;
        rebuilding = true;
        progress = 0;
        total = 0;
        try {
            Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
            if (idx.isEmpty()) {
                log.warn("[IndexRebuild] 主引擎不支持索引操作，蓝绿重建跳过");
                return -1;
            }

            log.info("[IndexRebuild] 蓝绿重建开始: type={}, tenantId={}", type, tenantId);

            // Step 1: 全量索引新数据（UPSERT 模式，不删除旧数据）
            int count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;

            // Step 2: 清理旧索引中不在 DB 中的冗余文档
            if (type != null && !type.isBlank()) {
                try {
                    idx.get().deleteAllIndices(type);
                    // 重新索引（因为 deleteAllIndices 会清空所有数据）
                    if (count > 0) {
                        count = indexSyncService.rebuildAll(type, tenantId);
                        total = count;
                        progress = count;
                    }
                } catch (Exception e) {
                    log.warn("[IndexRebuild] 蓝绿重建清理旧索引失败，新数据已就位", e);
                }
            }

            log.info("[IndexRebuild] 蓝绿重建完成: type={}, total={}", type, count);
            return count;
        } catch (Exception e) {
            log.error("[IndexRebuild] 蓝绿重建失败", e);
            return -1;
        } finally {
            rebuilding = false;
        }
    }

    public boolean isRebuilding() { return rebuilding; }
    public int getProgress() { return progress; }
    public int getTotal() { return total; }

    public int getProgressPercent() {
        if (!rebuilding || total == 0) return rebuilding ? 0 : -1;
        return Math.min(100, (progress * 100) / total);
    }

    public List<String> getRegisteredTypes() {
        return providerRegistry.getAllTypes();
    }

    public void shutdown() {
        rebuildExecutor.shutdown();
        log.info("[IndexRebuild] 重建线程池已关闭");
    }
}
