package com.njydsz.pmis.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.repository.TagRepository;
import com.njydsz.pmis.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索领域服务
 * <p>
 * 提供基于数据库的文件名、路径、标签搜索（多维度组合）。
 * 当 Elasticsearch 可用时，由 {@code WikiSearchProvider} 覆盖为全文搜索。
 *
 * <p><b>搜索能力分级：</b>
 * <ul>
 *   <li>P0 - 基于文件名/路径的 LIKE 搜索（数据库）</li>
 *   <li>P1 - 多维度搜索：文件名 + 路径 + 标签</li>
 *   <li>P2 - 相关度排序、高亮、搜索建议</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDomainService {

    private final FileNodeRepository fileNodeRepository;
    private final TagRepository tagRepository;

    /**
     * 综合搜索
     *
     * @param keyword  搜索关键词
     * @param userId   用户ID（权限过滤）
     * @param scope    搜索范围：all / filename / content / tag
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 搜索结果
     */
    public SearchResultVO search(String keyword, String userId, String scope,
                                  int page, int pageSize) {
        long startTime = System.currentTimeMillis();

        log.info("[SearchDomainService] 搜索: keyword={}, userId={}, scope={}, page={}, pageSize={}",
                keyword, userId, scope, page, pageSize);

        List<FileNode> allResults = new ArrayList<>();

        boolean searchFilename = scope == null || scope.isEmpty()
                || "all".equals(scope) || "filename".equals(scope);
        boolean searchTag = scope == null || scope.isEmpty()
                || "all".equals(scope) || "tag".equals(scope);

        // 文件名/路径搜索
        if (searchFilename) {
            List<FileNode> nameMatches = fileNodeRepository.searchByName(keyword, userId);
            allResults.addAll(nameMatches);
        }

        // 标签搜索（修复 P0-4：原来错误地调用了 searchByName）
        if (searchTag) {
            List<String> fileNodeIds = tagRepository.findFileNodeIdsByTagName(keyword);
            if (!fileNodeIds.isEmpty()) {
                List<FileNode> tagMatches = fileNodeRepository.findByIds(fileNodeIds);
                // 过滤：只返回当前用户的文件且未删除的
                List<FileNode> filtered = tagMatches.stream()
                        .filter(n -> userId.equals(n.getCreatedBy()))
                        .filter(n -> n.getDeleted() == null || n.getDeleted() == 0)
                        .collect(Collectors.toList());
                allResults.addAll(filtered);
            }
        }

        // 去重 + 过滤已删除
        List<FileNode> filtered = allResults.stream()
                .distinct()
                .filter(n -> n.getDeleted() == null || n.getDeleted() == 0)
                .collect(Collectors.toList());

        // 相关度排序：文件名完全匹配 > 文件名包含 > 路径包含
        filtered.sort(buildRelevanceComparator(keyword));

        int total = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<SearchResultVO.SearchHitVO> hits = new ArrayList<>();
        if (fromIndex < total) {
            List<FileNode> pageResults = filtered.subList(fromIndex, toIndex);
            for (FileNode node : pageResults) {
                float score = calculateScore(node, keyword);
                hits.add(SearchResultVO.SearchHitVO.builder()
                        .fileNodeId(node.getId())
                        .name(node.getName())
                        .path(node.getPath())
                        .nodeType(node.getNodeType())
                        .suffix(node.getSuffix())
                        .size(node.getSize())
                        .highlight(buildHighlight(node, keyword))
                        .score(score)
                        .createdBy(node.getCreatedBy())
                        .updatedAt(node.getUpdatedAt() != null
                                ? node.getUpdatedAt().toString() : null)
                        .build());
            }
        }

        long tookMs = System.currentTimeMillis() - startTime;

        return SearchResultVO.builder()
                .hits(hits)
                .total((long) total)
                .page(page)
                .pageSize(pageSize)
                .tookMs(tookMs)
                .build();
    }

    /**
     * 索引同步（文件上传/更新后调用）
     */
    public void indexFile(String fileNodeId, String content, String userId) {
        log.info("[SearchDomainService] 索引文件: fileNodeId={}", fileNodeId);
        // 数据库搜索模式下无需额外索引，数据已在 nw_file_node 表中
        // 当 ES 可用时，此处应将文件内容写入 ES 索引
    }

    /**
     * 删除索引
     */
    public void removeIndex(String fileNodeId) {
        log.info("[SearchDomainService] 删除索引: fileNodeId={}", fileNodeId);
    }

    /**
     * 重建全量索引
     */
    public void rebuildAllIndices() {
        log.info("[SearchDomainService] 重建全量索引（异步任务）");
    }

    // ==================== 私有方法 ====================

    /**
     * 构建相关度比较器：文件名完全匹配 > 文件名前缀匹配 > 文件名包含 > 路径包含
     */
    private Comparator<FileNode> buildRelevanceComparator(String keyword) {
        String lowerKeyword = keyword != null ? keyword.toLowerCase() : "";
        return Comparator.comparing((FileNode n) -> {
            String name = n.getName() != null ? n.getName().toLowerCase() : "";
            if (name.equals(lowerKeyword)) return 0;
            if (name.startsWith(lowerKeyword)) return 1;
            if (name.contains(lowerKeyword)) return 2;
            return 3;
        });
    }

    /**
     * 计算搜索得分（0-1 之间，越高越相关）
     */
    private float calculateScore(FileNode node, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return 1.0f;
        }
        String name = node.getName() != null ? node.getName().toLowerCase() : "";
        String lowerKeyword = keyword.toLowerCase();

        if (name.equals(lowerKeyword)) return 1.0f;
        if (name.startsWith(lowerKeyword)) return 0.8f;
        if (name.contains(lowerKeyword)) return 0.6f;

        String path = node.getPath() != null ? node.getPath().toLowerCase() : "";
        if (path.contains(lowerKeyword)) return 0.3f;

        return 0.1f;
    }

    private String buildHighlight(FileNode node, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String name = node.getName();
        if (name != null && name.toLowerCase().contains(keyword.toLowerCase())) {
            int idx = name.toLowerCase().indexOf(keyword.toLowerCase());
            int start = Math.max(0, idx - 20);
            int end = Math.min(name.length(), idx + keyword.length() + 20);
            String prefix = start > 0 ? "..." : "";
            String suffix = end < name.length() ? "..." : "";
            return prefix + name.substring(start, end) + suffix;
        }
        return null;
    }
}
