package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowDelegateAuthRepository;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowDelegateAuthDO;
import com.njydsz.workflow.infra.mapper.FlowDelegateAuthMapper;

/**
 * 委托授权仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowDelegateAuthRepository} 接口，封装 FlowDelegateAuthMapper 数据访问细节。
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
public class FlowDelegateAuthRepositoryImpl implements FlowDelegateAuthRepository {

  private final FlowDelegateAuthMapper delegateAuthMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowDelegateAuthVO save(FlowDelegateAuthVO vo) {
    FlowDelegateAuthDO entity = converter.entityToDO(vo);
    delegateAuthMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowDelegateAuthVO> findById(String id) {
    return Optional.ofNullable(delegateAuthMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowDelegateAuthVO> findByDelegatorId(String delegatorId) {
    return converter.flowDelegateAuthListToVO(
        delegateAuthMapper.selectList(
            new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getDelegatorId, delegatorId)
                .eq(FlowDelegateAuthDO::getDeleted, 0)));
  }

  @Override
  public List<FlowDelegateAuthVO> findByDelegatorAndFlow(String delegatorId, String flowCode) {
    return converter.flowDelegateAuthListToVO(
        delegateAuthMapper.selectList(
            new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getDelegatorId, delegatorId)
                .eq(FlowDelegateAuthDO::getFlowCode, flowCode)
                .eq(FlowDelegateAuthDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    delegateAuthMapper.deleteById(id);
  }

  @Override
  public FlowDelegateAuthVO update(FlowDelegateAuthVO vo) {
    FlowDelegateAuthDO entity = converter.entityToDO(vo);
    delegateAuthMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowDelegateAuthVO> findActiveByOwner(
      String ownerId, String tenantId, LocalDateTime now) {
    return converter.flowDelegateAuthListToVO(
        delegateAuthMapper.selectList(
            new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getOwnerUserId, ownerId)
                .eq(FlowDelegateAuthDO::getTenantId, tenantId)
                .eq(FlowDelegateAuthDO::getAuthStatus, "ENABLED")
                .le(FlowDelegateAuthDO::getStartTime, now)
                .ge(FlowDelegateAuthDO::getEndTime, now)
                .eq(FlowDelegateAuthDO::getDeleted, 0)));
  }

  @Override
  public List<FlowDelegateAuthVO> matchAuth(
      String ownerId, String flowCode, LocalDateTime now) {
    return converter.flowDelegateAuthListToVO(
        delegateAuthMapper.selectList(
            new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getOwnerUserId, ownerId)
                .eq(FlowDelegateAuthDO::getFlowCode, flowCode)
                .eq(FlowDelegateAuthDO::getAuthStatus, "ENABLED")
                .le(FlowDelegateAuthDO::getStartTime, now)
                .ge(FlowDelegateAuthDO::getEndTime, now)
                .eq(FlowDelegateAuthDO::getDeleted, 0)));
  }

  @Override
  public void updateStatus(String id, String status) {
    FlowDelegateAuthDO update = new FlowDelegateAuthDO();
    update.setAuthStatus(status);
    update.setId(id);
    delegateAuthMapper.updateById(update);
  }
}
