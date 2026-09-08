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
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.entity.SearchIndex;
import com.njydsz.nextwiki.domain.query.SearchIndexQuery;
import com.njydsz.nextwiki.domain.query.SearchQuery;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.infra.mapper.SearchIndexMapper;

/**
 * 搜索索引仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchIndexRepositoryImpl implements SearchIndexRepository {

  /** 高级搜索默认页码（未指定时使用） */
  private static final int DEFAULT_PAGE = 1;

  /** 高级搜索默认每页条数（未指定时使用） */
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final SearchIndexMapper searchIndexMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 写入/更新搜索索引（on duplicate key update 语义）。
   *
   * @param dto 搜索索引数据传输对象
   */
  @Override
  public void upsert(SearchIndexDTO dto) {
    SearchIndex entity = mapper.searchIndexToEntity(dto);
    searchIndexMapper.upsert(entity);
  }

  /**
   * 按文件节点 ID 删除索引记录（文件删除时级联清理）。
   *
   * @param fileNodeId 文件节点 ID
   */
  @Override
  public void deleteByFileNodeId(String fileNodeId) {
    searchIndexMapper.deleteByFileNodeId(fileNodeId);
  }

  /**
   * 按文件节点 ID 查询搜索索引。
   *
   * @param fileNodeId 文件节点 ID
   * @return 搜索索引视图对象（可能为空）
   */
  @Override
  public Optional<SearchIndexVO> findByFileNodeId(String fileNodeId) {
    return Optional.ofNullable(searchIndexMapper.selectByFileNodeId(fileNodeId))
        .map(mapper::searchIndexToVO);
  }

  /**
   * 查询指定用户的所有可索引文件 ID 列表（全量索引构建时使用）。
   *
   * @param createdBy 用户 ID
   * @return 文件节点 ID 列表
   */
  @Override
  public List<String> findAllFileNodeIds(String createdBy) {
    return searchIndexMapper.selectAllFileNodeIds(createdBy);
  }

  /**
   * 分页搜索索引（基础关键词搜索）。
   *
   * @param query 搜索查询条件
   * @return 分页搜索结果
   */
  @Override
  public PageResponse<List<SearchIndexVO>> searchPage(SearchIndexQuery query) {
    Page<SearchIndex> pageParam = new Page<>(query.getPage(), query.getPageSize());
    IPage<SearchIndex> result =
        searchIndexMapper.searchPage(
            pageParam, query.getKeyword(), query.getCreatedBy(), query.getScope());
    List<SearchIndexVO> vos = mapper.searchIndexListToVO(result.getRecords());
    Page<SearchIndexVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }

  /**
   * 高级语法分页搜索（支持字段限定、布尔运算等）。
   *
   * @param query 高级搜索查询条件
   * @return 分页搜索结果
   */
  @Override
  public PageResponse<List<SearchIndexVO>> searchAdvanced(SearchQuery query) {
    Page<SearchIndex> pageParam = new Page<>(
        query.getPage() != null ? query.getPage() : DEFAULT_PAGE,
        query.getPageSize() != null ? query.getPageSize() : DEFAULT_PAGE_SIZE);
    IPage<SearchIndex> result =
        searchIndexMapper.searchAdvanced(pageParam, query);
    List<SearchIndexVO> vos = mapper.searchIndexListToVO(result.getRecords());
    Page<SearchIndexVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }
}
