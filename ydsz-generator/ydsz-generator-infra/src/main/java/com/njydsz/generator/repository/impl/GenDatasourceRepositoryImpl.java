package com.njydsz.generator.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.mapper.GenDatasourceMapper;
import com.njydsz.generator.repository.GenDatasourceRepository;

/**
 * 数据源配置 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenDatasourceRepositoryImpl implements GenDatasourceRepository {

  private final GenDatasourceMapper mapper;

  @Override
  public GenDatasource save(final GenDatasource datasource) {
    if (datasource.getId() == null) {
      datasource.setCreatedAt(LocalDateTime.now());
      datasource.setUpdatedAt(LocalDateTime.now());
      mapper.insert(datasource);
    } else {
      datasource.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(datasource);
    }
    log.info("保存数据源成功 id={} name={}", datasource.getId(), datasource.getName());
    return datasource;
  }

  @Override
  public Optional<GenDatasource> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public List<GenDatasource> findAll() {
    LambdaQueryWrapper<GenDatasource> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(GenDatasource::getCreatedAt);
    return mapper.selectList(wrapper);
  }

  @Override
  public Optional<GenDatasource> findByDefaultFlagTrue() {
    LambdaQueryWrapper<GenDatasource> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenDatasource::getDefaultFlag, true).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper));
  }

  @Override
  public Optional<GenDatasource> findByName(final String name) {
    LambdaQueryWrapper<GenDatasource> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenDatasource::getName, name).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper));
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
