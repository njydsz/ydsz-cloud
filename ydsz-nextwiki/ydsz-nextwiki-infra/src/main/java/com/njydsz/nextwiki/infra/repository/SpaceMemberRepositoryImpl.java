package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.entity.SpaceMember;
import com.njydsz.nextwiki.domain.repository.SpaceMemberRepository;
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
  private final NextwikiStructMapper mapper;

  /**
   * 保存空间成员记录。
   *
   * @param dto 空间成员数据传输对象
   * @return 更新记录数
   */
  @Override
  public int save(SpaceMemberDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceMember entity = mapper.spaceMemberToEntity(dto);
    return spaceMemberMapper.insert(entity);
  }

  /**
   * 更新空间成员的角色。
   *
   * @param spaceId 空间 ID
   * @param userId 用户 ID
   * @param role 新角色
   * @return 更新记录数
   */
  @Override
  public int updateRole(String spaceId, String userId, String role) {
    return spaceMemberMapper.updateRole(spaceId, userId, role);
  }

  /**
   * 移除空间成员。
   *
   * @param spaceId 空间 ID
   * @param userId 用户 ID
   * @return 更新记录数
   */
  @Override
  public int deleteBySpaceIdAndUserId(String spaceId, String userId) {
    return spaceMemberMapper.deleteBySpaceIdAndUserId(spaceId, userId);
  }

  /**
   * 按空间 ID + 用户 ID 查询单个成员。
   *
   * @param spaceId 空间 ID
   * @param userId 用户 ID
   * @return 空间成员 DTO（可能为空）
   */
  @Override
  public Optional<SpaceMemberDTO> findBySpaceIdAndUserId(String spaceId, String userId) {
    SpaceMember entity = spaceMemberMapper.selectBySpaceIdAndUserId(spaceId, userId);
    return Optional.ofNullable(entity).map(mapper::spaceMemberToDTO);
  }

  /**
   * 查询空间的所有成员。
   *
   * @param spaceId 空间 ID
   * @return 空间成员 DTO 列表
   */
  @Override
  public List<SpaceMemberDTO> findBySpaceId(String spaceId) {
    List<SpaceMember> entities = spaceMemberMapper.selectBySpaceId(spaceId);
    return entities.stream()
        .map(mapper::spaceMemberToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 查询用户的所有空间成员关系。
   *
   * @param userId 用户 ID
   * @return 空间成员 DTO 列表
   */
  @Override
  public List<SpaceMemberDTO> findByUserId(String userId) {
    List<SpaceMember> entities = spaceMemberMapper.selectByUserId(userId);
    return entities.stream()
        .map(mapper::spaceMemberToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 统计空间的成员数量。
   *
   * @param spaceId 空间 ID
   * @return 成员数量
   */
  @Override
  public int countBySpaceId(String spaceId) {
    return spaceMemberMapper.countBySpaceId(spaceId);
  }

  /**
   * 判断用户是否是空间的成员。
   *
   * @param spaceId 空间 ID
   * @param userId 用户 ID
   * @return 该用户是否已在空间中
   */
  @Override
  public boolean existsBySpaceIdAndUserId(String spaceId, String userId) {
    return spaceMemberMapper.existsBySpaceIdAndUserId(spaceId, userId);
  }
}
