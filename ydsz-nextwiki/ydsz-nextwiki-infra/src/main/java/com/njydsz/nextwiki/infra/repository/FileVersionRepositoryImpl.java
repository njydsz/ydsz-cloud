package com.njydsz.nextwiki.infra.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.query.FileVersionQuery;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.infra.mapper.FileVersionMapper;

/**
 * 文件版本仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileVersionRepositoryImpl implements FileVersionRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final FileVersionMapper fileVersionMapper;
  private final NextwikiConverter converter;

  @Override
  public FileVersionVO save(FileVersionDTO dto) {
    FileVersion entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileVersionMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public void update(FileVersionDTO dto) {
    FileVersion entity = converter.dtoToEntityWithId(dto);
    fileVersionMapper.updateById(entity);
  }

  @Override
  public List<FileVersionVO> findByFileNodeId(String fileNodeId) {
    return converter.fileVersionListToVO(fileVersionMapper.selectByFileNodeId(fileNodeId));
  }

  @Override
  public Optional<FileVersionVO> findByFileNodeIdAndVersion(FileVersionQuery query) {
    return Optional.ofNullable(
            fileVersionMapper.selectByVersion(
                query.getFileNodeId(), query.getVersionNumber()))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<FileVersionVO> findActiveVersion(String fileNodeId) {
    return Optional.ofNullable(fileVersionMapper.selectActiveVersion(fileNodeId))
        .map(converter::entityToVO);
  }

  @Override
  public void setActiveVersion(String fileNodeId, Integer versionNumber) {
    fileVersionMapper.setActiveVersion(fileNodeId, versionNumber);
  }

  @Override
  public void deleteById(String id) {
    fileVersionMapper.deleteById(id);
  }

  @Override
  public int deleteExcessVersions(String fileNodeId, int keepCount) {
    List<FileVersion> excessVersions =
        fileVersionMapper.selectOldestVersions(
            fileNodeId, fileVersionMapper.countByFileNodeId(fileNodeId) - keepCount);

    if (excessVersions == null || excessVersions.isEmpty()) {
      return 0;
    }

    List<String> ids = new ArrayList<>(16);
    for (FileVersion v : excessVersions) {
      ids.add(v.getId());
    }

    if (!ids.isEmpty()) {
      fileVersionMapper.deleteBatchIds(ids);
    }

    log.info(
        "[FileVersionRepositoryImpl] 已删除 {} 个超出版本上限的旧版本，"
            + "fileNodeId={}, keepCount={}",
        ids.size(),
        fileNodeId,
        keepCount);
    return ids.size();
  }

  @Override
  public int countByFileNodeId(String fileNodeId) {
    return fileVersionMapper.countByFileNodeId(fileNodeId);
  }

  @Override
  public List<FileVersionVO> findOldestVersions(String fileNodeId, int limit) {
    return converter.fileVersionListToVO(fileVersionMapper.selectOldestVersions(fileNodeId, limit));
  }
}
