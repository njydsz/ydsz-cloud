package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.dto.FlowTaskQueryDTO;
import com.njydsz.workflow.domain.query.FlowTaskQuery;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

/**
 * 运行时任务仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowRunTaskRepository} 接口，封装 FlowRunTaskMapper 数据访问细节。
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
public class FlowRunTaskRepositoryImpl implements FlowRunTaskRepository {

  // ============================== 任务状态常量 ==============================

  /** 任务状态：待处理 */
  private static final String TASK_STATUS_PENDING = "PENDING";

  /** 任务状态：已签收 */
  private static final String TASK_STATUS_CLAIMED = "CLAIMED";

  /** 任务状态：已冻结 */
  private static final String TASK_STATUS_FROZEN = "FROZEN";

  /** 任务状态：已完成 */
  private static final String TASK_STATUS_COMPLETED = "COMPLETED";

  /** 任务状态：已驳回 */
  private static final String TASK_STATUS_REJECTED = "REJECTED";

  /** 流程定时器默认审批意见 */
  private static final String FLOW_TIMER_COMMENT = "FLOW_TIMER";

  private final FlowRunTaskMapper taskMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowRunTaskVO save(FlowRunTaskDTO dto) {
    FlowRunTaskDO entity = converter.dtoToDO(dto);
    taskMapper.insert(entity);
    // 转换回 VO 返回
    return converter.entityToVO(entity);
  }

  @Override
  @Deprecated
  public FlowRunTaskVO save(FlowRunTaskVO vo) {
    FlowRunTaskDO entity = converter.entityToDO(vo);
    taskMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowRunTaskVO> findById(String id) {
    return Optional.ofNullable(taskMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public void deleteById(String id) {
    taskMapper.deleteById(id);
  }

  @Override
  public List<FlowRunTaskVO> findPendingByInstance(String instanceId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTaskDO::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findPendingByNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getNodeCode, nodeCode)
                .eq(FlowRunTaskDO::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTaskDO::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findPendingByAssignee(String assigneeId, int offset, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, assigneeId)
                .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
                .eq(FlowRunTaskDO::getDeleted, 0)
                .orderByDesc(FlowRunTaskDO::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countPendingByAssignee(String assigneeId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getAssigneeId, assigneeId)
            .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
            .eq(FlowRunTaskDO::getDeleted, 0));
  }

  @Override
  public int freezeByInstance(String instanceId) {
    FlowRunTaskDO update = new FlowRunTaskDO();
    update.setTaskStatus("FROZEN");
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getInstanceId, instanceId)
            .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED"));
  }

  @Override
  public int unfreezeByInstance(String instanceId) {
    FlowRunTaskDO update = new FlowRunTaskDO();
    update.setTaskStatus("PENDING");
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getInstanceId, instanceId)
            .eq(FlowRunTaskDO::getTaskStatus, "FROZEN"));
  }

  @Override
  public int updateStatusByInstance(String instanceId, String taskStatus) {
    FlowRunTaskDO update = new FlowRunTaskDO();
    update.setTaskStatus(taskStatus);
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getInstanceId, instanceId)
            .eq(FlowRunTaskDO::getTaskStatus, "PENDING"));
  }

  @Override
  public FlowRunTaskVO update(FlowRunTaskVO vo) {
    FlowRunTaskDO entity = converter.entityToDO(vo);
    taskMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowRunTaskVO> findByInstanceId(String instanceId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .orderByDesc(FlowRunTaskDO::getCreatedAt)));
  }

  @Override
  public List<FlowRunTaskVO> findTodoByAssignee(String userId, String tenantId, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, userId)
                .eq(FlowRunTaskDO::getTenantId, tenantId)
                .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
                .eq(FlowRunTaskDO::getDeleted, 0)
                .orderByDesc(FlowRunTaskDO::getCreatedAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public List<FlowRunTaskVO> findByCondition(FlowTaskQuery query) {
    LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
        .eq(query.getFlowCode() != null, FlowRunTaskDO::getFlowCode, query.getFlowCode())
        .eq(query.getInstanceId() != null, FlowRunTaskDO::getInstanceId, query.getInstanceId())
        .eq(query.getNodeCode() != null, FlowRunTaskDO::getNodeCode, query.getNodeCode())
        .eq(query.getAssigneeId() != null, FlowRunTaskDO::getAssigneeId, query.getAssigneeId())
        .eq(query.getTaskStatus() != null, FlowRunTaskDO::getTaskStatus, query.getTaskStatus())
        .eq(query.getBusinessType() != null, FlowRunTaskDO::getBusinessType, query.getBusinessType())
        .eq(query.getBusinessId() != null, FlowRunTaskDO::getBusinessId, query.getBusinessId())
        .eq(query.getPriority() != null, FlowRunTaskDO::getPriority, query.getPriority())
        .ge(query.getCreatedAtFrom() != null, FlowRunTaskDO::getCreatedAt, query.getCreatedAtFrom())
        .le(query.getCreatedAtTo() != null, FlowRunTaskDO::getCreatedAt, query.getCreatedAtTo())
        .ge(query.getDueAtFrom() != null, FlowRunTaskDO::getDueAt, query.getDueAtFrom())
        .le(query.getDueAtTo() != null, FlowRunTaskDO::getDueAt, query.getDueAtTo())
        .eq(FlowRunTaskDO::getDeleted, 0);

    // 排序处理
    if ("ASC".equalsIgnoreCase(query.getOrderDirection())) {
      wrapper.orderByAsc(
          query.getOrderBy() != null ? FlowRunTaskDO::getCreatedAt : FlowRunTaskDO::getCreatedAt);
    } else {
      wrapper.orderByDesc(FlowRunTaskDO::getCreatedAt);
    }

    // 分页
    if (query.getLimit() > 0) {
      wrapper.last("LIMIT " + query.getLimit() + " OFFSET " + query.getOffset());
    }

    return converter.flowRunTaskListToVO(taskMapper.selectList(wrapper));
  }

  @Override
  public List<FlowRunTaskVO> findByCondition(FlowTaskQueryDTO condition) {
    // 委托给新的 findByCondition(FlowTaskQuery) 实现
    return findByCondition(convertToQuery(condition));
  }

  /**
   * 将旧的 FlowTaskQueryDTO 转换为新的 FlowTaskQuery
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  private FlowTaskQuery convertToQuery(FlowTaskQueryDTO dto) {
    FlowTaskQuery query = new FlowTaskQuery();
    query.setTenantId(dto.getTenantId());
    query.setFlowCode(dto.getFlowCode());
    query.setInstanceId(dto.getInstanceId());
    query.setNodeCode(dto.getNodeCode());
    query.setAssigneeId(dto.getAssigneeId());
    query.setTaskStatus(dto.getTaskStatus());
    query.setBusinessType(dto.getBusinessType());
    query.setBusinessId(dto.getBusinessId());
    query.setPriority(dto.getPriority());
    query.setCreatedAtFrom(dto.getCreatedAtFrom());
    query.setCreatedAtTo(dto.getCreatedAtTo());
    query.setDueAtFrom(dto.getDueAtFrom());
    query.setDueAtTo(dto.getDueAtTo());
    query.setOrderBy(dto.getOrderBy());
    query.setOrderDirection(dto.getOrderDirection());
    query.setOffset(dto.getOffset());
    query.setLimit(dto.getLimit());
    return query;
  }

  @Override
  @Deprecated
  public List<FlowRunTaskVO> findByCondition(FlowTaskQueryDTO condition) {
    return findByCondition(convertToQuery(condition));
  }

  @Override
  public int updateStatusByCondition(
      String instanceId, String nodeCode, String fromStatus, String toStatus) {
    FlowRunTaskDO update = new FlowRunTaskDO();
    update.setTaskStatus(toStatus);
    LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
        .eq(FlowRunTaskDO::getInstanceId, instanceId)
        .eq(nodeCode != null, FlowRunTaskDO::getNodeCode, nodeCode)
        .eq(FlowRunTaskDO::getTaskStatus, fromStatus);
    return taskMapper.update(update, wrapper);
  }

  @Override
  public long countByStatusIn(List<String> statuses) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .in(FlowRunTaskDO::getTaskStatus, statuses)
            .eq(FlowRunTaskDO::getDeleted, 0));
  }

  @Override
  public long countOverdue() {
    return taskMapper.countOverdue(null, null);
  }

  @Override
  public long countPending() {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
            .eq(FlowRunTaskDO::getDeleted, 0));
  }

  @Override
  public List<FlowRunTaskVO> findOverdueTasks(LocalDateTime thresholdTime, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
                .le(FlowRunTaskDO::getCreatedAt, thresholdTime)
                .last("LIMIT " + limit)));
  }

  @Override
  public List<FlowRunTaskVO> selectSlaCandidates(int limit) {
    List<FlowRunTaskDO> candidates = taskMapper.selectSlaCandidates(limit);
    return candidates == null ? Collections.emptyList() : converter.flowRunTaskListToVO(candidates);
  }

  @Override
  public void incrementUrgeCount(String taskId, int newUrgeCount, LocalDateTime urgeAt) {
    taskMapper.incrementUrgeCount(taskId, newUrgeCount, urgeAt);
  }

  @Override
  public void markSlaAction(String taskId, String slaAction, int slaEscalated) {
    taskMapper.markSlaAction(taskId, slaAction, slaEscalated);
  }

  @Override
  public void completeTask(String taskId, String taskStatus, LocalDateTime finishAt, Long durationMs) {
    taskMapper.completeTask(taskId, taskStatus, "FLOW_TIMER", finishAt, durationMs);
  }

  @Override
  public void completeTaskWithComment(
      String taskId, String taskStatus, String comment, LocalDateTime finishAt, Long durationMs) {
    taskMapper.completeTask(taskId, taskStatus, comment, finishAt, durationMs);
  }

  @Override
  public void cancelTask(String taskId, String taskStatus, String comment) {
    taskMapper.cancelTask(taskId, taskStatus, comment);
  }

  @Override
  public List<FlowRunTaskVO> findByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getNodeCode, nodeCode)
                .eq(FlowRunTaskDO::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findCompletedByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getNodeCode, nodeCode)
                .in(FlowRunTaskDO::getTaskStatus, "COMPLETED", "REJECTED")
                .eq(FlowRunTaskDO::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> selectTodoByAssignee(String assigneeId, String tenantId) {
    List<FlowRunTaskDO> list = taskMapper.selectTodoByAssignee(assigneeId, tenantId);
    return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
  }

  @Override
  public List<FlowRunTaskVO> selectTodoByAssigneePage(String assigneeId, String tenantId, int offset, int limit) {
    List<FlowRunTaskDO> list = taskMapper.selectTodoByAssigneePage(assigneeId, tenantId, offset, limit);
    return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
  }

  @Override
  public long countTodoByAssignee(String assigneeId, String tenantId) {
    return taskMapper.countTodoByAssignee(assigneeId, tenantId);
  }

  @Override
  public List<FlowRunTaskVO> selectOverdue(String assigneeId, String tenantId, int limit) {
    List<FlowRunTaskDO> list = taskMapper.selectOverdue(assigneeId, tenantId, limit);
    return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
  }

  @Override
  public long countOverdueByAssignee(String assigneeId, String tenantId) {
    return taskMapper.countOverdue(assigneeId, tenantId);
  }

  @Override
  public long countPendingByTenantId(String tenantId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getTenantId, tenantId)
            .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED"));
  }

  @Override
  public List<Map<String, Object>> selectOverdueTopN(String tenantId, int limit) {
    return taskMapper.selectOverdueTopN(tenantId, limit);
  }

  @Override
  public List<Map<String, Object>> selectWorkloadByAssignee(String tenantId, int limit) {
    return taskMapper.selectWorkloadByAssignee(tenantId, limit);
  }

@Override
public void markProcessed(String taskId, String userId, String comment, LocalDateTime processedAt) {
taskMapper.markProcessed(taskId, userId, comment, processedAt);
}

@Override
public List<FlowRunTaskVO> findPendingTasksByAssignee(String assigneeId) {
  return converter.flowRunTaskListToVO(
      taskMapper.selectList(
          new LambdaQueryWrapper<FlowRunTaskDO>()
              .eq(FlowRunTaskDO::getAssigneeId, assigneeId)
              .eq(FlowRunTaskDO::getDeleted, 0)
              .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")));
}

  @Override
  public void updateApproveFinished(String taskId, int approveFinished) {
    taskMapper.updateApproveFinished(taskId, approveFinished);
  }

  @Override
  public List<FlowRunTaskVO> selectPendingByAssignee(String assigneeId, String flowCode, String tenantId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, assigneeId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
                .eq(StringUtils.hasText(flowCode), FlowRunTaskDO::getFlowCode, flowCode)
                .eq(StringUtils.hasText(tenantId), FlowRunTaskDO::getTenantId, tenantId)));
  }

  @Override
  public List<FlowRunTaskVO> findStuckTasks(String tenantId, LocalDateTime threshold, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(tenantId != null, FlowRunTaskDO::getTenantId, tenantId)
                .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
                .lt(FlowRunTaskDO::getCreatedAt, threshold)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .orderByAsc(FlowRunTaskDO::getCreatedAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public long countOverdueByTenantId(String tenantId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .eq(FlowRunTaskDO::getTenantId, tenantId)
            .eq(FlowRunTaskDO::getDeleted, 0)
            .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
            .lt(FlowRunTaskDO::getDueAt, LocalDateTime.now()));
  }
}
