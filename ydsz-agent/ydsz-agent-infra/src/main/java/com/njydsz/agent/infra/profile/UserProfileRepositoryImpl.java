package com.njydsz.agent.infra.profile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.UserProfile;
import com.njydsz.agent.domain.profile.UserProfileRepository;
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.UserProfilePO;

/**
 * 用户画像 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 {@link UserProfileRepository} 接口，
 * 以 userId 作为主键（业务 ID，非自增）。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 实体。
 *
 * <p><b>DDD 分层：</b> domain 层 UserProfile 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

  private final UserProfileMapper userProfileMapper;

  private final AgentPoConverter poConverter;

  @Override
  public Optional<UserProfile> findByUserId(String userId) {
    UserProfilePO po = userProfileMapper.selectById(userId);
    return Optional.ofNullable(po).map(poConverter::poToDomain);
  }

  @Override
  public void save(UserProfile profile) {
    if (profile == null) {
      return;
    }
    prepareTimestamps(profile);
    UserProfile existing = userProfileMapper.selectById(profile.getUserId());
    UserProfilePO po = poConverter.domainToPo(profile);
    if (existing == null) {
      userProfileMapper.insert(po);
      log.debug("用户画像已创建: userId={}", profile.getUserId());
    } else {
      userProfileMapper.updateById(po);
      log.debug("用户画像已更新(upsert): userId={}", profile.getUserId());
    }
  }

  @Override
  public void update(UserProfile profile) {
    if (profile == null) {
      return;
    }
    prepareTimestamps(profile);
    userProfileMapper.updateById(poConverter.domainToPo(profile));
  }

  @Override
  public List<UserProfile> findActiveProfiles(int limit) {
    if (limit <= 0) {
      return Collections.emptyList();
    }
    List<UserProfilePO> poList = userProfileMapper.selectList(
        new LambdaQueryWrapper<UserProfilePO>()
            .orderByDesc(UserProfilePO::getLastInteractionAt)
            .last("LIMIT " + limit));
    return poList.stream()
        .map(poConverter::poToDomain)
        .toList();
  }

  @Override
  public void deleteByUserId(String userId) {
    userProfileMapper.deleteById(userId);
  }

  /**
   * 填充创建/更新时间戳。
   *
   * @param profile 用户画像实体
   */
  private void prepareTimestamps(UserProfile profile) {
    LocalDateTime now = LocalDateTime.now();
    if (profile.getCreatedAt() == null) {
      profile.setCreatedAt(now);
    }
    profile.setUpdatedAt(now);
  }
}
