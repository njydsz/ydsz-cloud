package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserPasswordHistory;
import com.njydsz.userinfo.infra.mapper.UserPasswordHistoryMapper;
import com.njydsz.userinfo.infra.repository.UserPasswordHistoryRepository;

/**
 * 密码历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserPasswordHistoryMapper} 实现密码历史的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserPasswordHistoryRepositoryImpl implements UserPasswordHistoryRepository {

  private final UserPasswordHistoryMapper userPasswordHistoryMapper;

  @Override
  public int insert(UserPasswordHistory entity) {
    return userPasswordHistoryMapper.insert(entity);
  }

  @Override
  public List<UserPasswordHistory> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserPasswordHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistory::getUserId, userId);
    wrapper.orderByDesc(UserPasswordHistory::getCreatedAt);
    wrapper.last("LIMIT " + limit);
    return userPasswordHistoryMapper.selectList(wrapper);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistory::getUserId, userId);
    return userPasswordHistoryMapper.delete(wrapper);
  }

  @Override
  public long countByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistory::getUserId, userId);
    return userPasswordHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserPasswordHistory> wrapper) {
    return userPasswordHistoryMapper.delete(wrapper);
  }

  @Override
  public List<UserPasswordHistory> list(LambdaQueryWrapper<UserPasswordHistory> wrapper) {
    return userPasswordHistoryMapper.selectList(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserPasswordHistory> wrapper) {
    return userPasswordHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int deleteByIds(Collection<String> ids) {
    return userPasswordHistoryMapper.deleteBatchIds(ids);
  }
}
