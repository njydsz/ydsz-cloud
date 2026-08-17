package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserLoginHistory;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;
import com.njydsz.userinfo.infra.repository.UserLoginHistoryRepository;

/**
 * 用户登录历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserLoginHistoryMapper} 实现登录历史的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserLoginHistoryRepositoryImpl implements UserLoginHistoryRepository {

  private final UserLoginHistoryMapper userLoginHistoryMapper;

  @Override
  public int insert(UserLoginHistory entity) {
    return userLoginHistoryMapper.insert(entity);
  }

  @Override
  public int countRecentFailures(String userId, int windowMinutes) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    wrapper.ge(UserLoginHistory::getCreatedAt,
        java.time.LocalDateTime.now().minusMinutes(windowMinutes));
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistory> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.orderByDesc(UserLoginHistory::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    return userLoginHistoryMapper.selectList(wrapper);
  }

  @Override
  public List<UserLoginHistory> list(LambdaQueryWrapper<UserLoginHistory> wrapper) {
    return userLoginHistoryMapper.selectList(wrapper);
  }
}
