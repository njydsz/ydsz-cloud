package com.njydsz.nextwiki.infra.repository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.infra.mapper.StorageQuotaMapper;

/**
 * 存储配额仓储实现
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
public class StorageQuotaRepositoryImpl implements StorageQuotaRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final StorageQuotaMapper storageQuotaMapper;
  private final NextwikiStructMapper mapper;

  @Override
  public StorageQuotaVO save(StorageQuotaDTO dto) {
    StorageQuota entity = mapper.storageQuotaToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    storageQuotaMapper.insert(entity);
    return mapper.storageQuotaToVO(entity);
  }

  /**
   * 按配额记录 ID 查询。
   *
   * @param id 配额记录 ID
   * @return 配额视图对象（可能为空）
   */
  @Override
  public Optional<StorageQuotaVO> findById(String id) {
    return Optional.ofNullable(storageQuotaMapper.selectById(id)).map(mapper::storageQuotaToVO);
  }

  /**
   * 按作用域类型和 ID 查询配额记录。
   *
   * @param scopeType 作用域类型（user/tenant/project）
   * @param scopeId 作用域 ID
   * @return 配额视图对象（可能为空）
   */
  @Override
  public Optional<StorageQuotaVO> findByScope(String scopeType, String scopeId) {
    return Optional.ofNullable(storageQuotaMapper.selectByScope(scopeType, scopeId))
        .map(mapper::storageQuotaToVO);
  }

  /**
   * 原子增加配额使用量（上传文件时调用）。
   *
   * @param scopeType 作用域类型（user/tenant/project）
   * @param scopeId 作用域 ID
   * @param bytesDelta 文件增量大小（字节）
   * @param fileCountDelta 文件数增量
   * @return 更新记录数
   */
  @Override
  public int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }

  /**
   * 原子减少配额使用量（删除文件时调用）。
   *
   * @param scopeType 作用域类型（user/tenant/project）
   * @param scopeId 作用域 ID
   * @param bytesDelta 文件减量大小（字节）
   * @param fileCountDelta 文件数减量
   * @return 更新记录数
   */
  @Override
  public int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }
}
