package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowEventSubscriptionRepository;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowEventSubscriptionDO;
import com.njydsz.workflow.infra.mapper.FlowEventSubscriptionMapper;

/**
 * 事件订阅仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowEventSubscriptionRepository} 接口，封装 FlowEventSubscriptionMapper 数据访问细节。
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
public class FlowEventSubscriptionRepositoryImpl implements FlowEventSubscriptionRepository {

  private final FlowEventSubscriptionMapper eventSubscriptionMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowEventSubscriptionVO save(FlowEventSubscriptionVO vo) {
    FlowEventSubscriptionDO entity = converter.entityToDO(vo);
    eventSubscriptionMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowEventSubscriptionVO> findById(String id) {
    return Optional.ofNullable(eventSubscriptionMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowEventSubscriptionVO> findByInstanceId(String instanceId) {
    return converter.flowEventSubscriptionListToVO(
        eventSubscriptionMapper.selectList(
            new LambdaQueryWrapper<FlowEventSubscriptionDO>()
                .eq(FlowEventSubscriptionDO::getInstanceId, instanceId)
                .eq(FlowEventSubscriptionDO::getDeleted, 0)));
  }

  @Override
  public List<FlowEventSubscriptionVO> findByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowEventSubscriptionListToVO(
        eventSubscriptionMapper.selectList(
            new LambdaQueryWrapper<FlowEventSubscriptionDO>()
                .eq(FlowEventSubscriptionDO::getInstanceId, instanceId)
                .eq(FlowEventSubscriptionDO::getNodeCode, nodeCode)
                .eq(FlowEventSubscriptionDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    eventSubscriptionMapper.deleteById(id);
  }

  @Override
  public void deleteByInstanceId(String instanceId) {
    eventSubscriptionMapper.delete(
        new LambdaQueryWrapper<FlowEventSubscriptionDO>()
            .eq(FlowEventSubscriptionDO::getInstanceId, instanceId));
  }

  @Override
  public FlowEventSubscriptionVO update(FlowEventSubscriptionVO vo) {
    FlowEventSubscriptionDO entity = converter.entityToDO(vo);
    eventSubscriptionMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowEventSubscriptionVO> findWaitingByEvent(String eventType, String flowCode) {
    return converter.flowEventSubscriptionListToVO(
        eventSubscriptionMapper.selectList(
            new LambdaQueryWrapper<FlowEventSubscriptionDO>()
                .eq(FlowEventSubscriptionDO::getEventType, eventType)
                .eq(FlowEventSubscriptionDO::getFlowCode, flowCode)
                .eq(FlowEventSubscriptionDO::getSubscriptionStatus, "WAITING")
                .eq(FlowEventSubscriptionDO::getDeleted, 0)));
  }

  @Override
  public List<FlowEventSubscriptionVO> findWaitingByEvent(
      String tenantId, String eventType, String eventRef) {
    return converter.flowEventSubscriptionListToVO(
        eventSubscriptionMapper.selectWaitingByEvent(tenantId, eventType, eventRef));
  }

  @Override
  public void markTriggered(String id) {
    FlowEventSubscriptionDO update = new FlowEventSubscriptionDO();
    update.setSubscriptionStatus("COMPLETED");
    update.setTriggeredAt(LocalDateTime.now());
    update.setId(id);
    eventSubscriptionMapper.updateById(update);
  }

  @Override
  public void markTriggered(
      String id, String eventPayload, String triggerSource, LocalDateTime triggeredAt) {
    eventSubscriptionMapper.markTriggered(id, eventPayload, triggerSource, triggeredAt);
  }

  @Override
  public void resetToWaiting(String id) {
    FlowEventSubscriptionDO update = new FlowEventSubscriptionDO();
    update.setSubscriptionStatus("WAITING");
    update.setTriggeredAt(null);
    update.setId(id);
    eventSubscriptionMapper.updateById(update);
  }

  @Override
  public int cancelByTask(String boundaryTaskId, String reason) {
    return eventSubscriptionMapper.cancelByTask(boundaryTaskId, reason);
  }

  @Override
  public int cancelByInstance(String instanceId, String reason) {
    return eventSubscriptionMapper.cancelByInstance(instanceId, reason);
  }

  @Override
  public List<FlowEventSubscriptionVO> findByInstanceOrderByCreatedAtDesc(String instanceId) {
    return converter.flowEventSubscriptionListToVO(
        eventSubscriptionMapper.selectList(
            new LambdaQueryWrapper<FlowEventSubscriptionDO>()
                .eq(FlowEventSubscriptionDO::getInstanceId, instanceId)
                .eq(FlowEventSubscriptionDO::getDeleted, 0)
                .orderByDesc(FlowEventSubscriptionDO::getCreatedAt)));
  }
}
