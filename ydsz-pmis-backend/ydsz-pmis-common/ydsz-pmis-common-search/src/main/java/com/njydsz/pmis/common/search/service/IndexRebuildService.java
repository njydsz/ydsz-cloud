package com.njydsz.pmis.common.search.service;


import java.util.List;

import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引重建服务
 * <p>
 * 支持全量重建索引，适用于首次部署、索引损坏修复、搜索引擎切换等场景。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class IndexRebuildService {

    private final IndexSyncService indexSyncService;
    private final SearchEngine searchEngine;
    private final SearchProviderRegistry providerRegistry;

    private volatile boolean rebuilding = false;
    private volatile int progress = 0;
    private volatile int total = 0;

    public IndexRebuildService(IndexSyncService indexSyncService,
                                SearchEngine searchEngine,
                                SearchProviderRegistry providerRegistry) {
        this.indexSyncService = indexSyncService;
        this.searchEngine = searchEngine;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 全量重建索引
     *
     * @param type     实体类型（为空表示全部）
     * @param tenantId 租户 ID（为空表示全部）
     * @return 重建的文档总数
     */
    public int rebuildAll(String type, String tenantId) {
        if (rebuilding) {
            log.warn("[IndexRebuild] 重建任务正在执行中，请稍后再试");
            return -1;
        }

        rebuilding = true;
        progress = 0;
        total = 0;

        try {
            // 先清空旧索引
            if (type == null || type.isBlank()) {
                searchEngine.deleteAllIndices(null);
            } else {
                searchEngine.deleteAllIndices(type);
            }

            // 执行全量重建
            int count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;

            log.info("[IndexRebuild] 全量重建完成: type={}, tenantId={}, total={}",
                    type, tenantId, count);
            return count;

        } catch (Exception e) {
            log.error("[IndexRebuild] 全量重建失败: type={}", type, e);
            return -1;
        } finally {
            rebuilding = false;
        }
    }

    /**
     * 检查是否正在重建
     */
    public boolean isRebuilding() {
        return rebuilding;
    }

    /**
     * 获取重建进度
     */
    public int getProgress() {
        return progress;
    }

    /**
     * 获取重建总数
     */
    public int getTotal() {
        return total;
    }

    /**
     * 获取已注册的实体类型列表
     */
    public List<String> getRegisteredTypes() {
        return providerRegistry.getAllTypes();
    }
}
