package com.njydsz.pmis.project.server.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.njydsz.pmis.common.jdbc.constant.DataSourceConstants;
import com.njydsz.pmis.common.search.api.SearchHit;
import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.provider.SearchProviderRegistry;
import com.njydsz.pmis.common.search.service.IndexRebuildService;
import com.njydsz.pmis.common.search.service.UnifiedSearchService;
import com.njydsz.pmis.common.search.sync.IndexSyncListener;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.project.domain.entity.InitiationDO;
import com.njydsz.pmis.project.domain.query.ProjectSearchVO;
import com.njydsz.pmis.project.domain.query.UniversalSearchVO;
import com.njydsz.pmis.project.infra.mapper.InitiationMapper;
import com.njydsz.pmis.project.server.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目搜索 Service 实现（P2-19 重构：接入 ydsz-pmis-common-search 统一搜索框架）。
 *
 * <p>核心变更：
 * <ul>
 *   <li>搜索逻辑从硬编码 PG tsvector SQL 迁移到 {@link UnifiedSearchService}</li>
 *   <li>通过 {@link com.njydsz.pmis.project.server.search.ProjectSearchProvider} 注册可搜索实体</li>
 *   <li>支持高亮、模糊匹配、聚合等高级搜索能力</li>
 *   <li>保留原 PG tsvector SQL 作为降级方案（搜索引擎不可用时自动回退）</li>
 * </ul>
 *
 * <p>降级策略：搜索服务不可用时回退到原 {@link InitiationMapper#searchByFullText}，
 * 保证核心搜索功能不中断。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS(DataSourceConstants.SLAVE)
public class SearchServiceImpl implements SearchService {

    private final InitiationMapper initiationMapper;
    private final UnifiedSearchService unifiedSearchService;
    private final SearchProviderRegistry providerRegistry;
    private final IndexRebuildService indexRebuildService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 全文检索项目。
     *
     * <p>优先使用 {@link UnifiedSearchService}（支持中文分词、高亮、模糊匹配），
     * 当搜索服务不可用时降级到原 PG tsvector SQL。
     *
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    @Override
    public Page<ProjectSearchVO> searchProjects(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        try {
            // 优先使用统一搜索服务
            String tenantId = TenantContext.getTenantId();
            SearchRequest request = SearchRequest.builder()
                    .keyword(keyword)
                    .types(List.of("project"))
                    .page(pageable.getPageNumber() + 1)
                    .pageSize(pageable.getPageSize())
                    .highlight(true)
                    .fuzzy(true)
                    .tenantId(tenantId)
                    .build();

            SearchResponse response = unifiedSearchService.search(request);

            // 转换为 ProjectSearchVO
            List<ProjectSearchVO> records = response.getHits().stream()
                    .map(this::toProjectSearchVO)
                    .toList();

            return new PageImpl<>(records, pageable, response.getTotal());

        } catch (Exception e) {
            log.warn("[Search] 统一搜索服务异常，降级到 PG tsvector: keyword={}, error={}",
                    keyword, e.getMessage());
            return fallbackSearchProjects(keyword, pageable);
        }
    }

    /**
     * 重建索引。
     *
     * <p>通过 {@link IndexRebuildService} 执行全量索引重建。
     *
     * @return 重建结果消息
     */
    @Override
    public String reindexAll() {
        try {
            String tenantId = TenantContext.getTenantId();
            int count = indexRebuildService.rebuildAll("project", tenantId);
            String message = "索引重建完成: count=" + count;
            log.info("[Search] {}", message);
            return message;
        } catch (Exception e) {
            log.error("[Search] 索引重建失败", e);
            return "索引重建失败: " + e.getMessage();
        }
    }

    /**
     * 跨实体统一搜索。
     *
     * <p>通过 {@link UnifiedSearchService} 搜索所有已注册实体类型，
     * 返回统一的搜索结果格式。
     *
     * @param keyword 搜索关键词
     * @param size    每类实体最大返回条数
     * @return 统一搜索结果列表
     */
    @Override
    public List<UniversalSearchVO> searchAll(String keyword, int size) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        try {
            String tenantId = TenantContext.getTenantId();
            SearchRequest request = SearchRequest.builder()
                    .keyword(keyword)
                    .page(1)
                    .pageSize(size)
                    .highlight(true)
                    .fuzzy(true)
                    .tenantId(tenantId)
                    .build();

            SearchResponse response = unifiedSearchService.search(request);

            List<UniversalSearchVO> results = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                results.add(UniversalSearchVO.builder()
                        .type(hit.getType())
                        .id(hit.getId())
                        .title(hit.getTitle())
                        .subtitle(hit.getSubtitle())
                        .status(hit.getStatus())
                        .path(hit.getPath())
                        .build());
            }

            // 如果零结果且有建议
            if (results.isEmpty() && response.getSuggestion() != null
                    && response.getSuggestion().getSuggestions() != null) {
                log.info("[Search] 零结果搜索建议: keyword={}, suggestions={}",
                        keyword, response.getSuggestion().getSuggestions());
            }

            return results;

        } catch (Exception e) {
            log.warn("[Search] 统一搜索异常，降级到项目搜索: keyword={}, error={}",
                    keyword, e.getMessage());

            // 降级：仅搜索项目
            List<UniversalSearchVO> results = new ArrayList<>();
            try {
                Page<ProjectSearchVO> projectPage = searchProjects(
                        keyword, PageRequest.of(0, size));
                for (ProjectSearchVO p : projectPage.getContent()) {
                    results.add(UniversalSearchVO.builder()
                            .type("project")
                            .id(p.getId())
                            .title(p.getProjectName())
                            .subtitle(joinNonBlank(p.getCustomerName(), p.getPmName()))
                            .status(p.getStage())
                            .path("/project/initiation?highlight=" + p.getId())
                            .build());
                }
            } catch (Exception ex) {
                log.warn("[Search] 降级项目搜索也失败: keyword={}", keyword, ex);
            }
            return results;
        }
    }

    /**
     * 发布索引同步事件（供项目 CRUD 调用）
     *
     * @param entity 项目实体
     */
    public void publishIndexEvent(InitiationDO entity) {
        try {
            IndexDocument document = providerRegistry.<InitiationDO>getProvider("project")
                    .toIndexDocument(entity);
            if (document != null) {
                eventPublisher.publishEvent(IndexSyncListener.IndexOperationEvent.upsert(document));
            }
        } catch (Exception e) {
            log.warn("[Search] 索引同步事件发布失败: id={}", entity.getId(), e);
        }
    }

    /**
     * 发布索引删除事件
     *
     * @param projectId 项目 ID
     */
    public void publishDeleteIndexEvent(String projectId) {
        try {
            eventPublisher.publishEvent(
                    IndexSyncListener.IndexOperationEvent.delete("project", projectId));
        } catch (Exception e) {
            log.warn("[Search] 索引删除事件发布失败: id={}", projectId, e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 降级搜索（原 PG tsvector SQL）
     */
    private Page<ProjectSearchVO> fallbackSearchProjects(String keyword, Pageable pageable) {
        try {
            String tenantId = TenantContext.getTenantId();
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();
            long total = initiationMapper.countByFullText(keyword, tenantId);
            if (total == 0) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            List<ProjectSearchVO> records = initiationMapper.searchByFullText(keyword, tenantId, offset, limit);
            return new PageImpl<>(records, pageable, total);
        } catch (Exception e) {
            log.warn("[Search] PG tsvector 降级检索也失败: keyword={}", keyword, e);
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * 搜索命中转 ProjectSearchVO
     */
    private ProjectSearchVO toProjectSearchVO(SearchHit hit) {
        ProjectSearchVO vo = new ProjectSearchVO();
        vo.setId(hit.getId());
        vo.setProjectName(hit.getTitle());
        // 从 subtitle 解析客户名和项目经理
        if (hit.getSubtitle() != null) {
            String[] parts = hit.getSubtitle().split(" · ");
            if (parts.length >= 1) vo.setCustomerName(parts[0]);
            if (parts.length >= 2) vo.setPmName(parts[1]);
        }
        vo.setStage(hit.getStatus());
        return vo;
    }

    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (!sb.isEmpty()) sb.append(" · ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
