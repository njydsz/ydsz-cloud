package com.njydsz.generator.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.mapper.GenColumnMetaMapper;
import com.njydsz.generator.repository.GenColumnMetaRepository;

/**
 * 列元数据 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenColumnMetaRepositoryImpl implements GenColumnMetaRepository {

  private final GenColumnMetaMapper mapper;

  @Override
  public GenColumnMeta save(final GenColumnMeta columnMeta) {
    if (columnMeta.getId() == null) {
      mapper.insert(columnMeta);
    } else {
      mapper.updateById(columnMeta);
    }
    return columnMeta;
  }

  @Override
  public List<GenColumnMeta> batchSave(final List<GenColumnMeta> columns) {
    columns.forEach(mapper::insert);
    return columns;
  }

  @Override
  public GenColumnMeta findById(final Long id) {
    return mapper.selectById(id);
  }

  @Override
  public List<GenColumnMeta> findByTableMetaIdOrderByIdAsc(final Long tableMetaId) {
    return mapper.selectByTableMetaIdOrderByIdAsc(tableMetaId);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
  }

  @Override
  public void deleteByTableMetaId(final Long tableMetaId) {
    LambdaQueryWrapper<GenColumnMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenColumnMeta::getTableMetaId, tableMetaId);
    mapper.delete(wrapper);
  }
}
