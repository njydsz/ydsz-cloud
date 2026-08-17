package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserPost;
import com.njydsz.userinfo.infra.mapper.UserPostMapper;
import com.njydsz.userinfo.infra.repository.UserPostRepository;

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
  public UserPost findById(String id) {
    return userPostMapper.selectById(id);
  }

  @Override
  public List<UserPost> findByUserId(String userId) {
    LambdaQueryWrapper<UserPost> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPost::getUserId, userId);
    return userPostMapper.selectList(wrapper);
  }

  @Override
  public List<String> findPostIdsByUserId(String userId) {
    LambdaQueryWrapper<UserPost> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPost::getUserId, userId);
    return userPostMapper.selectList(wrapper).stream()
        .map(UserPost::getPostId)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserPost> list(LambdaQueryWrapper<UserPost> wrapper) {
    return userPostMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserPost entity) {
    return userPostMapper.insert(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPost> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPost::getUserId, userId);
    return userPostMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserPost> wrapper) {
    return userPostMapper.delete(wrapper);
  }
}
