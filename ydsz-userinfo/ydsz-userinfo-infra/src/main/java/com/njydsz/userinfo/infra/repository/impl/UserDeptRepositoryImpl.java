package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.UserDept;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;
import com.njydsz.userinfo.infra.repository.UserDeptRepository;

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
  public UserDept findById(String id) {
    return userDeptMapper.selectById(id);
  }

  @Override
  public List<UserDept> findByUserId(String userId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    return userDeptMapper.selectList(wrapper);
  }

  @Override
  public List<String> findDeptIdsByUserId(String userId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    return userDeptMapper.selectList(wrapper).stream()
        .map(UserDept::getDeptId)
        .collect(Collectors.toList());
  }

  @Override
  public List<UserDept> list(LambdaQueryWrapper<UserDept> wrapper) {
    return userDeptMapper.selectList(wrapper);
  }

  @Override
  public int insert(UserDept entity) {
    return userDeptMapper.insert(entity);
  }

  @Override
  public int updateById(UserDept entity) {
    return userDeptMapper.updateById(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<UserDept> wrapper) {
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<UserDept> wrapper) {
    return userDeptMapper.selectCount(wrapper);
  }
}
