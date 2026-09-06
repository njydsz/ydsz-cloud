package com.njydsz.generator.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.mapper.GenTemplateMapper;
import com.njydsz.generator.repository.GenTemplateRepository;

/**
 * 模板 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 * 批量插入通过 SqlSession BATCH 模式执行，减少 DB round-trips。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
public class GenTemplateRepositoryImpl implements GenTemplateRepository {

  /** 批量刷新条数阈值。 */
  private static final int BATCH_FLUSH_SIZE = 100;

  private final GenTemplateMapper mapper;
  private final SqlSessionFactory sqlSessionFactory;

  /**
   * 构造器。
   *
   * @param mapper           模板 Mapper
   * @param sqlSessionFactory MyBatis SqlSession 工厂（用于批量模式）
   */
  public GenTemplateRepositoryImpl(
      GenTemplateMapper mapper,
      SqlSessionFactory sqlSessionFactory) {
    this.mapper = mapper;
    this.sqlSessionFactory = sqlSessionFactory;
  }

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
    LambdaQueryWrapper<GenTemplate> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenTemplate::getGroupId, groupId)
        .eq(GenTemplate::getFileName, fileName);
    return Optional.ofNullable(mapper.selectOne(wrapper));
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
  public List<GenTemplate> batchSave(final List<GenTemplate> templates) {
    if (templates == null || templates.isEmpty()) {
      return templates;
    }
    LocalDateTime now = LocalDateTime.now();
    long start = System.currentTimeMillis();
    try (SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
      GenTemplateMapper batchMapper = batchSession.getMapper(GenTemplateMapper.class);
      int count = 0;
      for (GenTemplate tpl : templates) {
        tpl.setCreatedAt(now);
        tpl.setUpdatedAt(now);
        batchMapper.insert(tpl);
        count++;
        if (count % BATCH_FLUSH_SIZE == 0) {
          batchSession.flushStatements();
        }
      }
      batchSession.commit();
      log.info("批量保存模板 count={} cost={}ms",
          templates.size(), System.currentTimeMillis() - start);
    }
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
