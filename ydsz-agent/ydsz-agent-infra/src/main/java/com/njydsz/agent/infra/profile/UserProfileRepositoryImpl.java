package com.njydsz.agent.infra.profile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.UserProfile;
import com.njydsz.agent.domain.profile.UserProfileRepository;

/**
 * 用户画像 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 {@link UserProfileRepository} 接口，
 * 以 userId 作为主键（业务 ID，非自增）。
 *
 * <p><b>DDD 分层：</b> domain 层 UserProfile 为纯净 POJO，直接用于持久化。
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

  private final BaseMapper<UserProfile> userProfileMapper;

  @Override
  public Optional<UserProfile> findByUserId(String userId) {
    return Optional.ofNullable(userProfileMapper.selectById(userId));
  }

  @Override
  public void save(UserProfile profile) {
    if (profile == null) {
      return;
    }
    prepareTimestamps(profile);
    UserProfile existing = userProfileMapper.selectById(profile.getUserId());
    if (existing == null) {
      userProfileMapper.insert(profile);
      log.debug("用户画像已创建: userId={}", profile.getUserId());
    } else {
      userProfileMapper.updateById(profile);
      log.debug("用户画像已更新(upsert): userId={}", profile.getUserId());
    }
  }

  @Override
  public void update(UserProfile profile) {
    if (profile == null) {
      return;
    }
    prepareTimestamps(profile);
    userProfileMapper.updateById(profile);
  }

  @Override
  public List<UserProfile> findActiveProfiles(int limit) {
    if (limit <= 0) {
      return Collections.emptyList();
    }
    return userProfileMapper.selectList(
        new LambdaQueryWrapper<UserProfile>()
            .orderByDesc(UserProfile::getLastInteractionAt)
            .last("LIMIT " + limit));
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
