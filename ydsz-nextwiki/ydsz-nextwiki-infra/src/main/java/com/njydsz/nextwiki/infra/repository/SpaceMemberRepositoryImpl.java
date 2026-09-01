package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.repository.SpaceMemberRepository;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.SpaceMember;
import com.njydsz.nextwiki.infra.mapper.SpaceMemberMapper;

/**
 * 空间成员仓储实现
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceMemberRepositoryImpl implements SpaceMemberRepository {

  private final SpaceMemberMapper spaceMemberMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

  @Override
  public int save(SpaceMemberDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceMember entity = nextwikiConverter.toSpaceMember(dto);
    return spaceMemberMapper.insert(entity);
  }

  @Override
  public int updateRole(String spaceId, String userId, String role) {
    return spaceMemberMapper.updateRole(spaceId, userId, role);
  }

  @Override
  public int deleteBySpaceIdAndUserId(String spaceId, String userId) {
    return spaceMemberMapper.deleteBySpaceIdAndUserId(spaceId, userId);
  }

  @Override
  public Optional<SpaceMemberDTO> findBySpaceIdAndUserId(String spaceId, String userId) {
    SpaceMember entity = spaceMemberMapper.selectBySpaceIdAndUserId(spaceId, userId);
    return Optional.ofNullable(entity).map(nextwikiConverter::toSpaceMemberDTO);
  }

  @Override
  public List<SpaceMemberDTO> findBySpaceId(String spaceId) {
    List<SpaceMember> entities = spaceMemberMapper.selectBySpaceId(spaceId);
    return entities.stream()
        .map(nextwikiConverter::toSpaceMemberDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<SpaceMemberDTO> findByUserId(String userId) {
    List<SpaceMember> entities = spaceMemberMapper.selectByUserId(userId);
    return entities.stream()
        .map(nextwikiConverter::toSpaceMemberDTO)
        .collect(Collectors.toList());
  }

  @Override
  public int countBySpaceId(String spaceId) {
    return spaceMemberMapper.countBySpaceId(spaceId);
  }

  @Override
  public boolean existsBySpaceIdAndUserId(String spaceId, String userId) {
    return spaceMemberMapper.existsBySpaceIdAndUserId(spaceId, userId);
  }
}
