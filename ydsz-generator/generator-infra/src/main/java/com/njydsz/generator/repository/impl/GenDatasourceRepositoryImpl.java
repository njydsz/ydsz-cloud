package com.njydsz.generator.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.mapper.GenDatasourceMapper;
import com.njydsz.generator.po.GenDatasourcePO;
import com.njydsz.generator.repository.GenDatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 数据源配置 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，通过 MapStruct Converter 实现 PO ↔ Entity 转换。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenDatasourceRepositoryImpl implements GenDatasourceRepository {

  private final GenDatasourceMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenDatasource save(final GenDatasource datasource) {
    if (datasource.getId() == null) {
      datasource.setCreatedAt(LocalDateTime.now());
      datasource.setUpdatedAt(LocalDateTime.now());
      mapper.insert(converter.toPO(datasource));
    } else {
      datasource.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(converter.toPO(datasource));
    }
    log.info("保存数据源成功 id={} name={}", datasource.getId(), datasource.getName());
    return datasource;
  }

  @Override
  public Optional<GenDatasource> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(converter::toEntity);
  }

  @Override
  public List<GenDatasource> findAll() {
    LambdaQueryWrapper<GenDatasourcePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(GenDatasourcePO::getCreatedAt);
    return converter.toDatasourceEntityList(mapper.selectList(wrapper));
  }

  @Override
  public Optional<GenDatasource> findByIsDefaultTrue() {
    LambdaQueryWrapper<GenDatasourcePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenDatasourcePO::getIsDefault, true).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper)).map(converter::toEntity);
  }

  @Override
  public Optional<GenDatasource> findByName(final String name) {
    LambdaQueryWrapper<GenDatasourcePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenDatasourcePO::getName, name).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper)).map(converter::toEntity);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除数据源 id={}", id);
  }

  @Override
  public long count() {
    return mapper.selectCount(null);
  }
}
