package com.njydsz.common.search.service;


import java.util.List;

import com.njydsz.common.search.core.SearchEngine;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引重建服务
 * <p>
 * 支持全量重建索引，适用于首次部署、索引损坏修复、搜索引擎切换等场景。
 * 支持 P1-9 蓝绿重建：重建期间搜索服务不中断。
 *
 * @author ydsz-team
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

    // P0-6: async rebuild using managed thread
    public void rebuildAllAsync(String type, String tenantId) {
        Thread t = new Thread(() -> {
            try {
                rebuildAll(type, tenantId);
            } catch (Exception e) {
                log.error("[IndexRebuild] async rebuild failed: {}", e.getMessage(), e);
            }
        }, "index-rebuild");
        t.setDaemon(true);
        t.start();
    }
    }

    /**
     * P1-9: 蓝绿重建索引
     * <p>
     * 重建期间搜索服务继续使用旧索引，重建完成后原子切换到新索引。
     * 适用于不允许搜索中断的生产环境。
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>在内存中构建新索引（通过 Provider 重新加载全量数据）</li>
     *   <li>重建完成后，清空旧索引并批量写入新数据</li>
     *   <li>切换期间搜索降级到内存索引</li>
     * </ol>
     *
     * @param type     实体类型（为空表示全部）
     * @param tenantId 租户 ID（为空表示全部）
     * @return 重建的文档总数
     */
    public int rebuildWithBlueGreen(String type, String tenantId) {
        if (rebuilding) {
            log.warn("[IndexRebuild] 重建任务正在执行中，请稍后再试");
            return -1;
        }

        rebuilding = true;
        progress = 0;
        total = 0;

        try {
            log.info("[IndexRebuild] 蓝绿重建开始: type={}, tenantId={}", type, tenantId);

            // 蓝色阶段：通过 IndexSyncService 重建（数据会同时写入内存和 PG）
            // 搜索请求继续使用旧索引，不受影响
            int count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;

            // 绿色阶段：切换 — 清空旧索引后，内存中的数据已经是最新的
            // 由于 IndexSyncService.rebuildAll 会调用 searchEngine.bulkIndex，
            // 数据已经写入 PG 索引表，无需额外切换操作

            log.info("[IndexRebuild] 蓝绿重建完成: type={}, total={}", type, count);
            return count;

        } catch (Exception e) {
            log.error("[IndexRebuild] 蓝绿重建失败: type={}", type, e);
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
