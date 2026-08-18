package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.RoleUpdateDTO;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;

/**
 * 角色 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link RoleMapper} 实现角色的数据访问。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

  private final RoleMapper roleMapper;
  private final UserInfoConverter converter;

  @Override
  public Optional<RoleVO> findById(String id) {
    RoleDO entity = roleMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<RoleVO> findByRoleCode(String roleCode) {
    LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RoleDO::getRoleCode, roleCode);
    RoleDO entity = roleMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<RoleVO> findByIds(Collection<String> ids) {
    List<RoleDO> entities = roleMapper.selectBatchIds(ids);
    return converter.roleListToVO(entities);
  }

  @Override
  public PageResponse<List<RoleVO>> page(RolePageQuery query) {
    Page<RoleDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<RoleDO> wrapper = buildWrapper(query);
    Page<RoleDO> result = roleMapper.selectPage(page, wrapper);
    List<RoleVO> vos = converter.roleListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<RoleVO> list(RolePageQuery query) {
    LambdaQueryWrapper<RoleDO> wrapper = buildWrapper(query);
    List<RoleDO> entities = roleMapper.selectList(wrapper);
    return converter.roleListToVO(entities);
  }

  @Override
  public RoleVO create(RoleCreateDTO dto) {
    RoleDO entity = converter.createDtoToEntity(dto);
    roleMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public RoleVO update(RoleUpdateDTO dto) {
    RoleDO entity = converter.updateDtoToEntity(dto);
    roleMapper.updateById(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public boolean deleteById(String id) {
    return roleMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(RolePageQuery query) {
    LambdaQueryWrapper<RoleDO> wrapper = buildWrapper(query);
    return roleMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<RoleDO> buildWrapper(RolePageQuery query) {
    LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
      wrapper.like(RoleDO::getRoleCode, query.getRoleCode());
    }
    if (query.getRoleName() != null && !query.getRoleName().isBlank()) {
      wrapper.like(RoleDO::getRoleName, query.getRoleName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(RoleDO::getStatus, query.getStatus());
    }
    if (query.getTenantId() != null && !query.getTenantId().isBlank()) {
      wrapper.eq(RoleDO::getTenantId, query.getTenantId());
    }
    return wrapper;
  }
}
