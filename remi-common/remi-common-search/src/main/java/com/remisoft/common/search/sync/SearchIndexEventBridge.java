package com.remisoft.common.search.sync;

import java.util.Optional;

import com.remisoft.common.search.core.IndexDocument;
import com.remisoft.common.search.core.IndexStrategy;
import com.remisoft.common.search.core.SearchEngineRegistry;
import com.remisoft.common.search.metrics.SearchMetrics;
import com.remisoft.common.search.provider.SearchProvider;
import com.remisoft.common.search.provider.SearchProviderRegistry;
import com.remisoft.common.search.service.IndexSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索索引事件桥接器
 * <p>
 * 业务模块通过此桥接器将数据变更同步到搜索索引，无需直接依赖 {@code IndexSyncService} 和 {@code SearchProvider}。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @RequiredArgsConstructor
 * public class ProjectInitiationServiceImpl {
 *     private final SearchIndexEventBridge searchIndexBridge;
 *
 *     public String save(ProjectInitiationDTO dto) {
 *         // ... 保存实体 ...
 *         searchIndexBridge.indexUpsert("project", entity);
 *         return entity.getId();
 *     }
 *
 *     public boolean removeById(String id) {
 *         // ... 删除实体 ...
 *         searchIndexBridge.indexDelete("project", id);
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class SearchIndexEventBridge {

    private final IndexSyncService indexSyncService;
    private final SearchProviderRegistry providerRegistry;
    private final SearchEngineRegistry engineRegistry;
    private final SearchMetrics metrics;

    /**
     * 异步索引实体（UPSERT）
     * <p>
     * 根据 type 查找对应的 {@link SearchProvider}，将实体转换为 {@link IndexDocument}，
     * 然后委托 {@link IndexSyncService} 异步写入搜索引擎。
     *
     * @param type   实体类型标识（如 "project"、"wiki"、"user"、"config"）
     * @param entity 业务实体对象
     * @param <T>    实体类型
     */
    public <T> void indexUpsert(String type, T entity) {
        if (entity == null) {
            return;
        }
        Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
        if (indexStrategy.isEmpty()) {
            log.debug("[SearchIndexBridge] 主引擎不支持索引操作，跳过: type={}", type);
            return;
        }
        SearchProvider<T> provider = providerRegistry.getProvider(type);
        if (provider == null) {
            log.debug("[SearchIndexBridge] 未找到 Provider: type={}", type);
            return;
        }
        indexSyncService.indexAsync(entity, provider);
    }

    /**
     * 异步删除索引
     *
     * @param type       实体类型标识
     * @param documentId 文档 ID
     */
    public void indexDelete(String type, String documentId) {
        if (documentId == null) {
            return;
        }
        Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
        if (indexStrategy.isEmpty()) {
            log.debug("[SearchIndexBridge] 主引擎不支持索引操作，跳过删除: type={}", type);
            return;
        }
        indexSyncService.deleteAsync(type, documentId);
    }

    /**
     * 手动同步单个实体到索引（同步调用，不走线程池）
     *
     * @param type   实体类型标识
     * @param entity 业务实体对象
     * @param <T>    实体类型
     */
    public <T> void indexSync(String type, T entity) {
        if (entity == null) {
            return;
        }
        Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
        if (indexStrategy.isEmpty()) {
            return;
        }
        SearchProvider<T> provider = providerRegistry.getProvider(type);
        if (provider == null) {
            return;
        }
        try {
            IndexDocument document = provider.toIndexDocument(entity);
            if (document != null) {
                indexStrategy.get().index(document);
                metrics.recordIndexOp(true);
            }
        } catch (Exception e) {
            metrics.recordIndexOp(false);
            log.error("[SearchIndexBridge] 同步索引失败: type={}", type, e);
        }
    }
}
