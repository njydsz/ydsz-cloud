package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.entity.FlowAdminRole;
import com.njydsz.workflow.domain.repository.FlowAdminRoleRepository;
import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;
import com.njydsz.workflow.infra.mapper.FlowAdminRoleMapper;

/**
 * 管理员角色仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowAdminRoleRepository} 接口，封装 FlowAdminRoleMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link WorkflowConverter} 将 DO 转换为 VO 后返回领域层
 * </ul>
 *
 * <p><b>分层定位：</b>依赖方向为 infra → domain（符合 DDD 依赖倒置原则）， domain 层定义接口契约，infra 层提供适配器实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class FlowAdminRoleRepositoryImpl implements FlowAdminRoleRepository {

  private final FlowAdminRoleMapper adminRoleMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAdminRoleVO save(FlowAdminRoleVO vo) {
    FlowAdminRole entity = converter.entityToEntity(vo);
    adminRoleMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowAdminRoleVO> findById(String id) {
    return Optional.ofNullable(adminRoleMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowAdminRoleVO> findByUserId(String userId) {
    return converter.flowAdminRoleListToVO(
        adminRoleMapper.selectList(
            new LambdaQueryWrapper<FlowAdminRole>()
                .eq(FlowAdminRole::getUserId, userId)
                .eq(FlowAdminRole::getDeleted, 0)));
  }

  @Override
  public List<FlowAdminRoleVO> findByRoleCode(String roleCode) {
    return converter.flowAdminRoleListToVO(
        adminRoleMapper.selectList(
            new LambdaQueryWrapper<FlowAdminRole>()
                .eq(FlowAdminRole::getRoleCode, roleCode)
                .eq(FlowAdminRole::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    adminRoleMapper.deleteById(id);
  }

  @Override
  public FlowAdminRoleVO update(FlowAdminRoleVO vo) {
    FlowAdminRole entity = converter.entityToEntity(vo);
    adminRoleMapper.updateById(entity);
    return vo;
  }

  @Override
  public Optional<FlowAdminRoleVO> findByUserAndRole(String userId, String roleCode) {
    return adminRoleMapper
        .selectList(
            new LambdaQueryWrapper<FlowAdminRole>()
                .eq(FlowAdminRole::getUserId, userId)
                .eq(FlowAdminRole::getRoleCode, roleCode)
                .eq(FlowAdminRole::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public Optional<FlowAdminRoleVO> findByUserAndRole(String userId, String roleCode, String tenantId) {
    return adminRoleMapper
        .selectList(
            new LambdaQueryWrapper<FlowAdminRole>()
                .eq(FlowAdminRole::getUserId, userId)
                .eq(FlowAdminRole::getRoleCode, roleCode)
                .eq(tenantId != null, FlowAdminRole::getTenantId, tenantId)
                .eq(FlowAdminRole::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowAdminRoleVO> findByUserId(String userId, String tenantId) {
    return converter.flowAdminRoleListToVO(
        adminRoleMapper.selectList(
            new LambdaQueryWrapper<FlowAdminRole>()
                .eq(FlowAdminRole::getUserId, userId)
                .eq(tenantId != null, FlowAdminRole::getTenantId, tenantId)
                .eq(FlowAdminRole::getDeleted, 0)));
  }
}
