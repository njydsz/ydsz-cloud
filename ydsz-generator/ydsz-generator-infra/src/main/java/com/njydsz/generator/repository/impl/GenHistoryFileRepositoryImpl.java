package com.njydsz.generator.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.mapper.GenHistoryFileMapper;
import com.njydsz.generator.repository.GenHistoryFileRepository;

/**
 * 生成历史文件明细 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenHistoryFileRepositoryImpl implements GenHistoryFileRepository {

  private final GenHistoryFileMapper mapper;

  @Override
  public GenHistoryFile save(final GenHistoryFile file) {
    mapper.insert(file);
    return file;
  }

  @Override
  public List<GenHistoryFile> saveAll(final List<GenHistoryFile> files) {
    files.forEach(mapper::insert);
    return files;
  }

  @Override
  public List<GenHistoryFile> findByHistoryId(final Long historyId) {
    LambdaQueryWrapper<GenHistoryFile> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenHistoryFile::getHistoryId, historyId);
    return mapper.selectList(wrapper);
  }

  @Override
  public void deleteByHistoryId(final Long historyId) {
    LambdaQueryWrapper<GenHistoryFile> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenHistoryFile::getHistoryId, historyId);
    mapper.delete(wrapper);
  }
}
