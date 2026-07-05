package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.search.ProjectSearchVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 项目全文检索服务接口。
 *
 * <p>P2-19：移除 Elasticsearch，改用 PostgreSQL {@code tsvector} 实现中文/混合关键词检索。
 * 所有方法保持空安全：关键词为空时直接返回空分页，避免对数据库产生无效查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SearchService {

    /**
     * 全文检索项目（PG tsvector，替代 ES multi_match）。
     *
     * <p>检索范围：立项主表的 project_name / customer_name / pm_name +
     * 关联合同表首个合同记录的 contract_name，按 PG {@code ts_rank} 倒序匹配。
     *
     * @param keyword  搜索关键词（不可为空；为空时由调用方提前过滤）
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    Page<ProjectSearchVO> searchProjects(String keyword, Pageable pageable);

    /**
     * 重建所有索引。
     *
     * <p>P2-19：PG tsvector 不需要重建索引（已通过表达式索引在写入时自动维护），
     * 保留方法仅为 API 兼容性（旧前端调用仍可成功），执行后直接返回成功标识。
     *
     * @return 重建结果
     */
    String reindexAll();
}
