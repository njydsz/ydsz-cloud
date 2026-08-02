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
     * 获取用户存储概览（文件数/文件夹数/总大小/回收站占用）。
     * <p>folderCount 由 {@code countFoldersByUser} 真实统计（早期版本曾硬编码为 0，P1-7 修复）。
     *
     * @param userId 用户 ID
     * @return 存储概览 {@link StorageOverview}
     * @complexity O(1)（4 次聚合查询）
     * @note 只读，无事务边界；usagePercentage 由调用方结合配额计算后回填
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
     * 按文件后缀（类型）统计存储分布（含占比）。
     * <p>占比 = 该类型总大小 / 用户全部文件总大小 × 100，四舍五入保留两位小数；无文件时占比为 0。
     *
     * @param userId 用户 ID
     * @return 后缀 → 类型统计 {@link TypeStats} 的映射（unknown 表示无后缀文件）
     * @complexity O(statRows)（一次分组聚合 + 一次遍历计算占比）
     * @note 只读，无事务边界
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
     * 查询用户占用空间最大的前 N 个文件（定位存储大户）。
     *
     * @param userId 用户 ID
     * @param limit  返回条数上限（Top-N）
     * @return 文件节点列表 {@link FileNode}（按大小降序，最多 limit 条）
     * @complexity O(query)（一次按大小分页排序查询）
     * @note 只读，无事务边界
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
        /** 用户 ID */
        private String userId;
        /** 已用存储总大小（字节） */
        private Long totalSize;
        /** 文件数（不含文件夹） */
        private Integer fileCount;
        /** 文件夹数 */
        private Integer folderCount;
        /** 回收站中文件数 */
        private Integer trashCount;
        /** 配额使用率（0~100，由调用方结合配额上限回填，可能为 null） */
        private Double usagePercentage;
    }

    /**
     * 单类型存储统计（按后缀聚合）。
     */
    @Data
    @Builder
    public static class TypeStats {
        /** 文件后缀（小写；无后缀为 "unknown"） */
        private String suffix;
        /** 该类型文件数 */
        private Integer fileCount;
        /** 该类型文件总大小（字节） */
        private Long totalSize;
        /** 占用户总存储的比例（%，四舍五入保留两位） */
        private Double percentage;
    }
}
