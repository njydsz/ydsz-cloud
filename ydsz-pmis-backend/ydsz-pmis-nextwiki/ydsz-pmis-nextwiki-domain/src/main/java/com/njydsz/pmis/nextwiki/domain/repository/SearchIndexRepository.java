package com.njydsz.pmis.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.pmis.nextwiki.domain.entity.SearchIndex;

/**
 * 搜索索引仓储接口
 * <p>
 * 领域层定义索引增删改查契约，基础设施层提供 MyBatis 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
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
}
