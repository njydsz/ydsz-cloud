package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.es.ProjectSearchDoc;
import com.njydsz.pmis.execution.es.ProjectSearchRepository;
import com.njydsz.pmis.execution.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 全文检索服务实现。
 *
 * <p>使用 Elasticsearch multi_match 查询实现中文分词搜索，
 * 所有 ES 操作均具备降级能力：ES 不可用时捕获异常并返回空结果，不影响主业务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final ProjectSearchRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 构造函数注入。
     *
     * @param repository            ES Repository
     * @param elasticsearchOperations ES 操作模板
     */
    public SearchServiceImpl(ProjectSearchRepository repository,
                             ElasticsearchOperations elasticsearchOperations) {
        this.repository = repository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public Page<ProjectSearchDoc> searchProjects(String keyword, Pageable pageable) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.multiMatch(m -> m
                            .query(keyword)
                            .fields("projectName^3", "customerName^2", "contractName", "pmName")
                            .fuzziness("AUTO")))
                    .withPageable(pageable)
                    .build();
            SearchHits<ProjectSearchDoc> hits = elasticsearchOperations.search(query, ProjectSearchDoc.class);
            List<ProjectSearchDoc> content = new ArrayList<>();
            hits.forEach(hit -> content.add(hit.getContent()));
            return new PageImpl<>(content, pageable, hits.getTotalHits());
        } catch (Exception e) {
            log.warn("[ES] 搜索失败，降级返回空结果: keyword={}, error={}", keyword, e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    @Override
    public void indexProject(ProjectSearchDoc doc) {
        try {
            repository.save(doc);
            log.debug("[ES] 索引项目文档: id={}, name={}", doc.getId(), doc.getProjectName());
        } catch (Exception e) {
            log.warn("[ES] 索引项目文档失败，降级忽略: id={}, error={}", doc.getId(), e.getMessage());
        }
    }

    @Override
    public void indexProjects(List<ProjectSearchDoc> docs) {
        try {
            repository.saveAll(docs);
            log.info("[ES] 批量索引项目文档: count={}", docs.size());
        } catch (Exception e) {
            log.warn("[ES] 批量索引失败，降级忽略: count={}, error={}", docs.size(), e.getMessage());
        }
    }

    @Override
    public void deleteProjectIndex(String id) {
        try {
            repository.deleteById(id);
        } catch (Exception e) {
            log.warn("[ES] 删除索引失败，降级忽略: id={}, error={}", id, e.getMessage());
        }
    }

    @Override
    public void reindexAll() {
        try {
            repository.deleteAll();
            log.info("[ES] 清空索引，等待增量同步");
        } catch (Exception e) {
            log.warn("[ES] 重建索引失败: error={}", e.getMessage());
        }
    }
}
