package com.njydsz.generator.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.mapper.GenTemplateGroupMapper;
import com.njydsz.generator.repository.GenTemplateGroupRepository;

/**
 * 模板分组 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTemplateGroupRepositoryImpl implements GenTemplateGroupRepository {

  private final GenTemplateGroupMapper mapper;

  @Override
  public GenTemplateGroup save(final GenTemplateGroup group) {
    if (group.getId() == null) {
      group.setCreatedAt(LocalDateTime.now());
      group.setUpdatedAt(LocalDateTime.now());
      mapper.insert(group);
    } else {
      group.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(group);
    }
    log.info("保存模板分组 id={} name={}", group.getId(), group.getName());
    return group;
  }

  @Override
  public Optional<GenTemplateGroup> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public Optional<GenTemplateGroup> findByName(final String name) {
    LambdaQueryWrapper<GenTemplateGroup> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplateGroup::getName, name).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper));
  }

  @Override
  public List<GenTemplateGroup> findAllByOrderBySortOrderAsc() {
    LambdaQueryWrapper<GenTemplateGroup> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByAsc(GenTemplateGroup::getSortOrder);
    return mapper.selectList(wrapper);
  }

  @Override
  public Optional<GenTemplateGroup> findByIsActiveTrue() {
    LambdaQueryWrapper<GenTemplateGroup> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplateGroup::getActive, true).last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(wrapper));
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
