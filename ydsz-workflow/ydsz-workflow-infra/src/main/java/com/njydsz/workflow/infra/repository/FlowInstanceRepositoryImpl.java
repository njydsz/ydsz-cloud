package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;

/**
 * 流程实例仓储实现（Repository Adapter）
 *
 * <p>位于基础设施层，实现领域层定义的 {@link FlowInstanceRepository} 接口。 内部委托 MyBatis-Plus Mapper 完成具体数据库操作。
 *
 * <p><b>分层定位：</b>依赖方向为 infra → domain（符合 DDD 依赖倒置原则）， domain 层定义接口契约，infra 层提供适配器实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FlowInstanceRepositoryImpl implements FlowInstanceRepository {

  private final FlowInstanceMapper instanceMapper;

  @Override
  public FlowInstanceDO save(FlowInstanceDO instance) {
    if (instance.getId() == null) {
      instanceMapper.insert(instance);
    } else {
      instanceMapper.updateById(instance);
    }
    return instance;
  }

  @Override
  public Optional<FlowInstanceDO> findById(String id) {
    return Optional.ofNullable(instanceMapper.selectById(id));
  }

  @Override
  public Optional<FlowInstanceDO> findByBusiness(String businessType, String businessId) {
    return instanceMapper
        .selectList(
            new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getBusinessType, businessType)
                .eq(FlowInstanceDO::getBusinessId, businessId)
                .eq(FlowInstanceDO::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public List<FlowInstanceDO> findByInitiatorId(String initiatorId) {
    return instanceMapper.selectList(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getInitiatorId, initiatorId)
            .eq(FlowInstanceDO::getDeleted, 0)
            .orderByDesc(FlowInstanceDO::getCreatedAt));
  }

  @Override
  public List<FlowInstanceDO> findChildren(String parentInstanceId) {
    return instanceMapper.selectList(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getParentInstanceId, parentInstanceId)
            .eq(FlowInstanceDO::getDeleted, 0));
  }

  @Override
  public long countByStatus(String flowStatus) {
    return instanceMapper.selectCount(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getFlowStatus, flowStatus)
            .eq(FlowInstanceDO::getDeleted, 0));
  }

  @Override
  public List<FlowInstanceDO> findSuspendedBefore(LocalDateTime before, int limit) {
    return instanceMapper.selectList(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getFlowStatus, "SUSPENDED")
            .le(FlowInstanceDO::getUpdatedAt, before)
            .eq(FlowInstanceDO::getDeleted, 0)
            .last("LIMIT " + limit));
  }

  @Override
  public void deleteById(String id) {
    instanceMapper.deleteById(id);
  }
}
