package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.UserFavoriteDTO;
import com.njydsz.nextwiki.domain.repository.UserFavoriteRepository;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.UserFavorite;
import com.njydsz.nextwiki.infra.mapper.UserFavoriteMapper;

/**
 * 用户收藏夹仓储实现
 *
 * <p>基于 MyBatis-Plus 实现收藏夹的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserFavoriteRepositoryImpl implements UserFavoriteRepository {

  private final UserFavoriteMapper userFavoriteMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

  @Override
  public int save(UserFavoriteDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    UserFavorite entity = nextwikiConverter.toUserFavorite(dto);
    return userFavoriteMapper.insert(entity);
  }

  @Override
  public int deleteByUserIdAndNodeId(String userId, String nodeId) {
    // 直接删除记录（物理删除，非逻辑删除）
    return userFavoriteMapper.deleteByUserIdAndNodeId(userId, nodeId);
  }

  @Override
  public List<UserFavoriteDTO> findByUserId(String userId, String tenantId) {
    List<UserFavorite> entities =
        userFavoriteMapper.selectByUserId(userId, tenantId);
    return entities.stream()
        .map(nextwikiConverter::toUserFavoriteDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserFavoriteDTO> findByUserIdWithPage(
      String userId, String tenantId, int offset, int limit) {
    List<UserFavorite> entities =
        userFavoriteMapper.selectByUserIdWithPage(userId, tenantId, offset, limit);
    return entities.stream()
        .map(nextwikiConverter::toUserFavoriteDTO)
        .collect(Collectors.toList());
  }

  @Override
  public int countByUserId(String userId, String tenantId) {
    return userFavoriteMapper.countByUserId(userId, tenantId);
  }

  @Override
  public int findMaxSortOrder(String userId, String tenantId) {
    return userFavoriteMapper.selectMaxSortOrder(userId, tenantId);
  }

  @Override
  public boolean existsByUserIdAndNodeId(String userId, String nodeId, String tenantId) {
    return userFavoriteMapper.existsByUserIdAndNodeId(userId, nodeId, tenantId);
  }

  @Override
  public int updateSortOrder(String userId, String nodeId, int sortOrder) {
    return userFavoriteMapper.updateSortOrder(userId, nodeId, sortOrder);
  }
}
