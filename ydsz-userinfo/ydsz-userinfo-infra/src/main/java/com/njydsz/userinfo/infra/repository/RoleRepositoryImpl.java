package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.converter.UserInfoAuthConverter;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;

/**
 * 角色 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link RoleMapper} 实现角色的数据访问。
 * 所有返回值通过 {@link UserInfoAuthConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

  private final RoleMapper roleMapper;
  private final UserInfoAuthConverter converter;

  @Override
  public Optional<RoleVO> findById(String id) {
    Role entity = roleMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<RoleVO> findByRoleCode(String roleCode) {
    LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Role::getRoleCode, roleCode);
    Role entity = roleMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<RoleVO> findByIds(Collection<String> ids) {
    List<Role> entities = roleMapper.selectBatchIds(ids);
    return converter.roleListToVO(entities);
  }

  @Override
  public List<RoleVO> listByIds(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<Role> list = roleMapper.selectBatchIds(ids);
    return list.stream().map(converter::entityToVO).collect(Collectors.toList());
  }

  @Override
  public PageResponse<List<RoleVO>> page(RolePageQuery query) {
    Page<Role> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Role> wrapper = buildWrapper(query);
    Page<Role> result = roleMapper.selectPage(page, wrapper);
    List<RoleVO> vos = converter.roleListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<RoleVO> list(RolePageQuery query) {
    LambdaQueryWrapper<Role> wrapper = buildWrapper(query);
    List<Role> entities = roleMapper.selectList(wrapper);
    return converter.roleListToVO(entities);
  }

  @Override
  public RoleVO save(RoleDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      // 创建场景
      Role entity = converter.dtoToEntity(dto);
      roleMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      // 更新场景
      Role entity = converter.dtoToEntityWithId(dto);
      roleMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return roleMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(RolePageQuery query) {
    LambdaQueryWrapper<Role> wrapper = buildWrapper(query);
    return roleMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<Role> buildWrapper(RolePageQuery query) {
    LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
    if (query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
      wrapper.like(Role::getRoleCode, query.getRoleCode());
    }
    if (query.getRoleName() != null && !query.getRoleName().isBlank()) {
      wrapper.like(Role::getRoleName, query.getRoleName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Role::getStatus, query.getStatus());
    }
    if (query.getTenantId() != null && !query.getTenantId().isBlank()) {
      wrapper.eq(Role::getTenantId, query.getTenantId());
    }
    return wrapper;
  }
}
