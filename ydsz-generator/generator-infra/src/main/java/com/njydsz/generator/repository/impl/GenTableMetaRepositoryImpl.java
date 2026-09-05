package com.njydsz.generator.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.mapper.GenTableMetaMapper;
import com.njydsz.generator.po.GenTableMetaPO;
import com.njydsz.generator.repository.GenTableMetaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 表元数据 Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTableMetaRepositoryImpl implements GenTableMetaRepository {

  private final GenTableMetaMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenTableMeta save(final GenTableMeta tableMeta) {
    if (tableMeta.getId() == null) {
      tableMeta.setCachedAt(LocalDateTime.now());
      mapper.insert(converter.toPO(tableMeta));
    } else {
      tableMeta.setCachedAt(LocalDateTime.now());
      mapper.updateById(converter.toPO(tableMeta));
    }
    log.info("保存表元数据 id={} table={}", tableMeta.getId(), tableMeta.getTableName());
    return tableMeta;
  }

  @Override
  public Optional<GenTableMeta> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(converter::toEntity);
  }

  @Override
  public Optional<GenTableMeta> findByDatasourceIdAndTableName(final Long datasourceId,
                                                               final String tableName) {
    return mapper.selectByDatasourceIdAndTableName(datasourceId, tableName).map(converter::toEntity);
  }

  @Override
  public List<GenTableMeta> findByDatasourceIdOrderByTableNameAsc(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMetaPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMetaPO::getDatasourceId, datasourceId)
        .orderByAsc(GenTableMetaPO::getTableName);
    return converter.toTableEntityList(mapper.selectList(wrapper));
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除表元数据 id={}", id);
  }

  @Override
  public void deleteByDatasourceId(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMetaPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMetaPO::getDatasourceId, datasourceId);
    mapper.delete(wrapper);
    log.info("删除数据源表元数据 datasourceId={}", datasourceId);
  }

  @Override
  public long countByDatasourceId(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMetaPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMetaPO::getDatasourceId, datasourceId);
    return mapper.selectCount(wrapper);
  }
}
