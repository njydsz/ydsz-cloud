package com.njydsz.pmis.nextwiki.domain.service;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.search.api.SearchHit;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchEngine;
import com.njydsz.pmis.common.search.service.IndexRebuildService;
import com.njydsz.pmis.common.search.sync.IndexSyncListener;
import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.pmis.nextwiki.domain.vo.SearchResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索领域服务
 * <p>
 * 接入 {@code ydsz-pmis-common-search} 统一搜索框架，提供文件名/路径/标签/内容搜索能力。
 *
 * <p><b>搜索能力分级：</b>
 * <ul>
 *   <li>P0 - 基于文件名/路径的 LIKE 搜索（数据库） ✓ 已实现</li>
 *   <li>P1 - 基于 PG tsvector + zhparser 的中文全文搜索 ✓ 已实现</li>
 *   <li>P2 - 支持高亮、聚合、相关性排序 ✓ 已实现</li>
 *   <li>P3 - 文档内容索引搜索（需 common-docs）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchDomainService {

    private final SearchEngine searchEngine;
    private final IndexRebuildService indexRebuildService;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

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
        log.info("[SearchDomainService] 搜索: keyword={}, userId={}, scope={}, page={}, pageSize={}",
                keyword, userId, scope, page, pageSize);

        if (keyword == null || keyword.isBlank()) {
            return SearchResultVO.builder()
                    .hits(List.of())
                    .total(0L)
                    .page(page)
                    .pageSize(pageSize)
                    .tookMs(0L)
                    .build();
        }

        try {
            SearchRequest.SearchRequestBuilder requestBuilder = SearchRequest.builder()
                    .keyword(keyword)
                    .types(List.of("wiki"))
                    .page(page)
                    .pageSize(pageSize)
                    .highlight(true)
                    .fuzzy(true)
                    .userId(userId);

            // scope 处理
            if ("filename".equalsIgnoreCase(scope)) {
                requestBuilder.titleOnly(true);
            }

            SearchResponse response = searchEngine.search(requestBuilder.build());

            // 转换为 SearchResultVO
            List<SearchResultVO.SearchHitVO> hits = response.getHits().stream()
                    .map(this::toSearchHitVO)
                    .collect(Collectors.toList());

            return SearchResultVO.builder()
                    .hits(hits)
                    .total(response.getTotal())
                    .page(page)
                    .pageSize(pageSize)
                    .tookMs(response.getTookMs())
                    .build();

        } catch (Exception e) {
            log.error("[SearchDomainService] 搜索失败，降级返回空结果: keyword={}", keyword, e);
            return SearchResultVO.builder()
                    .hits(List.of())
                    .total(0L)
                    .page(page)
                    .pageSize(pageSize)
                    .tookMs(0L)
                    .build();
        }
    }

    /**
     * 索引同步（文件上传/更新后调用）
     *
     * @param fileNodeId 文件节点 ID
     * @param content    文件内容（可选，为空则仅索引元数据）
     * @param userId     操作人 ID
     */
    public void indexFile(String fileNodeId, String content, String userId) {
        log.info("[SearchDomainService] 索引文件: fileNodeId={}", fileNodeId);

        try {
            FileNode node = fileNodeRepository.findById(fileNodeId);
            if (node == null) {
                log.warn("[SearchDomainService] 文件节点不存在: {}", fileNodeId);
                return;
            }

            IndexDocument document = toIndexDocument(node, content);
            eventPublisher.publishEvent(IndexSyncListener.IndexOperationEvent.upsert(document));

        } catch (Exception e) {
            log.error("[SearchDomainService] 索引文件失败: fileNodeId={}", fileNodeId, e);
        }
    }

    /**
     * 删除索引
     *
     * @param fileNodeId 文件节点 ID
     */
    public void removeIndex(String fileNodeId) {
        log.info("[SearchDomainService] 删除索引: fileNodeId={}", fileNodeId);
        try {
            eventPublisher.publishEvent(
                    IndexSyncListener.IndexOperationEvent.delete("wiki", fileNodeId));
        } catch (Exception e) {
            log.error("[SearchDomainService] 删除索引失败: fileNodeId={}", fileNodeId, e);
        }
    }

    /**
     * 重建全量索引
     */
    public void rebuildAllIndices() {
        log.info("[SearchDomainService] 重建全量索引（异步任务）");
        try {
            int count = indexRebuildService.rebuildAll("wiki", null);
            log.info("[SearchDomainService] 全量重建完成: count={}", count);
        } catch (Exception e) {
            log.error("[SearchDomainService] 全量重建失败", e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * FileNode 转 IndexDocument
     */
    private IndexDocument toIndexDocument(FileNode node, String content) {
        return IndexDocument.builder()
                .id(node.getId())
                .type("wiki")
                .title(node.getName())
                .subtitle(node.getPath())
                .content(content != null ? content : node.getName())
                .snippet(content != null && content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content)
                .status(node.getShareStatus())
                .path("/nextwiki/files/" + node.getId())
                .tenantId(node.getCreatedBy())
                .createdBy(node.getCreatedBy())
                .createdAt(node.getCreatedAt() != null
                        ? node.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .updatedBy(node.getUpdatedBy())
                .updatedAt(node.getUpdatedAt() != null
                        ? node.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .build();
    }

    /**
     * SearchHit 转 SearchHitVO
     */
    private SearchResultVO.SearchHitVO toSearchHitVO(SearchHit hit) {
        return SearchResultVO.SearchHitVO.builder()
                .fileNodeId(hit.getId())
                .name(hit.getTitle())
                .path(hit.getSubtitle())
                .nodeType("file")
                .highlight(hit.getHighlight())
                .score(hit.getScore())
                .tags(hit.getTags() != null ? hit.getTags() : Collections.emptyList())
                .createdBy(hit.getCreatedAt())
                .updatedAt(hit.getUpdatedAt())
                .build();
    }
}
