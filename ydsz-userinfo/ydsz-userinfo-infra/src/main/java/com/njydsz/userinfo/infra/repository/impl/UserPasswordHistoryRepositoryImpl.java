package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.UserPasswordHistoryRepository;
import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;
import com.njydsz.userinfo.infra.mapper.UserPasswordHistoryMapper;

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
  public int insert(UserPasswordHistoryDO entity) {
    return userPasswordHistoryMapper.insert(entity);
  }

  @Override
  public List<UserPasswordHistoryDO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    wrapper.orderByDesc(UserPasswordHistoryDO::getCreatedAt);
    wrapper.last("LIMIT " + limit);
    return userPasswordHistoryMapper.selectList(wrapper);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    return userPasswordHistoryMapper.delete(wrapper);
  }

  @Override
  public long countByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    return userPasswordHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserPasswordHistoryDO> wrapper) {
    return userPasswordHistoryMapper.delete(wrapper);
  }

  @Override
  public List<UserPasswordHistoryDO> list(LambdaQueryWrapper<UserPasswordHistoryDO> wrapper) {
    return userPasswordHistoryMapper.selectList(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserPasswordHistoryDO> wrapper) {
    return userPasswordHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int deleteByIds(Collection<String> ids) {
    return userPasswordHistoryMapper.deleteBatchIds(ids);
  }
}
