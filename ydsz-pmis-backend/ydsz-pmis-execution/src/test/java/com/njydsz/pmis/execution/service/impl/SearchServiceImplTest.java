package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.es.ProjectSearchDoc;
import com.njydsz.pmis.execution.es.ProjectSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchServiceImpl 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SearchServiceImpl 全文检索服务测试")
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ProjectSearchRepository repository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private SearchServiceImpl searchService;

    private ProjectSearchDoc testDoc;

    @BeforeEach
    void setUp() {
        testDoc = new ProjectSearchDoc("1", 100L, "测试项目", "测试客户", "测试合同", "IT", "进行中", "张三");
    }

    @Test
    @DisplayName("indexProject 应保存文档到 Repository")
    void indexProject_shouldSaveToRepository() {
        searchService.indexProject(testDoc);
        verify(repository).save(testDoc);
    }

    @Test
    @DisplayName("indexProject 在 ES 失败时不应抛出异常")
    void indexProject_shouldNotThrowWhenEsFails() {
        doThrow(new RuntimeException("ES down"))
                .when(repository).save(any());
        searchService.indexProject(testDoc);
    }

    @Test
    @DisplayName("indexProjects 应批量保存到 Repository")
    void indexProjects_shouldSaveAllToRepository() {
        searchService.indexProjects(List.of(testDoc));
        verify(repository).saveAll(any());
    }

    @Test
    @DisplayName("deleteProjectIndex 应从 Repository 删除")
    void deleteProjectIndex_shouldDeleteFromRepository() {
        searchService.deleteProjectIndex("1");
        verify(repository).deleteById("1");
    }

    @Test
    @DisplayName("deleteProjectIndex 在 ES 失败时不应抛出异常")
    void deleteProjectIndex_shouldNotThrowWhenEsFails() {
        doThrow(new RuntimeException("ES down"))
                .when(repository).deleteById(any());
        searchService.deleteProjectIndex("1");
    }

    @Test
    @DisplayName("reindexAll 应清空所有索引")
    void reindexAll_shouldDeleteAll() {
        searchService.reindexAll();
        verify(repository).deleteAll();
    }

    @Test
    @DisplayName("reindexAll 在 ES 失败时不应抛出异常")
    void reindexAll_shouldNotThrowWhenEsFails() {
        doThrow(new RuntimeException("ES down"))
                .when(repository).deleteAll();
        searchService.reindexAll();
    }

    @Test
    @DisplayName("searchProjects 在 ES 失败时应降级返回空结果")
    void searchProjects_shouldReturnEmptyWhenEsFails() {
        when(elasticsearchOperations.search(any(), any(Class.class)))
                .thenThrow(new RuntimeException("ES down"));
        Page<ProjectSearchDoc> result = searchService.searchProjects("测试", PageRequest.of(0, 10));
        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
