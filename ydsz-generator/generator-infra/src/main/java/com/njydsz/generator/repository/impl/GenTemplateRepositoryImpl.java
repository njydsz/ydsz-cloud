package com.njydsz.generator.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.mapper.GenTemplateMapper;
import com.njydsz.generator.po.GenTemplatePO;
import com.njydsz.generator.repository.GenTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 模板 Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenTemplateRepositoryImpl implements GenTemplateRepository {

  private final GenTemplateMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenTemplate save(final GenTemplate template) {
    if (template.getId() == null) {
      template.setCreatedAt(LocalDateTime.now());
      template.setUpdatedAt(LocalDateTime.now());
      mapper.insert(converter.toPO(template));
    } else {
      template.setUpdatedAt(LocalDateTime.now());
      mapper.updateById(converter.toPO(template));
    }
    log.info("保存模板 groupId={} fileName={}", template.getGroupId(), template.getFileName());
    return template;
  }

  @Override
  public Optional<GenTemplate> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(converter::toEntity);
  }

  @Override
  public Optional<GenTemplate> findByGroupIdAndFileName(final Long groupId, final String fileName) {
    return mapper.selectByGroupIdAndFileName(groupId, fileName).map(converter::toEntity);
  }

  @Override
  public List<GenTemplate> findByGroupIdOrderByFileNameAsc(final Long groupId) {
    LambdaQueryWrapper<GenTemplatePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplatePO::getGroupId, groupId)
        .eq(GenTemplatePO::getIsActive, true)
        .orderByAsc(GenTemplatePO::getFileName);
    return converter.toTemplateEntityList(mapper.selectList(wrapper));
  }

  @Override
  public List<GenTemplate> saveAll(final List<GenTemplate> templates) {
    List<GenTemplatePO> poList = templates.stream()
        .map(converter::toPO)
        .collect(Collectors.toList());
    poList.forEach(mapper::insert);
    log.info("批量保存模板 count={}", poList.size());
    return templates;
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除模板 id={}", id);
  }

  @Override
  public void deleteByGroupId(final Long groupId) {
    LambdaQueryWrapper<GenTemplatePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplatePO::getGroupId, groupId);
    mapper.delete(wrapper);
    log.info("删除分组模板 groupId={}", groupId);
  }

  @Override
  public long countByGroupId(final Long groupId) {
    LambdaQueryWrapper<GenTemplatePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplatePO::getGroupId, groupId);
    return mapper.selectCount(wrapper);
  }
}
