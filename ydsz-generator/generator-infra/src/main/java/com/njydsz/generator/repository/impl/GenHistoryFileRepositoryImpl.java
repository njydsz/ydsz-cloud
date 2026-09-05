package com.njydsz.generator.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.generator.converter.GeneratorConverter;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.mapper.GenHistoryFileMapper;
import com.njydsz.generator.po.GenHistoryFilePO;
import com.njydsz.generator.repository.GenHistoryFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 生成历史文件明细 Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenHistoryFileRepositoryImpl implements GenHistoryFileRepository {

  private final GenHistoryFileMapper mapper;
  private final GeneratorConverter converter;

  @Override
  public GenHistoryFile save(final GenHistoryFile file) {
    mapper.insert(converter.toPO(file));
    return file;
  }

  @Override
  public List<GenHistoryFile> saveAll(final List<GenHistoryFile> files) {
    files.stream().map(converter::toPO).forEach(mapper::insert);
    return files;
  }

  @Override
  public List<GenHistoryFile> findByHistoryId(final Long historyId) {
    LambdaQueryWrapper<GenHistoryFilePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenHistoryFilePO::getHistoryId, historyId);
    return converter.toFileEntityList(mapper.selectList(wrapper));
  }

  @Override
  public void deleteByHistoryId(final Long historyId) {
    LambdaQueryWrapper<GenHistoryFilePO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(GenHistoryFilePO::getHistoryId, historyId);
    mapper.delete(wrapper);
  }
}
