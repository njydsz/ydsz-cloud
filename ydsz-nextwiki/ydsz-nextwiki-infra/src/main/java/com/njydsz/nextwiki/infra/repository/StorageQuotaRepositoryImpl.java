package com.njydsz.nextwiki.infra.repository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.StorageQuota;
import com.njydsz.nextwiki.infra.mapper.StorageQuotaMapper;

/**
 * 存储配额仓储实现
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
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StorageQuotaRepositoryImpl implements StorageQuotaRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final StorageQuotaMapper storageQuotaMapper;
  private final NextwikiConverter converter;

  @Override
  public StorageQuotaVO save(StorageQuotaDTO dto) {
    StorageQuota entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    storageQuotaMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public Optional<StorageQuotaVO> findById(String id) {
    return Optional.ofNullable(storageQuotaMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<StorageQuotaVO> findByScope(String scopeType, String scopeId) {
    return Optional.ofNullable(storageQuotaMapper.selectByScope(scopeType, scopeId))
        .map(converter::entityToVO);
  }

  @Override
  public int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }

  @Override
  public int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }
}
