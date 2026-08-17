package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.infra.repository.UserRoleRepository;

/**
 * 用户-角色关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserRoleMapper} 实现用户-角色关联的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

  private final UserRoleMapper userRoleMapper;

  @Override
  public List<UserRole> findByUserId(String userId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    return userRoleMapper.selectList(wrapper);
  }

  @Override
  public List<String> findRoleIdsByUserId(String userId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    return userRoleMapper.selectList(wrapper).stream()
        .map(UserRole::getRoleId)
        .collect(Collectors.toList());
  }

  @Override
  public UserRole findByUserIdAndRoleId(String userId, String roleId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    wrapper.eq(UserRole::getRoleId, roleId);
    return userRoleMapper.selectOne(wrapper);
  }

  @Override
  public List<UserRole> list(LambdaQueryWrapper<UserRole> wrapper) {
    return userRoleMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserRole entity) {
    return userRoleMapper.insert(entity);
  }

  @Override
  public int batchInsert(List<UserRole> list) {
    return userRoleMapper.batchInsert(list);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserRole> wrapper) {
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserRole> wrapper) {
    return userRoleMapper.selectCount(wrapper);
  }
}
