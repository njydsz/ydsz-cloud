package com.njydsz.nextwiki.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;

import lombok.Builder;
import lombok.Data;
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
 * @author ydsz-team
 * @since 1.0.0
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
        int fileCount = fileNodeRepository.countByUser(userId);
        long totalSize = fileNodeRepository.sumSizeByUser(userId);
        int trashCount = trashItemRepository.countActiveTrash(userId);
        // P1-7 修复：folderCount 不再硬编码为 0
        int folderCount = fileNodeRepository.countFoldersByUser(userId);

        return StorageOverview.builder()
                .userId(userId)
                .totalSize(totalSize)
                .fileCount(fileCount)
                .folderCount(folderCount)
                .trashCount(trashCount)
                .build();
    }

    /**
     * 按文件类型统计
     */
    public Map<String, TypeStats> statsByType(String userId) {
        List<FileNodeRepository.FileTypeStat> stats = fileNodeRepository.statsBySuffixAndUser(userId);
        Map<String, TypeStats> result = new HashMap<>();

        long grandTotal = stats.stream()
                .mapToLong(FileNodeRepository.FileTypeStat::totalSize)
                .sum();

        for (FileNodeRepository.FileTypeStat stat : stats) {
            double percentage = grandTotal > 0
                    ? (double) stat.totalSize() / grandTotal * 100
                    : 0.0;
            String key = stat.suffix() != null ? stat.suffix() : "unknown";
            result.put(key, TypeStats.builder()
                    .suffix(key)
                    .fileCount(stat.fileCount())
                    .totalSize(stat.totalSize())
                    .percentage(Math.round(percentage * 100.0) / 100.0)
                    .build());
        }

        return result;
    }

    /**
     * 大文件 Top-N
     */
    public List<FileNode> topLargeFiles(String userId, int limit) {
        return fileNodeRepository.findTopLargeFilesByUser(userId, limit);
    }

    /**
     * 存储概览
     */
    @Data
    @Builder
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
    @Data
    @Builder
    public static class TypeStats {
        private String suffix;
        private Integer fileCount;
        private Long totalSize;
        private Double percentage;
    }
}
