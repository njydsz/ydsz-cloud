package com.njydsz.pmis.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.njydsz.pmis.common.search.config.SearchProperties;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.IndexOperation;
import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.provider.SearchProvider;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引同步服务
 * <p>
 * 负责将业务实体的变更同步到搜索引擎索引。支持单文档索引、批量索引和全量重建。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class IndexSyncService {

    private final SearchEngine searchEngine;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final ExecutorService executorService;

    public IndexSyncService(SearchEngine searchEngine,
                            SearchProviderRegistry providerRegistry,
                            SearchProperties properties) {
        this.searchEngine = searchEngine;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, properties.getIndex().getThreadPoolSize()));
    }

    /**
     * 处理索引操作
     *
     * @param operation 索引操作
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleOperation(IndexOperation operation) {
        if (operation == null) {
            return;
        }

        switch (operation.getOperation()) {
            case UPSERT -> {
                if (operation.getDocument() != null) {
                    searchEngine.index(operation.getDocument());
                    log.debug("[IndexSync] 索引更新: type={}, id={}",
                            operation.getDocument().getType(), operation.getDocument().getId());
                }
            }
            case DELETE -> {
                if (operation.getType() != null && operation.getDocumentId() != null) {
                    searchEngine.deleteIndex(operation.getType(), operation.getDocumentId());
                    log.debug("[IndexSync] 索引删除: type={}, id={}",
                            operation.getType(), operation.getDocumentId());
                }
            }
            case BULK -> log.debug("[IndexSync] 批量操作");
        }
    }

    /**
     * 异步索引实体
     *
     * @param entity   业务实体
     * @param provider 搜索提供者
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void indexAsync(T entity, SearchProvider<T> provider) {
        executorService.submit(() -> {
            try {
                IndexDocument document = provider.toIndexDocument(entity);
                searchEngine.index(document);
                log.debug("[IndexSync] 异步索引完成: type={}, id={}",
                        document.getType(), document.getId());
            } catch (Exception e) {
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
                log.debug("[IndexSync] 异步删除索引: type={}, id={}", type, documentId);
            } catch (Exception e) {
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int rebuildAll(String type, String tenantId) {
        log.info("[IndexSync] 开始全量重建索引: type={}, tenantId={}", type, tenantId);
        AtomicInteger total = new AtomicInteger(0);

        List<SearchProvider<?>> providers = providerRegistry.getProviders(
                type != null ? List.of(type) : Collections.emptyList());

        for (SearchProvider<?> provider : providers) {
            try {
                rebuildProvider(provider, tenantId, total);
            } catch (Exception e) {
                log.error("[IndexSync] Provider {} 全量重建失败: {}",
                        provider.getType(), e.getMessage(), e);
            }
        }

        log.info("[IndexSync] 全量重建完成: total={}", total.get());
        return total.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        log.info("[IndexSync] 线程池已关闭");
    }
}
