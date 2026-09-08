package com.njydsz.nextwiki.infra.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.query.FileVersionQuery;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.infra.mapper.FileVersionMapper;

/**
 * 文件版本仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileVersionRepositoryImpl implements FileVersionRepository {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final FileVersionMapper fileVersionMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 保存文件版本记录。
   *
   * @param dto 版本数据传输对象
   * @return 保存后的版本视图对象
   */
  @Override
  public FileVersionVO save(FileVersionDTO dto) {
    FileVersion entity = mapper.fileVersionToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileVersionMapper.insert(entity);
    return mapper.fileVersionToVO(entity);
  }

  /**
   * 更新版本记录（仅更新描述、活跃状态等元数据）。
   *
   * @param dto 版本数据传输对象
   */
  @Override
  public void update(FileVersionDTO dto) {
    FileVersion entity = mapper.fileVersionToEntity(dto);
    fileVersionMapper.updateById(entity);
  }

  /**
   * 按文件节点 ID 查询全部版本历史（倒序）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 版本视图对象列表
   */
  @Override
  public List<FileVersionVO> findByFileNodeId(String fileNodeId) {
    return mapper.fileVersionListToVO(fileVersionMapper.selectByFileNodeId(fileNodeId));
  }

  /**
   * 按文件节点 ID 与版本号精确查询单个版本。
   *
   * @param query 版本查询条件
   * @return 版本视图对象（可能为空）
   */
  @Override
  public Optional<FileVersionVO> findByFileNodeIdAndVersion(FileVersionQuery query) {
    return Optional.ofNullable(
            fileVersionMapper.selectByVersion(
                query.getFileNodeId(), query.getVersionNumber()))
        .map(mapper::fileVersionToVO);
  }

  /**
   * 查询文件的当前活跃版本。
   *
   * @param fileNodeId 文件节点 ID
   * @return 活跃版本视图对象（可能为空）
   */
  @Override
  public Optional<FileVersionVO> findActiveVersion(String fileNodeId) {
    return Optional.ofNullable(fileVersionMapper.selectActiveVersion(fileNodeId))
        .map(mapper::fileVersionToVO);
  }

  /**
   * 设置指定版本为活跃版本（版本回滚时使用）。
   *
   * @param fileNodeId 文件节点 ID
   * @param versionNumber 要激活的版本号
   */
  @Override
  public void setActiveVersion(String fileNodeId, Integer versionNumber) {
    fileVersionMapper.setActiveVersion(fileNodeId, versionNumber);
  }

  /**
   * 物理删除指定版本记录。
   *
   * @param id 版本 ID
   */
  @Override
  public void deleteById(String id) {
    fileVersionMapper.deleteById(id);
  }

  /**
   * 删除超出保留数量的旧版本。
   *
   * @param fileNodeId 文件节点 ID
   * @param keepCount 保留的最新版本数量
   * @return 实际删除的版本数量
   */
  @Override
  public int deleteExcessVersions(String fileNodeId, int keepCount) {
    List<FileVersion> excessVersions =
        fileVersionMapper.selectOldestVersions(
            fileNodeId, fileVersionMapper.countByFileNodeId(fileNodeId) - keepCount);

    if (excessVersions == null || excessVersions.isEmpty()) {
      return 0;
    }

    List<String> ids = new ArrayList<>(COLLECTION_CAPACITY);
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

  /**
   * 统计文件的版本总数。
   *
   * @param fileNodeId 文件节点 ID
   * @return 版本数量
   */
  @Override
  public int countByFileNodeId(String fileNodeId) {
    return fileVersionMapper.countByFileNodeId(fileNodeId);
  }

  /**
   * 查询最早的 N 个版本。
   *
   * @param fileNodeId 文件节点 ID
   * @param limit 返回数量限制
   * @return 版本视图对象列表
   */
  @Override
  public List<FileVersionVO> findOldestVersions(String fileNodeId, int limit) {
    return mapper.fileVersionListToVO(fileVersionMapper.selectOldestVersions(fileNodeId, limit));
  }
}
