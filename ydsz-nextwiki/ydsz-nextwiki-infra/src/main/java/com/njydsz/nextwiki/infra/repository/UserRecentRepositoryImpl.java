package com.njydsz.nextwiki.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.UserRecentDTO;
import com.njydsz.nextwiki.domain.repository.UserRecentRepository;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.UserRecentDO;
import com.njydsz.nextwiki.infra.mapper.UserRecentMapper;

/**
 * 用户最近访问仓储实现
 *
 * <p>基于 MyBatis-Plus 实现最近访问的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRecentRepositoryImpl implements UserRecentRepository {

  /** 每个用户最多保留的最近访问记录数 */
  private static final int MAX_RECENT_COUNT = 100;

  private final UserRecentMapper userRecentMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

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
    UserRecentDO entity = nextwikiConverter.toUserRecentDO(dto);
    int inserted = userRecentMapper.insert(entity);

    // 清理超出容量限制的旧记录
    if (inserted > 0) {
      cleanupOldRecords(dto.getUserId(), dto.getTenantId());
    }
    return inserted;
  }

  @Override
  public List<UserRecentDTO> findByUserIdOrderByAccessedAt(
      String userId, String tenantId, int limit) {
    List<UserRecentDO> entities =
        userRecentMapper.selectByUserIdOrderByAccessedAt(userId, tenantId, limit);
    return entities.stream()
        .map(nextwikiConverter::toUserRecentDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserRecentDTO> findByUserIdWithPage(
      String userId, String tenantId, int offset, int limit) {
    List<UserRecentDO> entities =
        userRecentMapper.selectByUserIdWithPage(userId, tenantId, offset, limit);
    return entities.stream()
        .map(nextwikiConverter::toUserRecentDTO)
        .collect(Collectors.toList());
  }

  @Override
  public int countByUserId(String userId, String tenantId) {
    return userRecentMapper.countByUserId(userId, tenantId);
  }

  @Override
  public int deleteEarliestRecords(String userId, String tenantId, int keepCount) {
    return userRecentMapper.deleteEarliestRecords(userId, tenantId, keepCount);
  }

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
