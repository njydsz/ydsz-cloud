package com.njydsz.generator.repository.impl;

import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.mapper.GenColumnMetaMapper;
import com.njydsz.generator.po.GenColumnMetaPO;
import com.njydsz.generator.repository.GenColumnMetaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 列元数据 Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenColumnMetaRepositoryImpl implements GenColumnMetaRepository {

  private final GenColumnMetaMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenColumnMeta save(final GenColumnMeta columnMeta) {
    if (columnMeta.getId() == null) {
      mapper.insert(converter.toPO(columnMeta));
    } else {
      mapper.updateById(converter.toPO(columnMeta));
    }
    return columnMeta;
  }

  @Override
  public List<GenColumnMeta> saveAll(final List<GenColumnMeta> columns) {
    columns.stream().map(converter::toPO).forEach(mapper::insert);
    return columns;
  }

  @Override
  public GenColumnMeta findById(final Long id) {
    GenColumnMetaPO po = mapper.selectById(id);
    return po == null ? null : converter.toEntity(po);
  }

  @Override
  public List<GenColumnMeta> findByTableMetaIdOrderByIdAsc(final Long tableMetaId) {
    return converter.toColumnEntityList(mapper.selectByTableMetaIdOrderByIdAsc(tableMetaId));
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
  }

  @Override
  public void deleteByTableMetaId(final Long tableMetaId) {
    // 使用批量删除
    List<GenColumnMetaPO> existing = mapper.selectByTableMetaIdOrderByIdAsc(tableMetaId);
    if (existing != null && !existing.isEmpty()) {
      existing.stream()
          .map(GenColumnMetaPO::getId)
          .forEach(mapper::deleteById);
    }
  }
}
