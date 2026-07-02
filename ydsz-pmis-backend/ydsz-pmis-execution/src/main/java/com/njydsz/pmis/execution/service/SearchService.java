package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.es.ProjectSearchDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 全文检索服务接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SearchService {

    /**
     * 全文检索项目。
     *
     * @param keyword  搜索关键词
     * @param pageable 分页参数
     * @return 搜索结果
     */
    Page<ProjectSearchDoc> searchProjects(String keyword, Pageable pageable);

    /**
     * 索引单个项目文档。
     *
     * @param doc 项目文档
     */
    void indexProject(ProjectSearchDoc doc);

    /**
     * 批量索引项目文档。
     *
     * @param docs 项目文档列表
     */
    void indexProjects(List<ProjectSearchDoc> docs);

    /**
     * 删除项目索引。
     *
     * @param id 文档 ID
     */
    void deleteProjectIndex(String id);

    /**
     * 重建所有索引。
     */
    void reindexAll();
}
