package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.SpaceDO;
import com.njydsz.nextwiki.infra.mapper.SpaceMapper;

/**
 * 知识库空间仓储实现
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceRepositoryImpl implements SpaceRepository {

  private final SpaceMapper spaceMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

  @Override
  public int save(SpaceDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceDO entity = nextwikiConverter.toSpaceDO(dto);
    return spaceMapper.insert(entity);
  }

  @Override
  public int update(SpaceDTO dto) {
    SpaceDO entity = nextwikiConverter.toSpaceDO(dto);
    return spaceMapper.updateById(entity);
  }

  @Override
  public Optional<SpaceDTO> findById(String id) {
    SpaceDO entity = spaceMapper.selectById(id);
    return Optional.ofNullable(entity).map(nextwikiConverter::toSpaceDTO);
  }

  @Override
  public Optional<SpaceDTO> findByTenantIdAndName(String tenantId, String name) {
    SpaceDO entity = spaceMapper.selectByTenantIdAndName(tenantId, name);
    return Optional.ofNullable(entity).map(nextwikiConverter::toSpaceDTO);
  }

  @Override
  public List<SpaceDTO> findByTenantId(String tenantId) {
    List<SpaceDO> entities = spaceMapper.selectByTenantId(tenantId);
    return entities.stream()
        .map(nextwikiConverter::toSpaceDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<SpaceDTO> findByTenantIdWithPage(String tenantId, int offset, int limit) {
    List<SpaceDO> entities = spaceMapper.selectByTenantIdWithPage(tenantId, offset, limit);
    return entities.stream()
        .map(nextwikiConverter::toSpaceDTO)
        .collect(Collectors.toList());
  }

  @Override
  public int countByTenantId(String tenantId) {
    return spaceMapper.countByTenantId(tenantId);
  }

  @Override
  public int deleteById(String id) {
    return spaceMapper.deleteById(id);
  }
}
