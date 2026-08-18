package com.njydsz.workflow.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowAdminRoleRepository;
import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowAdminRoleDO;
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
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FlowAdminRoleRepositoryImpl implements FlowAdminRoleRepository {

  private final FlowAdminRoleMapper adminRoleMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAdminRoleVO save(FlowAdminRoleVO vo) {
    FlowAdminRoleDO entity = converter.entityToDO(vo);
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
            new LambdaQueryWrapper<FlowAdminRoleDO>()
                .eq(FlowAdminRoleDO::getUserId, userId)
                .eq(FlowAdminRoleDO::getDeleted, 0)));
  }

  @Override
  public List<FlowAdminRoleVO> findByRoleCode(String roleCode) {
    return converter.flowAdminRoleListToVO(
        adminRoleMapper.selectList(
            new LambdaQueryWrapper<FlowAdminRoleDO>()
                .eq(FlowAdminRoleDO::getRoleCode, roleCode)
                .eq(FlowAdminRoleDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    adminRoleMapper.deleteById(id);
  }

  @Override
  public FlowAdminRoleVO update(FlowAdminRoleVO vo) {
    FlowAdminRoleDO entity = converter.entityToDO(vo);
    adminRoleMapper.updateById(entity);
    return vo;
  }
}
