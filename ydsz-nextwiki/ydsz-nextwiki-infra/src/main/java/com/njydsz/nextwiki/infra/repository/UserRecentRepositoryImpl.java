package com.njydsz.nextwiki.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.UserRecentDTO;
import com.njydsz.nextwiki.domain.entity.UserRecent;
import com.njydsz.nextwiki.domain.repository.UserRecentRepository;
import com.njydsz.nextwiki.infra.mapper.UserRecentMapper;

/**
 * 用户最近访问仓储实现
 *
 * <p>基于 MyBatis-Plus 实现最近访问的数据访问。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRecentRepositoryImpl implements UserRecentRepository {

  /** 每个用户最多保留的最近访问记录数 */
  private static final int MAX_RECENT_COUNT = 100;

  private final UserRecentMapper userRecentMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiStructMapper mapper;

  /**
   * 保存或更新最近访问记录（已存在则刷新访问时间）。
   *
   * @param dto 最近访问数据传输对象
   * @return 更新记录数
   */
  @Override
  public int saveOrUpdate(UserRecentDTO dto) {
    // 更新已有记录的访问时间
    int updated = userRecentMapper.updateAccessTime(dto.getUserId(), dto.getNodeId(), dto.getAccessType());
    if (updated > 0) {
      return updated;
    }

    // 不存在则新增
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    if (dto.getAccessedAt() == null) {
      dto.setAccessedAt(LocalDateTime.now());
    }
    UserRecent entity = mapper.userRecentToEntity(dto);
    int inserted = userRecentMapper.insert(entity);

    // 清理超出容量限制的旧记录
    if (inserted > 0) {
      cleanupOldRecords(dto.getUserId(), dto.getTenantId());
    }
    return inserted;
  }

  /**
   * 按用户 ID 分页查询最近访问列表（按访问时间倒序）。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param limit 返回数量限制
   * @return 最近访问 DTO 列表
   */
  @Override
  public List<UserRecentDTO> findByUserIdOrderByAccessedAt(
      String userId, String tenantId, int limit) {
    List<UserRecent> entities =
        userRecentMapper.selectByUserIdOrderByAccessedAt(userId, tenantId, limit);
    return entities.stream()
        .map(mapper::userRecentToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 按用户 ID 分页查询最近访问列表。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 最近访问 DTO 列表
   */
  @Override
  public List<UserRecentDTO> findByUserIdWithPage(
      String userId, String tenantId, int offset, int limit) {
    List<UserRecent> entities =
        userRecentMapper.selectByUserIdWithPage(userId, tenantId, offset, limit);
    return entities.stream()
        .map(mapper::userRecentToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 统计用户最近访问记录数。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 访问记录数量
   */
  @Override
  public int countByUserId(String userId, String tenantId) {
    return userRecentMapper.countByUserId(userId, tenantId);
  }

  /**
   * 删除超出容量限制的最早访问记录（LRU 淘汰策略）。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param keepCount 保留的最大记录数
   * @return 实际删除的记录数
   */
  @Override
  public int deleteEarliestRecords(String userId, String tenantId, int keepCount) {
    return userRecentMapper.deleteEarliestRecords(userId, tenantId, keepCount);
  }

  /**
   * 删除指定的最近访问记录。
   *
   * @param userId 用户 ID
   * @param nodeId 文件节点 ID
   * @return 更新记录数
   */
  @Override
  public int deleteByUserIdAndNodeId(String userId, String nodeId) {
    return userRecentMapper.deleteByUserIdAndNodeId(userId, nodeId);
  }

  /**
   * 清理超出容量限制的旧记录。
   *
   * @param userId 用户ID
   * @param tenantId 租户ID
   */
  private void cleanupOldRecords(String userId, String tenantId) {
    int count = countByUserId(userId, tenantId);
    if (count > MAX_RECENT_COUNT) {
      int deleteCount = userRecentMapper.deleteEarliestRecords(userId, tenantId, MAX_RECENT_COUNT);
      log.debug(
          "[UserRecentRepositoryImpl] 清理最近访问记录: userId={}, deleted={}",
          userId,
          deleteCount);
    }
  }
}
