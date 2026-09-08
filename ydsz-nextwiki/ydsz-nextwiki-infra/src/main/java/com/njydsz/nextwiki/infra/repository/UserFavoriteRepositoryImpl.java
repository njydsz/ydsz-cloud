package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.UserFavoriteDTO;
import com.njydsz.nextwiki.domain.entity.UserFavorite;
import com.njydsz.nextwiki.domain.repository.UserFavoriteRepository;
import com.njydsz.nextwiki.infra.mapper.UserFavoriteMapper;

/**
 * 用户收藏夹仓储实现
 *
 * <p>基于 MyBatis-Plus 实现收藏夹的数据访问。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserFavoriteRepositoryImpl implements UserFavoriteRepository {

  private final UserFavoriteMapper userFavoriteMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiStructMapper mapper;

  /**
   * 保存收藏记录。
   *
   * @param dto 收藏数据传输对象
   * @return 更新记录数
   */
  @Override
  public int save(UserFavoriteDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    UserFavorite entity = mapper.userFavoriteToEntity(dto);
    return userFavoriteMapper.insert(entity);
  }

  /**
   * 取消收藏（物理删除记录）。
   *
   * @param userId 用户 ID
   * @param nodeId 文件节点 ID
   * @return 更新记录数
   */
  @Override
  public int deleteByUserIdAndNodeId(String userId, String nodeId) {
    // 直接删除记录（物理删除，非逻辑删除）
    return userFavoriteMapper.deleteByUserIdAndNodeId(userId, nodeId);
  }

  /**
   * 按用户 ID 查询收藏列表。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 收藏 DTO 列表
   */
  @Override
  public List<UserFavoriteDTO> findByUserId(String userId, String tenantId) {
    List<UserFavorite> entities =
        userFavoriteMapper.selectByUserId(userId, tenantId);
    return entities.stream()
        .map(mapper::userFavoriteToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 按用户 ID 分页查询收藏列表。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 收藏 DTO 列表
   */
  @Override
  public List<UserFavoriteDTO> findByUserIdWithPage(
      String userId, String tenantId, int offset, int limit) {
    List<UserFavorite> entities =
        userFavoriteMapper.selectByUserIdWithPage(userId, tenantId, offset, limit);
    return entities.stream()
        .map(mapper::userFavoriteToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 统计用户收藏数量。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 收藏数量
   */
  @Override
  public int countByUserId(String userId, String tenantId) {
    return userFavoriteMapper.countByUserId(userId, tenantId);
  }

  /**
   * 查询用户当前最大的收藏排序号（新增收藏时计算 sort 值）。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 最大排序号（无记录时返回 0）
   */
  @Override
  public int findMaxsort(String userId, String tenantId) {
    return userFavoriteMapper.selectMaxsort(userId, tenantId);
  }

  /**
   * 判断用户是否已收藏指定节点。
   *
   * @param userId 用户 ID
   * @param nodeId 文件节点 ID
   * @param tenantId 租户 ID
   * @return 是否已收藏
   */
  @Override
  public boolean existsByUserIdAndNodeId(String userId, String nodeId, String tenantId) {
    return userFavoriteMapper.existsByUserIdAndNodeId(userId, nodeId, tenantId);
  }

  /**
   * 更新收藏的排序号（拖拽排序）。
   *
   * @param userId 用户 ID
   * @param nodeId 文件节点 ID
   * @param sort 新排序号
   * @return 更新记录数
   */
  @Override
  public int updatesort(String userId, String nodeId, int sort) {
    return userFavoriteMapper.updatesort(userId, nodeId, sort);
  }
}
