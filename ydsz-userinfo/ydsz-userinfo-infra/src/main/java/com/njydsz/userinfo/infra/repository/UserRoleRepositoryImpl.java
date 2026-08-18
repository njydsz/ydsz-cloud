package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.infra.entity.UserRoleDO;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;

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
  public List<UserRoleDO> findByUserId(String userId) {
    LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRoleDO::getUserId, userId);
    return userRoleMapper.selectList(wrapper);
  }

  @Override
  public List<String> findRoleIdsByUserId(String userId) {
    LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRoleDO::getUserId, userId);
    return userRoleMapper.selectList(wrapper).stream()
        .map(UserRoleDO::getRoleId)
        .collect(Collectors.toList());
  }

  @Override
  public UserRoleDO findByUserIdAndRoleId(String userId, String roleId) {
    LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRoleDO::getUserId, userId);
    wrapper.eq(UserRoleDO::getRoleId, roleId);
    return userRoleMapper.selectOne(wrapper);
  }

  @Override
  public List<UserRoleDO> list(LambdaQueryWrapper<UserRoleDO> wrapper) {
    return userRoleMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserRoleDO entity) {
    return userRoleMapper.insert(entity);
  }

  @Override
  public int batchInsert(List<UserRoleDO> list) {
    return userRoleMapper.batchInsert(list);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRoleDO::getUserId, userId);
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserRoleDO> wrapper) {
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserRoleDO> wrapper) {
    return userRoleMapper.selectCount(wrapper);
  }
}
