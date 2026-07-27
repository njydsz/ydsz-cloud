package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexOperation;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.ProviderTypeBridge;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引同步服务
 * <p>
 * 负责将业务实体的变更同步到搜索引擎索引。支持单文档索引、批量索引和全量重建。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public class IndexSyncService {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final SearchMetrics metrics;
    private final ThreadPoolTaskExecutor executorService;

    private static final int MAX_DLQ_SIZE = 10000;
    private final ConcurrentLinkedQueue<IndexOperation> deadLetterQueue = new ConcurrentLinkedQueue<>();

    public IndexSyncService(SearchEngineRegistry engineRegistry,
                            SearchProviderRegistry providerRegistry,
                            SearchProperties properties,
                            SearchMetrics metrics) {
        this.engineRegistry = engineRegistry;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.metrics = metrics;

        this.executorService = new ThreadPoolTaskExecutor();
        this.executorService.setCorePoolSize(Math.max(2, properties.getIndex().getThreadPoolSize()));
        this.executorService.setMaxPoolSize(Math.max(4, properties.getIndex().getThreadPoolSize() * 2));
        this.executorService.setQueueCapacity(512);
        this.executorService.setThreadNamePrefix("index-sync-");
        this.executorService.setWaitForTasksToCompleteOnShutdown(true);
        this.executorService.setAwaitTerminationSeconds(5);
        this.executorService.initialize();
    }

    public void handleOperation(IndexOperation operation) {
        if (operation == null) return;
        Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
        if (indexStrategy.isEmpty()) {
            log.debug("[IndexSync] 主引擎不支持索引操作，跳过");
            return;
        }
        IndexStrategy idx = indexStrategy.get();

        switch (operation.getOperation()) {
            case UPSERT -> {
                if (operation.getDocument() != null) {
                    executeWithRetry(() -> {
                        idx.index(operation.getDocument());
                    }, operation);
                }
            }
            case DELETE -> {
                if (operation.getType() != null && operation.getDocumentId() != null) {
                    executeWithRetry(() -> {
                        idx.deleteIndex(operation.getType(), operation.getDocumentId());
                    }, operation);
                }
            }
            case BULK -> {
                if (operation.getDocuments() != null && !operation.getDocuments().isEmpty()) {
                    executeWithRetry(() -> {
                        idx.bulkIndex(operation.getDocuments());
                    }, operation);
                }
            }
        }
    }

    public <T> void indexAsync(T entity, SearchProvider<T> provider) {
        executorService.submit(() -> {
            try {
                IndexDocument document = provider.toIndexDocument(entity);
                Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
                if (idx.isPresent()) {
                    idx.get().index(document);
                    metrics.recordIndexOp(true);
                }
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步索引失败: type={}", provider.getType(), e);
            }
        });
    }

    public void deleteAsync(String type, String documentId) {
        executorService.submit(() -> {
            try {
                engineRegistry.getIndexStrategy().ifPresent(idx -> {
                    idx.deleteIndex(type, documentId);
                    metrics.recordIndexOp(true);
                });
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步删除失败: type={}, id={}", type, documentId, e);
            }
        });
    }

    public int rebuildAll(String type, String tenantId) {
        log.info("[IndexSync] 开始全量重建: type={}, tenantId={}", type, tenantId);
        AtomicInteger total = new AtomicInteger(0);
        List<SearchProvider<?>> providers = providerRegistry.getProviders(
                type != null ? List.of(type) : Collections.emptyList());
        for (SearchProvider<?> provider : providers) {
            try {
                rebuildProvider(ProviderTypeBridge.cast(provider), tenantId, total);
            } catch (Exception e) {
                log.error("[IndexSync] Provider {} 重建失败", provider.getType(), e);
            }
        }
        log.info("[IndexSync] 全量重建完成: total={}", total.get());
        return total.get();
    }

    public List<IndexOperation> getDeadLetterQueue() {
        return new ArrayList<>(deadLetterQueue);
    }

    public void retryDeadLetterQueue() {
        List<IndexOperation> snapshot = new ArrayList<>();
        IndexOperation op;
        while ((op = deadLetterQueue.poll()) != null) {
            snapshot.add(op);
        }
        if (snapshot.isEmpty()) return;
        log.info("[IndexSync] 重试死信队列: size={}", snapshot.size());
        for (IndexOperation operation : snapshot) {
            handleOperation(operation);
        }
    }

    public void shutdown() {
        executorService.shutdown();
        log.info("[IndexSync] 线程池已关闭");
    }

    // ==================== 私有方法 ====================

    private void executeWithRetry(Runnable action, IndexOperation operation) {
        int maxRetries = properties.getIndex().getMaxRetries();
        long retryInterval = properties.getIndex().getRetryIntervalMs();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                metrics.recordIndexOp(true);
                return;
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                if (attempt < maxRetries) {
                    log.warn("[IndexSync] 索引操作失败，重试: {}/{}", attempt + 1, maxRetries);
                    try {
                        Thread.sleep(retryInterval * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("[IndexSync] 重试耗尽，加入死信队列", e);
                    if (deadLetterQueue.size() < MAX_DLQ_SIZE) {
                        deadLetterQueue.add(operation);
                    }
                }
            }
        }
    }

    private <T> void rebuildProvider(SearchProvider<T> provider, String tenantId, AtomicInteger total) {
        List<String> ids = provider.getAllDocumentIds(tenantId);
        if (ids == null || ids.isEmpty()) return;

        int batchSize = properties.getIndex().getRebuildBatchSize();
        Optional<IndexStrategy> indexStrategyOpt = engineRegistry.getIndexStrategy();
        if (indexStrategyOpt.isEmpty()) {
            log.warn("[IndexSync] 引擎不支持索引操作，跳过重建");
            return;
        }
        IndexStrategy indexStrategy = indexStrategyOpt.get();

        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<String> batch = ids.subList(i, end);
            List<IndexDocument> documents = new ArrayList<>();
            for (String id : batch) {
                try {
                    T entity = provider.loadById(id);
                    if (entity != null) {
                        IndexDocument doc = provider.toIndexDocument(entity);
                        if (doc != null) documents.add(doc);
                    }
                } catch (Exception e) {
                    log.warn("[IndexSync] 加载实体失败: type={}, id={}", provider.getType(), id, e);
                }
            }
            if (!documents.isEmpty()) {
                indexStrategy.bulkIndex(documents);
                total.addAndGet(documents.size());
            }
        }
    }
}
