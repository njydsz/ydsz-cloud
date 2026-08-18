package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.repository.RolePermissionRepository;
import com.njydsz.userinfo.domain.vo.RolePermissionVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.RolePermissionDO;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;

/**
 * 角色-权限关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link RolePermissionMapper} 实现角色-权限关联的数据访问。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class RolePermissionRepositoryImpl implements RolePermissionRepository {

  private final RolePermissionMapper rolePermissionMapper;
  private final UserInfoConverter converter;

  @Override
  public List<RolePermissionVO> findByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    List<RolePermissionDO> entities = rolePermissionMapper.selectList(wrapper);
    return converter.rolePermissionListToVO(entities);
  }

  @Override
  public List<String> findPermissionIdsByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    return rolePermissionMapper.selectList(wrapper).stream()
        .map(RolePermissionDO::getPermissionId)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<RolePermissionVO> findByRoleIdAndPermissionId(String roleId, String permissionId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    wrapper.eq(RolePermissionDO::getPermissionId, permissionId);
    RolePermissionDO entity = rolePermissionMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public RolePermissionVO create(RolePermissionDTO dto) {
    RolePermissionDO entity = converter.dtoToEntity(dto);
    rolePermissionMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int batchInsert(List<RolePermissionDTO> dtoList) {
    List<RolePermissionDO> entities = dtoList.stream()
        .map(converter::dtoToEntity)
        .collect(Collectors.toList());
    return rolePermissionMapper.batchInsert(entities);
  }

  @Override
  public int deleteByRoleId(String roleId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    return rolePermissionMapper.delete(wrapper);
  }

  @Override
  public int deleteByRoleIdAndPermissionId(String roleId, String permissionId) {
    LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RolePermissionDO::getRoleId, roleId);
    wrapper.eq(RolePermissionDO::getPermissionId, permissionId);
    return rolePermissionMapper.delete(wrapper);
  }
}
