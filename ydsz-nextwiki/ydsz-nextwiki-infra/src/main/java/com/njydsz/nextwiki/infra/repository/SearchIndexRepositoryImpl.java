package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.query.SearchIndexQuery;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.SearchIndexDO;
import com.njydsz.nextwiki.infra.mapper.SearchIndexMapper;

/**
 * 搜索索引仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchIndexRepositoryImpl implements SearchIndexRepository {

  private final SearchIndexMapper searchIndexMapper;
  private final NextwikiConverter converter;

  @Override
  public void upsert(SearchIndexDTO dto) {
    SearchIndexDO entity = converter.dtoToEntity(dto);
    searchIndexMapper.upsert(entity);
  }

  @Override
  public void deleteByFileNodeId(String fileNodeId) {
    searchIndexMapper.deleteByFileNodeId(fileNodeId);
  }

  @Override
  public Optional<SearchIndexVO> findByFileNodeId(String fileNodeId) {
    return Optional.ofNullable(searchIndexMapper.selectByFileNodeId(fileNodeId))
        .map(converter::entityToVO);
  }

  @Override
  public List<SearchIndexVO> findAll() {
    return converter.searchIndexListToVO(searchIndexMapper.selectAll());
  }

  @Override
  public List<String> findAllFileNodeIds(String createdBy) {
    return searchIndexMapper.selectAllFileNodeIds(createdBy);
  }

  @Override
  public PageResponse<List<SearchIndexVO>> searchPage(SearchIndexQuery query) {
    Page<SearchIndexDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
    IPage<SearchIndexDO> result =
        searchIndexMapper.searchPage(
            pageParam, query.getKeyword(), query.getCreatedBy(), query.getScope());
    List<SearchIndexVO> vos = converter.searchIndexListToVO(result.getRecords());
    Page<SearchIndexVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }
}
