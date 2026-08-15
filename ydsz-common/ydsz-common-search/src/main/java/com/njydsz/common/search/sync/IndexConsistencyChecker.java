package com.njydsz.common.search.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.provider.ProviderTypeBridge;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 索引一致性巡检器
 * <p>
 * 定时对比数据库文档数与索引文档数，检测索引丢失或冗余。
 * 巡检结果通过日志输出，严重不一致时触发告警。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class IndexConsistencyChecker {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProviderRegistry providerRegistry;

    /**
     * 执行一致性巡检
     *
     * @param tenantId 租户 ID（可为 null，表示全部租户）
     * @return 巡检结果
     */
    public ConsistencyReport check(String tenantId) {
        Map<String, Long> dbCounts = new HashMap<>();
        Map<String, Long> indexCounts = new HashMap<>();
        Map<String, Long> missingFromIndex = new HashMap<>();
        Map<String, Long> orphanInIndex = new HashMap<>();

        List<SearchProvider<?>> providers = providerRegistry.getAllProviders();
        if (providers.isEmpty()) {
            log.debug("[IndexConsistency] 无已注册 Provider，跳过巡检");
            return new ConsistencyReport(dbCounts, indexCounts, missingFromIndex, orphanInIndex);
        }

        IndexStrategy indexStrategy = engineRegistry.getIndexStrategy().orElse(null);
        if (indexStrategy == null) {
            log.debug("[IndexConsistency] 主引擎不支持索引操作，跳过巡检");
            return new ConsistencyReport(dbCounts, indexCounts, missingFromIndex, orphanInIndex);
        }

        for (SearchProvider<?> provider : providers) {
            String type = provider.getType();
            try {
                long dbCount = provider.getAllDocumentIds(tenantId).size();
                long indexCount = indexStrategy.count(type);

                dbCounts.put(type, dbCount);
                indexCounts.put(type, indexCount);

                if (dbCount > indexCount) {
                    missingFromIndex.put(type, dbCount - indexCount);
                    log.warn("[IndexConsistency] 索引丢失: type={}, db={}, index={}, missing={}",
                            type, dbCount, indexCount, dbCount - indexCount);
                } else if (indexCount > dbCount) {
                    orphanInIndex.put(type, indexCount - dbCount);
                    log.warn("[IndexConsistency] 索引冗余: type={}, db={}, index={}, orphan={}",
                            type, dbCount, indexCount, indexCount - dbCount);
                }
            } catch (Exception e) {
                log.error("[IndexConsistency] 巡检失败: type={}", type, e);
            }
        }

        if (!missingFromIndex.isEmpty() || !orphanInIndex.isEmpty()) {
            log.warn("[IndexConsistency] 巡检完成，发现不一致: missing={}, orphan={}",
                    missingFromIndex, orphanInIndex);
        } else {
            log.info("[IndexConsistency] 巡检完成，索引一致: types={}", dbCounts.keySet());
        }

        return new ConsistencyReport(dbCounts, indexCounts, missingFromIndex, orphanInIndex);
    }

    /**
     * 自动修复索引不一致（丢失文档重新索引，冗余文档删除）
     *
     * @param tenantId 租户 ID
     * @return 修复的文档总数
     */
    public int autoRepair(String tenantId) {
        ConsistencyReport report = check(tenantId);
        int repaired = 0;

        IndexStrategy indexStrategy = engineRegistry.getIndexStrategy().orElse(null);
        if (indexStrategy == null) return 0;

        for (Map.Entry<String, Long> entry : report.missingFromIndex().entrySet()) {
            String type = entry.getKey();
            log.info("[IndexConsistency] 自动修复丢失索引: type={}, missing={}", type, entry.getValue());
            SearchProvider<?> rawProvider = providerRegistry.getProvider(type);
            if (rawProvider != null) {
                try {
                    SearchProvider<Object> provider = ProviderTypeBridge.cast(rawProvider);
                    List<String> dbIds = provider.getAllDocumentIds(tenantId);
                    Set<String> indexedIds = new HashSet<>(indexStrategy.getAllDocumentIds(type));
                    for (String id : dbIds) {
                        if (!indexedIds.contains(id)) {
                            Object entity = provider.loadById(id);
                            if (entity != null) {
                                indexStrategy.index(provider.toIndexDocument(entity));
                                repaired++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[IndexConsistency] 修复丢失索引失败: type={}", type, e);
                }
            }
        }

        for (Map.Entry<String, Long> entry : report.orphanInIndex().entrySet()) {
            String type = entry.getKey();
            log.info("[IndexConsistency] 清理冗余索引: type={}, orphan={}", type, entry.getValue());
            try {
                SearchProvider<?> provider = providerRegistry.getProvider(type);
                if (provider != null) {
                    Set<String> dbIdSet = new HashSet<>(provider.getAllDocumentIds(tenantId));
                    Set<String> indexedIds = new HashSet<>(indexStrategy.getAllDocumentIds(type));
                    for (String indexedId : indexedIds) {
                        if (!dbIdSet.contains(indexedId)) {
                            indexStrategy.deleteIndex(type, indexedId);
                            repaired++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[IndexConsistency] 清理冗余索引失败: type={}", type, e);
            }
        }

        log.info("[IndexConsistency] 自动修复完成: repaired={}", repaired);
        return repaired;
    }

    /**
     * 一致性巡检报告
     *
     * @param dbCounts        各类型数据库文档数
     * @param indexCounts     各类型索引文档数
     * @param missingFromIndex 索引中缺失的文档数（按类型）
     * @param orphanInIndex   索引中冗余的文档数（按类型）
     */
    public record ConsistencyReport(
            Map<String, Long> dbCounts,
            Map<String, Long> indexCounts,
            Map<String, Long> missingFromIndex,
            Map<String, Long> orphanInIndex
    ) {
        /**
         * 判断索引与数据库是否一致。
         *
         * @return {@code true} 表示索引无缺失文档且无冗余文档，两侧完全对齐
         */
        public boolean isConsistent() {
            return missingFromIndex.isEmpty() && orphanInIndex.isEmpty();
        }
    }
}
