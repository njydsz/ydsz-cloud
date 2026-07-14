package com.njydsz.pmis.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.domain.query.PageResult;
import com.njydsz.pmis.nextwiki.domain.entity.SearchIndex;
import com.njydsz.pmis.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.SearchIndexMapper;

import lombok.RequiredArgsConstructor;

/**
 * 搜索索引仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class SearchIndexRepositoryImpl implements SearchIndexRepository {

    private final SearchIndexMapper searchIndexMapper;

    @Override
    public void upsert(SearchIndex index) {
        searchIndexMapper.upsert(index);
    }

    @Override
    public void deleteByFileNodeId(String fileNodeId) {
        searchIndexMapper.deleteByFileNodeId(fileNodeId);
    }

    @Override
    public SearchIndex findByFileNodeId(String fileNodeId) {
        return searchIndexMapper.selectByFileNodeId(fileNodeId);
    }

    @Override
    public List<SearchIndex> findAll() {
        return searchIndexMapper.selectAll();
    }

    @Override
    public List<String> findAllFileNodeIds(String createdBy) {
        return searchIndexMapper.selectAllFileNodeIds(createdBy);
    }

    @Override
    public PageResult<SearchIndex> searchPage(String keyword, String createdBy, String scope,
                                               int page, int pageSize) {
        Page<SearchIndex> pageParam = new Page<>(page, pageSize);
        IPage<SearchIndex> result = searchIndexMapper.searchPage(
                pageParam, keyword, createdBy, scope);
        return PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }
}
