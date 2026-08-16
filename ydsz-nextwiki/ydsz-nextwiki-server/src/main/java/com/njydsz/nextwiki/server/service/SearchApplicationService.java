package com.njydsz.nextwiki.server.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;

/**
 * NextWiki 搜索应用服务。
 * <p>
 * 读路径优先走统一搜索引擎（{@code ydsz-common-search}），引擎不可用时降级到 DB LIKE 查询。
 *
 * <p><b>搜索链路：</b>
 * <pre>
 *   用户请求 → SearchController → SearchApplicationService.search()
 *       ↓
 *   主路径：UnifiedSearchService → WikiSearchProvider（权限过滤 + 全文检索）
 *       ↓
 *   降级：SearchDomainService.search()（nw_search_index 表的 LIKE 查询）
 * </pre>
 *
 * <p><b>索引同步链路：</b>
 * <ul>
 *   <li>增量同步：{@code FileOperatedEventListener} 调用 SearchIndexEventBridge → IndexSyncService</li>
 *   <li>全量重建：{@code NextwikiScheduledJobs} 触发 → IndexRebuildService.rebuildAll()</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.search.service.UnifiedSearchService 统一搜索服务
 * @see com.njydsz.nextwiki.domain.service.SearchDomainService DB 降级搜索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

    private final SearchDomainService searchDomainService;
    private final ObjectProvider<UnifiedSearchService> unifiedSearchServiceProvider;
    private final ObjectProvider<SearchEngineRegistry> engineRegistryProvider;

    /**
     * 全文检索（按关键词在用户可见范围内分页搜索）。
     * <p>
     * 搜索引擎可用时走统一检索链路（PG/ES + {@code WikiSearchProvider} 权限过滤），
     * 不可用时降级到 DB LIKE。引擎异常同样降级并打印 warn 日志，保证搜索接口始终可用。
     *
     * @param keyword  搜索关键词（文件名/路径/全文/标签）
     * @param userId   操作人 ID（用于权限与结果过滤）
     * @param scope    搜索作用域（如 "all"/"my"，由领域服务解释）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页搜索结果 {@link SearchResultVO}
     * @complexity 引擎路径 O(query)（一次搜索引擎查询 + 分页）；DB 路径同 {@link SearchDomainService#search}
     * @note 只读，无事务边界
     */
    public SearchResultVO search(String keyword, String userId, String scope,
                                 int page, int pageSize) {
        SearchEngineRegistry registry = engineRegistryProvider.getIfAvailable();
        UnifiedSearchService unifiedSearch = unifiedSearchServiceProvider.getIfAvailable();

        if (registry != null && unifiedSearch != null && registry.isPrimaryAvailable()) {
            return searchViaEngine(unifiedSearch, keyword, userId, page, pageSize);
        }

        log.info("[SearchApplicationService] 搜索引擎不可用，降级 DB LIKE: keyword={}", keyword);
        return searchDomainService.search(keyword, userId, scope, page, pageSize);
    }

    /**
     * 重建全量搜索索引（通常由定时任务或运维操作触发）。
     *
     * @return 无返回值
     * @complexity O(N)（N 为文件总数，遍历重新建索引，耗时较长）
     * @note 非事务（批量操作）；执行期间建议避开高峰期，避免影响在线搜索
     * @see com.njydsz.nextwiki.server.job.NextwikiScheduledJobs#rebuildSearchIndex()
     */
    public void rebuildAllIndices() {
        searchDomainService.rebuildAllIndices();
    }

    // ==================== 私有方法 ====================

    /**
     * 通过统一搜索引擎执行检索，结果转换为 {@link SearchResultVO}。
     */
    private SearchResultVO searchViaEngine(UnifiedSearchService unifiedSearch,
                                           String keyword, String userId,
                                           int page, int pageSize) {
        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .types(List.of("wiki"))
                .page(page)
                .pageSize(pageSize)
                .userId(userId)
                .highlight(true)
                .build();

        try {
            SearchResponse response = unifiedSearch.search(request);
            log.info("[SearchApplicationService] 引擎检索完成: keyword={}, total={}, tookMs={}, engine={}",
                    keyword, response.getTotal(), response.getTookMs(), response.getEngine());
            return convertResponse(response);
        } catch (Exception e) {
            log.warn("[SearchApplicationService] 引擎检索异常，降级 DB: keyword={}, error={}",
                    keyword, e.getMessage());
            return searchDomainService.search(keyword, userId, null, page, pageSize);
        }
    }

    /**
     * 将 {@link SearchResponse} 转换为 {@link SearchResultVO}。
     */
    private SearchResultVO convertResponse(SearchResponse response) {
        List<SearchResultVO.SearchHitVO> hits = response.getHits().stream()
                .map(this::convertHit)
                .collect(Collectors.toList());

        return SearchResultVO.builder()
                .hits(hits)
                .total(response.getTotal())
                .page(response.getPage())
                .pageSize(response.getPageSize())
                .tookMs(response.getTookMs())
                .build();
    }

    /**
     * 单条命中转换：将引擎返回的 {@link SearchHit} 映射到 VO。
     */
    private SearchResultVO.SearchHitVO convertHit(SearchHit hit) {
        return SearchResultVO.SearchHitVO.builder()
                .fileNodeId(hit.getId())
                .name(hit.getTitle())
                .path(hit.getSubtitle())
                .nodeType(FileNode.TYPE_FILE)
                .highlight(hit.getHighlight() != null ? hit.getHighlight() : hit.getSnippet())
                .score(hit.getScore())
                .tags(hit.getTags())
                .updatedAt(hit.getUpdatedAt())
                .build();
    }
}
