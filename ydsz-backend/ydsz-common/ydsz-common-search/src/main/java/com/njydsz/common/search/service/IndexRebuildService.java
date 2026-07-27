package com.njydsz.common.search.service;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引重建服务
 *
 * @author ydsz-team
 * @since 1.3.0
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

    public int rebuildWithBlueGreen(String type, String tenantId) {
        if (rebuilding) return -1;
        rebuilding = true;
        progress = 0;
        total = 0;
        try {
            int count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;
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
