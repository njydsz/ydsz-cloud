package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexOperation;
import com.njydsz.common.search.core.SearchEngine;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.ProviderTypeBridge;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引同步服务
 * <p>
 * 负责将业务实体的变更同步到搜索引擎索引。支持单文档索引、批量索引和全量重建。
 * 支持索引同步重试和死信队列。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class IndexSyncService {

    private final SearchEngine searchEngine;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final SearchMetrics metrics;
    private final ExecutorService executorService;

    /** P2-15: 死信队列 — 存储重试失败的索引操作 */
    // P1-6: bounded dead letter queue (max 10000 entries)
    private static final int MAX_DLQ_SIZE = 10000;
    // P2-10: 使用 ConcurrentLinkedQueue 替代 synchronizedList 修复 check-then-act 竞态
    private final ConcurrentLinkedQueue<IndexOperation> deadLetterQueue = new ConcurrentLinkedQueue<>();

    public IndexSyncService(SearchEngine searchEngine,
                            SearchProviderRegistry providerRegistry,
                            SearchProperties properties,
                            SearchMetrics metrics) {
        this.searchEngine = searchEngine;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.metrics = metrics;
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, properties.getIndex().getThreadPoolSize()));
    }

    /**
     * 处理索引操作
     *
     * @param operation 索引操作
     */
    public void handleOperation(IndexOperation operation) {
        if (operation == null) {
            return;
        }

        switch (operation.getOperation()) {
            case UPSERT -> {
                if (operation.getDocument() != null) {
                    executeWithRetry(() -> {
                        searchEngine.index(operation.getDocument());
                        log.debug("[IndexSync] 索引更新: type={}, id={}",
                                operation.getDocument().getType(), operation.getDocument().getId());
                    }, operation);
                }
            }
            case DELETE -> {
                if (operation.getType() != null && operation.getDocumentId() != null) {
                    executeWithRetry(() -> {
                        searchEngine.deleteIndex(operation.getType(), operation.getDocumentId());
                        log.debug("[IndexSync] 索引删除: type={}, id={}",
                                operation.getType(), operation.getDocumentId());
                    }, operation);
                }
            }
            // P0-5: BULK 操作实际处理
            case BULK -> {
                if (operation.getDocuments() != null && !operation.getDocuments().isEmpty()) {
                    executeWithRetry(() -> {
                        searchEngine.bulkIndex(operation.getDocuments());
                        log.debug("[IndexSync] 批量索引: count={}", operation.getDocuments().size());
                    }, operation);
                }
            }
        }
    }

    /**
     * 异步索引实体
     *
     * @param entity   业务实体
     * @param provider 搜索提供者
     */
    public <T> void indexAsync(T entity, SearchProvider<T> provider) {
        executorService.submit(() -> {
            try {
                IndexDocument document = provider.toIndexDocument(entity);
                searchEngine.index(document);
                metrics.recordIndexOp(true);
                log.debug("[IndexSync] 异步索引完成: type={}, id={}",
                        document.getType(), document.getId());
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步索引失败: type={}, error={}",
                        provider.getType(), e.getMessage(), e);
            }
        });
    }

    /**
     * 异步删除索引
     *
     * @param type       实体类型
     * @param documentId 文档 ID
     */
    public void deleteAsync(String type, String documentId) {
        executorService.submit(() -> {
            try {
                searchEngine.deleteIndex(type, documentId);
                metrics.recordIndexOp(true);
                log.debug("[IndexSync] 异步删除索引: type={}, id={}", type, documentId);
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步删除索引失败: type={}, id={}, error={}",
                        type, documentId, e.getMessage(), e);
            }
        });
    }

    /**
     * 全量重建索引
     * <p>
     * 遍历所有已注册 Provider 的全量数据，分批写入索引。
     *
     * @param type    实体类型（为空表示重建全部）
     * @param tenantId 租户 ID（为空表示全部）
     * @return 重建的文档总数
     */
    public int rebuildAll(String type, String tenantId) {
        log.info("[IndexSync] 开始全量重建索引: type={}, tenantId={}", type, tenantId);
        AtomicInteger total = new AtomicInteger(0);

        List<SearchProvider<?>> providers = providerRegistry.getProviders(
                type != null ? List.of(type) : Collections.emptyList());

        for (SearchProvider<?> provider : providers) {
            try {
                rebuildProvider(ProviderTypeBridge.cast(provider), tenantId, total);
            } catch (Exception e) {
                log.error("[IndexSync] Provider {} 全量重建失败: {}",
                        provider.getType(), e.getMessage(), e);
            }
        }

        log.info("[IndexSync] 全量重建完成: total={}", total.get());
        return total.get();
    }

    /**
     * P2-15: 获取死信队列中的操作
     */
    public List<IndexOperation> getDeadLetterQueue() {
        return new ArrayList<>(deadLetterQueue);
    }

    /**
     * P2-15: 重试死信队列中的操作
     */
    public void retryDeadLetterQueue() {
        // P2-10: 使用 poll() 原子操作替代 snapshot + clear，避免竞态条件
        List<IndexOperation> snapshot = new ArrayList<>();
        IndexOperation op;
        while ((op = deadLetterQueue.poll()) != null) {
            snapshot.add(op);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        log.info("[IndexSync] 重试死信队列: size={}", snapshot.size());
        for (IndexOperation operation : snapshot) {
            handleOperation(operation);
        }
    }

    /**
     * 关闭线程池（Spring 生命周期回调）
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[IndexSync] 线程池已关闭");
    }

    // ==================== 私有方法 ====================

    /**
     * P2-15: 带重试的索引操作执行
     */
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
                    log.warn("[IndexSync] 索引操作失败，准备重试: attempt={}/{}, error={}",
                            attempt + 1, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryInterval * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("[IndexSync] 索引操作重试耗尽，加入死信队列: operation={}",
                            operation.getOperation(), e);
                    if (deadLetterQueue.size() < MAX_DLQ_SIZE) {
                        deadLetterQueue.add(operation);
                    } else {
                        log.error("[IndexSync] Dead letter queue full ({}), dropping operation: {}", MAX_DLQ_SIZE, operation.getOperation());
                    }
                }
            }
        }
    }

    private <T> void rebuildProvider(SearchProvider<T> provider, String tenantId, AtomicInteger total) {
        List<String> ids = provider.getAllDocumentIds(tenantId);
        if (ids == null || ids.isEmpty()) {
            log.info("[IndexSync] Provider {} 无数据需要重建", provider.getType());
            return;
        }

        int batchSize = properties.getIndex().getRebuildBatchSize();
        log.info("[IndexSync] Provider {} 开始重建: count={}, batchSize={}",
                provider.getType(), ids.size(), batchSize);

        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<String> batch = ids.subList(i, end);

            List<IndexDocument> documents = new ArrayList<>();
            for (String id : batch) {
                try {
                    T entity = provider.loadById(id);
                    if (entity != null) {
                        IndexDocument doc = provider.toIndexDocument(entity);
                        if (doc != null) {
                            documents.add(doc);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[IndexSync] 加载实体失败: type={}, id={}, error={}",
                            provider.getType(), id, e.getMessage());
                }
            }

            if (!documents.isEmpty()) {
                searchEngine.bulkIndex(documents);
                total.addAndGet(documents.size());
            }

            log.debug("[IndexSync] Provider {} 重建进度: {}/{}",
                    provider.getType(), end, ids.size());
        }
    }
}
