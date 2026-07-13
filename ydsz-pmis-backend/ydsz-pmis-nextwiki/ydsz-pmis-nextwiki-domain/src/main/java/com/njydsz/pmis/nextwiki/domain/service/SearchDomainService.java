package com.njydsz.pmis.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索领域服务
 * <p>
 * 提供基于数据库的文件名/路径 LIKE 搜索（P0 fallback）。
 * 当 Elasticsearch 可用时，由 {@code WikiSearchProvider} 覆盖为全文搜索。
 *
 * <p><b>搜索能力分级：</b>
 * <ul>
 *   <li>P0 - 基于文件名/路径的 LIKE 搜索（数据库）</li>
 *   <li>P1 - 基于 Elasticsearch 的全文搜索（内容索引）</li>
 *   <li>P2 - 支持高亮、聚合、相关性排序</li>
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

        if (scope == null || scope.isEmpty() || "all".equals(scope) || "filename".equals(scope)) {
            List<FileNode> nameMatches = fileNodeRepository.searchByName(keyword, userId);
            allResults.addAll(nameMatches);
        }

        if ("tag".equals(scope)) {
            List<FileNode> tagMatches = fileNodeRepository.searchByName(keyword, userId);
            allResults.addAll(tagMatches);
        }

        List<FileNode> filtered = allResults.stream()
                .distinct()
                .filter(n -> n.getDeleted() == null || n.getDeleted() == 0)
                .toList();

        int total = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<SearchResultVO.SearchHitVO> hits = new ArrayList<>();
        if (fromIndex < total) {
            List<FileNode> pageResults = filtered.subList(fromIndex, toIndex);
            for (FileNode node : pageResults) {
                hits.add(SearchResultVO.SearchHitVO.builder()
                        .fileNodeId(node.getId())
                        .name(node.getName())
                        .path(node.getPath())
                        .nodeType(node.getNodeType())
                        .suffix(node.getSuffix())
                        .size(node.getSize())
                        .highlight(buildHighlight(node, keyword))
                        .score(1.0f)
                        .createdBy(node.getCreatedBy())
                        .updatedAt(node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : null)
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

    private String buildHighlight(FileNode node, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String name = node.getName();
        if (name != null && name.toLowerCase().contains(keyword.toLowerCase())) {
            int idx = name.toLowerCase().indexOf(keyword.toLowerCase());
            int start = Math.max(0, idx - 20);
            int end = Math.min(name.length(), idx + keyword.length() + 20);
            return "..." + name.substring(start, end) + "...";
        }
        return null;
    }
}
