package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowHisTask;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;

/**
 * 历史任务仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowHisTaskRepository} 接口，封装 FlowHisTaskMapper 数据访问细节。
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
public class FlowHisTaskRepositoryImpl implements FlowHisTaskRepository {

  private final FlowHisTaskMapper hisTaskMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowHisTaskVO save(FlowHisTaskVO vo) {
    FlowHisTask entity = converter.entityToEntity(vo);
    hisTaskMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowHisTaskVO> findById(String id) {
    return Optional.ofNullable(hisTaskMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowHisTaskVO> findByInstanceId(String instanceId) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTask>()
                .eq(FlowHisTask::getInstanceId, instanceId)
                .eq(FlowHisTask::getDeleted, 0)
                .orderByDesc(FlowHisTask::getFinishAt)));
  }

  @Override
  public List<Map<String, Object>> listPassedNodes(String instanceId) {
    return hisTaskMapper.listPassedNodes(instanceId);
  }

  @Override
  public List<FlowHisTaskVO> findByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTask>()
                .eq(FlowHisTask::getInstanceId, instanceId)
                .eq(FlowHisTask::getNodeCode, nodeCode)
                .eq(FlowHisTask::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    hisTaskMapper.deleteById(id);
  }

  @Override
  public List<FlowHisTaskVO> findByAssignee(String userId, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTask>()
                .eq(FlowHisTask::getAssigneeId, userId)
                .orderByDesc(FlowHisTask::getFinishAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public Map<String, Object> selectOverviewStats(
      String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
    return hisTaskMapper.selectOverviewStats(tenantId, startTime, endTime);
  }

  @Override
  public List<Map<String, Object>> selectApproverEfficiency(
      String tenantId,
      java.time.LocalDateTime startTime,
      java.time.LocalDateTime endTime,
      int limit) {
    return hisTaskMapper.selectApproverEfficiency(tenantId, startTime, endTime, limit);
  }

  @Override
  public List<Map<String, Object>> selectFlowEfficiencyComparison(
      String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
    return hisTaskMapper.selectFlowEfficiencyComparison(tenantId, startTime, endTime);
  }

  @Override
  public List<Map<String, Object>> selectNodeDurationStats(String flowCode, String tenantId) {
    return hisTaskMapper.nodeDurationStats(flowCode, tenantId);
  }

  @Override
  public List<Map<String, Object>> selectApprovalTrend(
      String tenantId,
      java.time.LocalDateTime startTime,
      java.time.LocalDateTime endTime,
      String granularity) {
    return hisTaskMapper.selectApprovalTrend(tenantId, startTime, endTime, granularity);
  }

  @Override
  public List<String> selectCompletedAssigneeIds(String instanceId) {
    return hisTaskMapper.selectCompletedAssigneeIds(instanceId);
  }

  @Override
  public List<FlowHisTaskVO> selectDoneByAssignee(String assigneeId, String tenantId) {
    return converter.flowHisTaskListToVO(hisTaskMapper.selectDoneByAssignee(assigneeId, tenantId));
  }

  @Override
  public List<FlowHisTaskVO> selectDoneByAssigneePage(String assigneeId, String tenantId, int offset, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectDoneByAssigneePage(assigneeId, tenantId, offset, limit));
  }

  @Override
  public List<FlowHisTaskVO> selectDonePage(String assigneeId, String businessType, String flowCode,
      java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
      String tenantId, int offset, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectDonePage(assigneeId, businessType, flowCode, startTime, endTime, tenantId, offset, limit));
  }

  @Override
  public long countDoneByAssignee(String assigneeId, String tenantId) {
    return hisTaskMapper.countDoneByAssignee(assigneeId, tenantId);
  }

  @Override
  public long countDone(String assigneeId, String businessType, String flowCode,
      java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, String tenantId) {
    return hisTaskMapper.countDone(assigneeId, businessType, flowCode, startTime, endTime, tenantId);
  }

  @Override
  public List<FlowHisTaskVO> selectByTimeRange(
      String tenantId, String flowCode, LocalDateTime startTime, LocalDateTime endTime, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTask>()
                .eq(tenantId != null, FlowHisTask::getTenantId, tenantId)
                .eq(flowCode != null, FlowHisTask::getFlowCode, flowCode)
                .ge(startTime != null, FlowHisTask::getFinishAt, startTime)
                .le(endTime != null, FlowHisTask::getFinishAt, endTime)
                .eq(FlowHisTask::getDeleted, 0)
                .orderByDesc(FlowHisTask::getFinishAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public List<FlowHisTaskVO> selectRecentByTenant(String tenantId, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTask>()
                .eq(tenantId != null, FlowHisTask::getTenantId, tenantId)
                .eq(FlowHisTask::getDeleted, 0)
                .orderByDesc(FlowHisTask::getFinishAt)
                .last("LIMIT " + limit)));
  }
}
