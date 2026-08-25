package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.domain.vo.UserDeptVO;
import com.njydsz.userinfo.infra.converter.UserInfoUserConverter;
import com.njydsz.userinfo.infra.entity.UserDept;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;

/**
 * 用户-部门关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserDeptMapper} 实现用户-部门关联的数据访问。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserDeptRepositoryImpl implements UserDeptRepository {

  private final UserDeptMapper userDeptMapper;
  private final UserInfoUserConverter converter;

  @Override
  public Optional<UserDeptVO> findById(String id) {
    UserDept entity = userDeptMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<UserDeptVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    List<UserDept> entities = userDeptMapper.selectList(wrapper);
    return converter.userDeptListToVO(entities);
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
  public Optional<UserDeptVO> findByUserIdAndDeptId(String userId, String deptId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    wrapper.eq(UserDept::getDeptId, deptId);
    UserDept entity = userDeptMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public UserDeptVO create(UserDeptDTO dto) {
    UserDept entity = converter.dtoToEntity(dto);
    userDeptMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public UserDeptVO update(UserDeptDTO dto) {
    UserDept entity = converter.userDeptDtoToEntityWithId(dto);
    userDeptMapper.updateById(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public int deleteByUserIdAndDeptId(String userId, String deptId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getUserId, userId);
    wrapper.eq(UserDept::getDeptId, deptId);
    return userDeptMapper.delete(wrapper);
  }

  @Override
  public boolean deleteById(String id) {
    return userDeptMapper.deleteById(id) > 0;
  }

  @Override
  public long countByDeptId(String deptId) {
    LambdaQueryWrapper<UserDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserDept::getDeptId, deptId);
    return userDeptMapper.selectCount(wrapper);
  }
}
