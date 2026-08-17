package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.RolePermission;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;
import com.njydsz.userinfo.infra.repository.RolePermissionRepository;

/**
 * 角色-权限关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link RolePermissionMapper} 实现角色-权限关联的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class RolePermissionRepositoryImpl implements RolePermissionRepository {

  private final RolePermissionMapper rolePermissionMapper;

  @Override
  public List<RolePermission> findByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermission::getRoleId, roleId);
    return rolePermissionMapper.selectList(wrapper);
  }

  @Override
  public List<String> findMenuIdsByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermission::getRoleId, roleId);
    return rolePermissionMapper.selectList(wrapper).stream()
        .map(RolePermission::getPermissionId)
        .collect(Collectors.toList());
  }

  @Override
  public List<RolePermission> list(LambdaQueryWrapper<RolePermission> wrapper) {
    return rolePermissionMapper.selectList(wrapper);
  }

  @Override
  public int batchInsert(List<RolePermission> list) {
    return rolePermissionMapper.batchInsert(list);
  }

  @Override
  public int deleteByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermission::getRoleId, roleId);
    return rolePermissionMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<RolePermission> wrapper) {
    return rolePermissionMapper.delete(wrapper);
  }
}
