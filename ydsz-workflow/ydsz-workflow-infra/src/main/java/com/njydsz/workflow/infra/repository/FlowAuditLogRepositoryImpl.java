package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowAuditLogDO;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;

/**
 * 审计日志仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowAuditLogRepository} 接口，封装 FlowAuditLogMapper 数据访问细节。
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
public class FlowAuditLogRepositoryImpl implements FlowAuditLogRepository {

  private final FlowAuditLogMapper auditLogMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAuditLogVO save(FlowAuditLogVO vo) {
    FlowAuditLogDO entity = converter.entityToDO(vo);
    auditLogMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowAuditLogVO> findById(String id) {
    return Optional.ofNullable(auditLogMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowAuditLogVO> findByInstanceId(String instanceId) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLogDO>()
                .eq(FlowAuditLogDO::getInstanceId, instanceId)
                .eq(FlowAuditLogDO::getDeleted, 0)
                .orderByDesc(FlowAuditLogDO::getOperatedAt)));
  }

  @Override
  public List<FlowAuditLogVO> findByInstanceIdAndAction(String instanceId, String action) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLogDO>()
                .eq(FlowAuditLogDO::getInstanceId, instanceId)
                .eq(FlowAuditLogDO::getAction, action)
                .eq(FlowAuditLogDO::getDeleted, 0)
                .orderByDesc(FlowAuditLogDO::getOperatedAt)));
  }

  @Override
  public List<FlowAuditLogVO> findByTaskId(String taskId) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLogDO>()
                .eq(FlowAuditLogDO::getTaskId, taskId)
                .eq(FlowAuditLogDO::getDeleted, 0)
                .orderByDesc(FlowAuditLogDO::getOperatedAt)));
  }

  @Override
  public void deleteById(String id) {
    auditLogMapper.deleteById(id);
  }

  @Override
  public List<FlowAuditLogVO> findByBusinessTypeAndOperator(
      String businessType, String operatorId, int offset, int limit) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLogDO>()
                .eq(FlowAuditLogDO::getBusinessType, businessType)
                .eq(FlowAuditLogDO::getOperatorId, operatorId)
                .orderByDesc(FlowAuditLogDO::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public List<FlowAuditLogVO> findByBusinessTypeAndTarget(
      String businessType, String targetId, int offset, int limit) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLogDO>()
                .eq(FlowAuditLogDO::getBusinessType, businessType)
                .eq(FlowAuditLogDO::getTargetId, targetId)
                .orderByDesc(FlowAuditLogDO::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countByBusinessTypeAndActions(
      String businessType,
      List<String> actions,
      String tenantId,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    return auditLogMapper.selectCount(
        new LambdaQueryWrapper<FlowAuditLogDO>()
            .eq(FlowAuditLogDO::getBusinessType, businessType)
            .eq(tenantId != null, FlowAuditLogDO::getTenantId, tenantId)
            .in(FlowAuditLogDO::getAction, actions)
            .ge(startTime != null, FlowAuditLogDO::getCreatedAt, startTime)
            .le(endTime != null, FlowAuditLogDO::getCreatedAt, endTime));
  }
}
