package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.nextwiki.domain.entity.SearchIndex;

/**
 * 搜索索引仓储接口
 * <p>
 * 领域层定义索引增删改查契约，基础设施层提供 MyBatis 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SearchIndexRepository {

    /**
     * 新增或更新索引（以 fileNodeId 为唯一键）
     */
    void upsert(SearchIndex index);

    /**
     * 根据文件节点ID删除索引
     */
    void deleteByFileNodeId(String fileNodeId);

    /**
     * 根据文件节点ID查询索引
     */
    SearchIndex findByFileNodeId(String fileNodeId);

    /**
     * 查询所有未删除的索引记录
     */
    List<SearchIndex> findAll();

    /**
     * 查询所有未删除的文件节点ID（用于索引重建）
     *
     * @param createdBy 创建人，传 null 查询全部
     */
    List<String> findAllFileNodeIds(String createdBy);

    /**
     * 数据库分页搜索索引（使用 LIMIT/OFFSET 在 SQL 层面分页）
     *
     * @param keyword   搜索关键词
     * @param createdBy 创建人（权限过滤）
     * @param scope     搜索范围：all / filename / content / tag
     * @param page      页码（从 1 开始）
     * @param pageSize  每页大小
     * @return 分页搜索结果
     */
    PageResponse<SearchIndex> searchPage(String keyword, String createdBy, String scope,
                                        int page, int pageSize);
}
