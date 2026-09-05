package com.njydsz.generator.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.mapper.GenTemplateMapper;
import com.njydsz.generator.repository.GenTemplateRepository;

/**
 * 模板 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTemplateRepositoryImpl implements GenTemplateRepository {

  private final GenTemplateMapper mapper;

  @Override
  public GenTemplate save(final GenTemplate template) {
    if (template.getId() == null) {
      template.setCreatedAt(LocalDateTime.now());
      template.setUpdatedAt(LocalDateTime.now());
      mapper.insert(template);
    } else {
      template.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(template);
    }
    log.info("保存模板 groupId={} fileName={}", template.getGroupId(), template.getFileName());
    return template;
  }

  @Override
  public Optional<GenTemplate> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public Optional<GenTemplate> findByGroupIdAndFileName(final Long groupId, final String fileName) {
    return mapper.selectByGroupIdAndFileName(groupId, fileName);
  }

  @Override
  public List<GenTemplate> findByGroupIdOrderByFileNameAsc(final Long groupId) {
    LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplate::getGroupId, groupId)
        .eq(GenTemplate::getActive, true)
        .orderByAsc(GenTemplate::getFileName);
    return mapper.selectList(wrapper);
  }

  @Override
  public List<GenTemplate> saveAll(final List<GenTemplate> templates) {
    templates.forEach(mapper::insert);
    log.info("批量保存模板 count={}", templates.size());
    return templates;
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除模板 id={}", id);
  }

  @Override
  public void deleteByGroupId(final Long groupId) {
    LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplate::getGroupId, groupId);
    mapper.delete(wrapper);
    log.info("删除分组模板 groupId={}", groupId);
  }

  @Override
  public long countByGroupId(final Long groupId) {
    LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplate::getGroupId, groupId);
    return mapper.selectCount(wrapper);
  }
}
