package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.nextwiki.domain.entity.SearchIndex;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.infra.mapper.SearchIndexMapper;

import lombok.RequiredArgsConstructor;

/**
 * 搜索索引仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class SearchIndexRepositoryImpl implements SearchIndexRepository {

    private final SearchIndexMapper searchIndexMapper;

    /**
     * 新增或更新搜索索引记录（幂等操作）：已存在则覆盖、不存在则插入，便于文件版本变更后同步索引。
     *
     * @param index 搜索索引实体（含 fileNodeId、标题、内容、标签等可被检索字段）
     */
    @Override
    public void upsert(SearchIndex index) {
        searchIndexMapper.upsert(index);
    }

    /**
     * 物理删除指定文件节点的搜索索引（文件删除/移出回收站彻底清理时调用）。
     *
     * @param fileNodeId 文件节点 ID
     */
    @Override
    public void deleteByFileNodeId(String fileNodeId) {
        searchIndexMapper.deleteByFileNodeId(fileNodeId);
    }

    /**
     * 查询指定文件节点对应的搜索索引记录。
     *
     * @param fileNodeId 文件节点 ID
     * @return 索引实体；不存在则返回 null
     */
    @Override
    public SearchIndex findByFileNodeId(String fileNodeId) {
        return searchIndexMapper.selectByFileNodeId(fileNodeId);
    }

    /**
     * 查询全部未删除的搜索索引记录，主要用于索引全量重建/校验。
     *
     * @return 索引实体列表
     */
    @Override
    public List<SearchIndex> findAll() {
        return searchIndexMapper.selectAll();
    }

    /**
     * 查询待（重建）索引的文件节点 ID 列表；createdBy 为 null 时返回全部用户文件，用于索引重建任务的分页遍历。
     *
     * @param createdBy 创建人 ID（传 null 表示不过滤，查询全部）
     * @return 文件节点 ID 列表（按创建时间升序）
     */
    @Override
    public List<String> findAllFileNodeIds(String createdBy) {
        return searchIndexMapper.selectAllFileNodeIds(createdBy);
    }

    /**
     * 分页搜索索引：将 MyBatis-Plus 的分页结果封装为统一的 {@link PageResponse}。
     * keyword 为空时退化为按权限（createdBy）列示，scope 控制检索维度（all/filename/content/tag）。
     *
     * @param keyword   搜索关键词
     * @param createdBy 创建人（权限过滤，避免跨用户检索）
     * @param scope     搜索范围：all / filename / content / tag
     * @param page      页码（从 1 开始）
     * @param pageSize  每页大小
     * @return 统一分页结果，含命中的索引实体列表与总数
     */
    @Override
    public PageResponse<List<SearchIndex>> searchPage(String keyword, String createdBy, String scope,
                                               int page, int pageSize) {
        Page<SearchIndex> pageParam = new Page<>(page, pageSize);
        IPage<SearchIndex> result = searchIndexMapper.searchPage(
                pageParam, keyword, createdBy, scope);
        return PageResponses.success(result);
    }
}
