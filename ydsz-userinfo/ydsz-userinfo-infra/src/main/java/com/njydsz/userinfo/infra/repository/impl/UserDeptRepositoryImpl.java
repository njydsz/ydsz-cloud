package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.infra.entity.UserDeptDO;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;

/**
 * 用户-部门关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserDeptMapper} 实现用户-部门关联的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserDeptRepositoryImpl implements UserDeptRepository {

  private final UserDeptMapper userDeptMapper;

  @Override
  public UserDeptDO findById(String id) {
    return userDeptMapper.selectById(id);
  }

  @Override
  public List<UserDeptDO> findByUserId(String userId) {
    LambdaQueryWrapper<UserDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDeptDO::getUserId, userId);
    return userDeptMapper.selectList(wrapper);
  }

  @Override
  public List<String> findDeptIdsByUserId(String userId) {
    LambdaQueryWrapper<UserDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDeptDO::getUserId, userId);
    return userDeptMapper.selectList(wrapper).stream()
        .map(UserDeptDO::getDeptId)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserDeptDO> list(LambdaQueryWrapper<UserDeptDO> wrapper) {
    return userDeptMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserDeptDO entity) {
    return userDeptMapper.insert(entity);
  }

  @Override
  public int updateById(UserDeptDO entity) {
    return userDeptMapper.updateById(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDeptDO::getUserId, userId);
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public int deleteById(String id) {
    return userDeptMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserDeptDO> wrapper) {
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserDeptDO> wrapper) {
    return userDeptMapper.selectCount(wrapper);
  }
}
