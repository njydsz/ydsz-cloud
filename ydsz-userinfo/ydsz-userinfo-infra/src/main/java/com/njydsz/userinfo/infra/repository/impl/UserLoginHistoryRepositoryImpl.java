package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.infra.entity.UserLoginHistoryDO;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;

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
  public int insert(UserLoginHistoryDO entity) {
    return userLoginHistoryMapper.insert(entity);
  }

  @Override
  public int countRecentFailures(String userId, int windowMinutes) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistoryDO::getUserId, userId);
    wrapper.eq(UserLoginHistoryDO::getLoginResult, "FAILED");
    wrapper.ge(UserLoginHistoryDO::getCreatedAt,
        java.time.LocalDateTime.now().minusMinutes(windowMinutes));
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryDO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistoryDO::getUserId, userId);
    wrapper.orderByDesc(UserLoginHistoryDO::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    return userLoginHistoryMapper.selectList(wrapper);
  }

  @Override
  public List<UserLoginHistoryDO> list(LambdaQueryWrapper<UserLoginHistoryDO> wrapper) {
    return userLoginHistoryMapper.selectList(wrapper);
  }
}
