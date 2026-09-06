package com.njydsz.generator.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.mapper.GenColumnMetaMapper;
import com.njydsz.generator.repository.GenColumnMetaRepository;

/**
 * 列元数据 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 * 批量插入通过 SqlSession BATCH 模式执行，减少 DB round-trips。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
public class GenColumnMetaRepositoryImpl implements GenColumnMetaRepository {

  private static final int BATCH_FLUSH_SIZE = 100;

  private final GenColumnMetaMapper mapper;
  private final SqlSessionFactory sqlSessionFactory;

  /**
   * 构造器。
   *
   * @param mapper           列元数据 Mapper
   * @param sqlSessionFactory MyBatis SqlSession 工厂（用于批量模式）
   */
  public GenColumnMetaRepositoryImpl(
      GenColumnMetaMapper mapper,
      SqlSessionFactory sqlSessionFactory) {
    this.mapper = mapper;
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public GenColumnMeta save(final GenColumnMeta columnMeta) {
    if (columnMeta.getId() == null) {
      mapper.insert(columnMeta);
    } else {
      mapper.updateById(columnMeta);
    }
    return columnMeta;
  }

  @Override
  public List<GenColumnMeta> batchSave(final List<GenColumnMeta> columns) {
    if (columns == null || columns.isEmpty()) {
      return columns;
    }
    long start = System.currentTimeMillis();
    try (SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
      GenColumnMetaMapper batchMapper = batchSession.getMapper(GenColumnMetaMapper.class);
      int count = 0;
      for (GenColumnMeta column : columns) {
        batchMapper.insert(column);
        count++;
        if (count % BATCH_FLUSH_SIZE == 0) {
          batchSession.flushStatements();
        }
      }
      batchSession.commit();
      log.info("批量保存列元数据 count={} cost={}ms",
          columns.size(), System.currentTimeMillis() - start);
    }
    return columns;
  }

  @Override
  public GenColumnMeta findById(final Long id) {
    return mapper.selectById(id);
  }

  @Override
  public List<GenColumnMeta> findByTableMetaIdOrderByIdAsc(final Long tableMetaId) {
    LambdaQueryWrapper<GenColumnMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenColumnMeta::getTableMetaId, tableMetaId)
        .orderByAsc(GenColumnMeta::getId);
    return mapper.selectList(wrapper);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
  }

  @Override
  public void deleteByTableMetaId(final Long tableMetaId) {
    LambdaQueryWrapper<GenColumnMeta> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenColumnMeta::getTableMetaId, tableMetaId);
    mapper.delete(wrapper);
  }
}
