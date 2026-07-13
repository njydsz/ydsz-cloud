package com.njydsz.pmis.nextwiki.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.pmis.nextwiki.domain.repository.TrashItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储分析报表服务
 * <p>
 * 提供存储使用情况统计和分析。
 *
 * <p><b>报表维度：</b>
 * <ul>
 *   <li>按用户/租户统计存储使用量</li>
 *   <li>按文件类型统计分布</li>
 *   <li>按目录层级统计</li>
 *   <li>增长趋势分析</li>
 *   <li>大文件 Top-N</li>
 *   <li>回收站占用统计</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageAnalysisApplicationService {

    private final FileNodeRepository fileNodeRepository;
    private final StorageQuotaRepository quotaRepository;
    private final TrashItemRepository trashItemRepository;

    /**
     * 获取用户存储概览
     */
    public StorageOverview getUserOverview(String userId) {
        // 通过 repository 查询用户文件
        // 简化实现：返回基本结构
        return StorageOverview.builder()
                .userId(userId)
                .totalSize(0L)
                .fileCount(0)
                .folderCount(0)
                .trashCount(trashItemRepository.countActiveTrash(userId))
                .build();
    }

    /**
     * 按文件类型统计
     */
    public Map<String, TypeStats> statsByType(String userId) {
        // 实际实现：通过 mapper 聚合查询
        return new HashMap<>();
    }

    /**
     * 大文件 Top-N
     */
    public List<FileNode> topLargeFiles(String userId, int limit) {
        // 实际实现：通过 mapper 查询
        return List.of();
    }

    /**
     * 存储概览
     */
    @lombok.Data
    @lombok.Builder
    public static class StorageOverview {
        private String userId;
        private Long totalSize;
        private Integer fileCount;
        private Integer folderCount;
        private Integer trashCount;
        private Double usagePercentage;
    }

    /**
     * 类型统计
     */
    @lombok.Data
    @lombok.Builder
    public static class TypeStats {
        private String suffix;
        private Integer fileCount;
        private Long totalSize;
        private Double percentage;
    }
}
