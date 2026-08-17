package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.repository.RoleRepository;

/**
 * 角色 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link RoleMapper} 实现角色的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

  private final RoleMapper roleMapper;

  @Override
  public Role findById(String id) {
    return roleMapper.selectById(id);
  }

  @Override
  public Role findByRoleCode(String roleCode) {
    LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Role::getRoleCode, roleCode);
    return roleMapper.selectOne(wrapper);
  }

  @Override
  public List<Role> findByIds(Collection<String> ids) {
    return roleMapper.selectBatchIds(ids);
  }

  @Override
  public List<Role> list(LambdaQueryWrapper<Role> wrapper) {
    return roleMapper.selectList(wrapper);
  }

  @Override
  public Page<Role> page(Page<Role> page, LambdaQueryWrapper<Role> wrapper) {
    return roleMapper.selectPage(page, wrapper);
  }

  @Override
  public int insert(Role entity) {
    return roleMapper.insert(entity);
  }

  @Override
  public int updateById(Role entity) {
    return roleMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return roleMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<Role> wrapper) {
    return roleMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<Role> wrapper) {
    return roleMapper.selectCount(wrapper);
  }
}
