package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserRoleDTO;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.domain.vo.UserRoleVO;
import com.njydsz.userinfo.infra.converter.UserInfoUserConverter;
import com.njydsz.userinfo.infra.entity.UserRole;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;

/**
 * 用户-角色关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserRoleMapper} 实现用户-角色关联的数据访问。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

  private final UserRoleMapper userRoleMapper;
  private final UserInfoUserConverter converter;

  @Override
  public List<UserRoleVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    List<UserRole> entities = userRoleMapper.selectList(wrapper);
    return converter.userRoleListToVO(entities);
  }

  @Override
  public List<UserRoleVO> findByRoleId(String roleId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getRoleId, roleId);
    List<UserRole> entities = userRoleMapper.selectList(wrapper);
    return converter.userRoleListToVO(entities);
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
  public Optional<UserRoleVO> findByUserIdAndRoleId(String userId, String roleId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    wrapper.eq(UserRole::getRoleId, roleId);
    UserRole entity = userRoleMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public UserRoleVO create(UserRoleDTO dto) {
    UserRole entity = converter.dtoToEntity(dto);
    userRoleMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int batchInsert(List<UserRoleDTO> dtoList) {
    List<UserRole> entities = dtoList.stream()
        .map(converter::dtoToEntity)
        .collect(Collectors.toList());
    return userRoleMapper.batchInsert(entities);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public int deleteByUserIdAndRoleId(String userId, String roleId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    wrapper.eq(UserRole::getRoleId, roleId);
    return userRoleMapper.delete(wrapper);
  }

  @Override
  public long countByRoleId(String roleId) {
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getRoleId, roleId);
    return userRoleMapper.selectCount(wrapper);
  }
}
