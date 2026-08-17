package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.RoleRepository;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;

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
  public RoleDO findById(String id) {
    return roleMapper.selectById(id);
  }

  @Override
  public RoleDO findByRoleCode(String roleCode) {
    LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RoleDO::getRoleCode, roleCode);
    return roleMapper.selectOne(wrapper);
  }

  @Override
  public List<RoleDO> findByIds(Collection<String> ids) {
    return roleMapper.selectBatchIds(ids);
  }

  @Override
  public List<RoleDO> list(LambdaQueryWrapper<RoleDO> wrapper) {
    return roleMapper.selectList(wrapper);
  }

  @Override
  public Page<RoleDO> page(Page<RoleDO> page, LambdaQueryWrapper<RoleDO> wrapper) {
    return roleMapper.selectPage(page, wrapper);
  }

  @Override
  public int insert(RoleDO entity) {
    return roleMapper.insert(entity);
  }

  @Override
  public int updateById(RoleDO entity) {
    return roleMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return roleMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<RoleDO> wrapper) {
    return roleMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<RoleDO> wrapper) {
    return roleMapper.selectCount(wrapper);
  }
}
