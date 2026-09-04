package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
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
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class FlowAuditLogRepositoryImpl implements FlowAuditLogRepository {

  private final FlowAuditLogMapper auditLogMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAuditLogVO save(FlowAuditLogVO vo) {
    FlowAuditLog entity = converter.entityToEntity(vo);
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
            new LambdaQueryWrapper<FlowAuditLog>()
                .eq(FlowAuditLog::getInstanceId, instanceId)
                .eq(FlowAuditLog::getDeleted, 0)
                .orderByDesc(FlowAuditLog::getOperatedAt)));
  }

  @Override
  public List<FlowAuditLogVO> findByInstanceIdAndAction(String instanceId, String action) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLog>()
                .eq(FlowAuditLog::getInstanceId, instanceId)
                .eq(FlowAuditLog::getAction, action)
                .eq(FlowAuditLog::getDeleted, 0)
                .orderByDesc(FlowAuditLog::getOperatedAt)));
  }

  @Override
  public List<FlowAuditLogVO> findByTaskId(String taskId) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLog>()
                .eq(FlowAuditLog::getTaskId, taskId)
                .eq(FlowAuditLog::getDeleted, 0)
                .orderByDesc(FlowAuditLog::getOperatedAt)));
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
            new LambdaQueryWrapper<FlowAuditLog>()
                .eq(FlowAuditLog::getBusinessType, businessType)
                .eq(FlowAuditLog::getOperatorId, operatorId)
                .orderByDesc(FlowAuditLog::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public List<FlowAuditLogVO> findByBusinessTypeAndTarget(
      String businessType, String targetId, int offset, int limit) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectList(
            new LambdaQueryWrapper<FlowAuditLog>()
                .eq(FlowAuditLog::getBusinessType, businessType)
                .eq(FlowAuditLog::getTargetId, targetId)
                .orderByDesc(FlowAuditLog::getCreatedAt)
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
        new LambdaQueryWrapper<FlowAuditLog>()
            .eq(FlowAuditLog::getBusinessType, businessType)
            .eq(tenantId != null, FlowAuditLog::getTenantId, tenantId)
            .in(FlowAuditLog::getAction, actions)
            .ge(startTime != null, FlowAuditLog::getCreatedAt, startTime)
            .le(endTime != null, FlowAuditLog::getCreatedAt, endTime));
  }

  @Override
  public List<FlowAuditLogVO> findCountersignByInstance(
      String instanceId, List<String> actions, int offset, int limit) {
    return converter.flowAuditLogListToVO(
        auditLogMapper.selectCountersignByInstance(instanceId, actions, offset, limit));
  }

  @Override
  public long countCountersignByInstance(String instanceId, List<String> actions) {
    return auditLogMapper.countCountersignByInstance(instanceId, actions);
  }
}
