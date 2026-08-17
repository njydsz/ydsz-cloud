package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.RolePermissionRepository;
import com.njydsz.userinfo.infra.entity.RolePermissionDO;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;

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
  public List<RolePermissionDO> findByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    return rolePermissionMapper.selectList(wrapper);
  }

  @Override
  public List<String> findMenuIdsByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    return rolePermissionMapper.selectList(wrapper).stream()
        .map(RolePermissionDO::getPermissionId)
        .collect(Collectors.toList());
  }

  @Override
  public List<RolePermissionDO> list(LambdaQueryWrapper<RolePermissionDO> wrapper) {
    return rolePermissionMapper.selectList(wrapper);
  }

  @Override
  public int batchInsert(List<RolePermissionDO> list) {
    return rolePermissionMapper.batchInsert(list);
  }

  @Override
  public int deleteByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    return rolePermissionMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<RolePermissionDO> wrapper) {
    return rolePermissionMapper.delete(wrapper);
  }
}
