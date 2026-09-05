package com.njydsz.generator.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.mapper.GenTemplateGroupMapper;
import com.njydsz.generator.po.GenTemplateGroupPO;
import com.njydsz.generator.repository.GenTemplateGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模板分组 Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTemplateGroupRepositoryImpl implements GenTemplateGroupRepository {

  private final GenTemplateGroupMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenTemplateGroup save(final GenTemplateGroup group) {
    if (group.getId() == null) {
      group.setCreatedAt(LocalDateTime.now());
      group.setUpdatedAt(LocalDateTime.now());
      mapper.insert(converter.toPO(group));
    } else {
      group.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(converter.toPO(group));
    }
    log.info("保存模板分组 id={} name={}", group.getId(), group.getName());
    return group;
  }

  @Override
  public Optional<GenTemplateGroup> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(converter::toEntity);
  }

  @Override
  public Optional<GenTemplateGroup> findByName(final String name) {
    LambdaQueryWrapper<GenTemplateGroupPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplateGroupPO::getName, name).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper)).map(converter::toEntity);
  }

  @Override
  public List<GenTemplateGroup> findAllByOrderBySortOrderAsc() {
    LambdaQueryWrapper<GenTemplateGroupPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(GenTemplateGroupPO::getSortOrder);
    return converter.toGroupEntityList(mapper.selectList(wrapper));
  }

  @Override
  public Optional<GenTemplateGroup> findByIsActiveTrue() {
    LambdaQueryWrapper<GenTemplateGroupPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplateGroupPO::getIsActive, true).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper)).map(converter::toEntity);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除模板分组 id={}", id);
  }

  @Override
  public long count() {
    return mapper.selectCount(null);
  }
}
