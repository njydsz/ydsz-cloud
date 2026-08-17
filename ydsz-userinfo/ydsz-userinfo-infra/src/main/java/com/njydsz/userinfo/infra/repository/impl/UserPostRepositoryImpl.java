package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.UserPostRepository;
import com.njydsz.userinfo.infra.entity.UserPostDO;
import com.njydsz.userinfo.infra.mapper.UserPostMapper;

/**
 * 用户-岗位关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserPostMapper} 实现用户-岗位关联的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserPostRepositoryImpl implements UserPostRepository {

  private final UserPostMapper userPostMapper;

  @Override
  public UserPostDO findById(String id) {
    return userPostMapper.selectById(id);
  }

  @Override
  public List<UserPostDO> findByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    return userPostMapper.selectList(wrapper);
  }

  @Override
  public List<String> findPostIdsByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    return userPostMapper.selectList(wrapper).stream()
        .map(UserPostDO::getPostId)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserPostDO> list(LambdaQueryWrapper<UserPostDO> wrapper) {
    return userPostMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserPostDO entity) {
    return userPostMapper.insert(entity);
  }

  @Override
  public int updateById(UserPostDO entity) {
    return userPostMapper.updateById(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    return userPostMapper.delete(wrapper);
  }

  @Override
  public int deleteById(String id) {
    return userPostMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserPostDO> wrapper) {
    return userPostMapper.delete(wrapper);
  }
}
