package com.njydsz.generator.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.mapper.GenTableMetaMapper;
import com.njydsz.generator.repository.GenTableMetaRepository;

/**
 * 表元数据 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTableMetaRepositoryImpl implements GenTableMetaRepository {

  private final GenTableMetaMapper mapper;

  @Override
  public GenTableMeta save(final GenTableMeta tableMeta) {
    if (tableMeta.getId() == null) {
      tableMeta.setCachedAt(LocalDateTime.now());
    } else {
      tableMeta.setCachedAt(LocalDateTime.now());
    }
    if (tableMeta.getId() == null) {
      mapper.insert(tableMeta);
    } else {
      mapper.updateById(tableMeta);
    }
    log.info("保存表元数据 id={} table={}", tableMeta.getId(), tableMeta.getTableName());
    return tableMeta;
  }

  @Override
  public Optional<GenTableMeta> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public Optional<GenTableMeta> findByDatasourceIdAndTableName(final Long datasourceId,
                                                               final String tableName) {
    return mapper.selectByDatasourceIdAndTableName(datasourceId, tableName);
  }

  @Override
  public List<GenTableMeta> findByDatasourceIdOrderByTableNameAsc(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMeta::getDatasourceId, datasourceId)
        .orderByAsc(GenTableMeta::getTableName);
    return mapper.selectList(wrapper);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除表元数据 id={}", id);
  }

  @Override
  public void deleteByDatasourceId(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMeta::getDatasourceId, datasourceId);
    mapper.delete(wrapper);
    log.info("删除数据源表元数据 datasourceId={}", datasourceId);
  }

  @Override
  public long countByDatasourceId(final Long datasourceId) {
    LambdaQueryWrapper<GenTableMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTableMeta::getDatasourceId, datasourceId);
    return mapper.selectCount(wrapper);
  }
}
