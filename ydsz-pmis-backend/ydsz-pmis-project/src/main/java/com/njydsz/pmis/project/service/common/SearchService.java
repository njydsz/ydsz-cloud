package com.njydsz.pmis.project.service.common;

import com.njydsz.pmis.project.search.ProjectSearchVO;
import com.njydsz.pmis.project.search.UniversalSearchVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 项目全文检索服务接口。
 *
 * <p>P2-19：移除 Elasticsearch，改用 PostgreSQL {@code tsvector} 实现中文/混合关键词检索。
 * 所有方法保持空安全：关键词为空时直接返回空分页，避免对数据库产生无效查询。
 *
 * <p>P2-1：新增 {@link #searchAll(String, int)} 跨实体统一搜索。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SearchService {

    /**
     * 全文检索项目（PG tsvector，替代 ES multi_match）。
     */
    Page<ProjectSearchVO> searchProjects(String keyword, Pageable pageable);

    /**
     * 重建所有索引。
     */
    String reindexAll();

    /**
     * 跨实体统一搜索 (P2-1)。
     *
     * <p>一次请求搜索项目 / 合同 / 审批 / 工单 / 人员 / 知识库等实体，
     * 按实体类型分组返回，每类最多 {@code size} 条。
     *
     * @param keyword 搜索关键词
     * @param size    每类实体最大返回条数
     * @return 统一搜索结果列表
     */
    List<UniversalSearchVO> searchAll(String keyword, int size);
}
