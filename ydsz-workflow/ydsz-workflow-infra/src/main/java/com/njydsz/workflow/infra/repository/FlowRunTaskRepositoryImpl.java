package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import com.njydsz.workflow.infra.entity.FlowRunTask;
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

  // ============================== 排序方向常量 ==============================

  /** 排序方向：升序 */
  private static final String ORDER_DIRECTION_ASC = "ASC";

  /** 排序方向：降序 */
  private static final String ORDER_DIRECTION_DESC = "DESC";

  private final FlowRunTaskMapper taskMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowRunTaskVO save(FlowRunTaskDTO dto) {
    FlowRunTask entity = converter.dtoToEntity(dto);
    taskMapper.insert(entity);
    // 转换回 VO 返回
    return converter.entityToVO(entity);
  }

  @Override
  public Optional<FlowRunTaskVO> findById(String id) {
    return Optional.ofNullable(taskMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowRunTaskVO> findByIds(Collection<String> ids, String tenantId) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    LambdaQueryWrapper<FlowRunTask> wrapper = new LambdaQueryWrapper<FlowRunTask>()
        .in(FlowRunTask::getId, ids)
        .eq(StringUtils.hasText(tenantId), FlowRunTask::getTenantId, tenantId)
        .eq(FlowRunTask::getDeleted, 0);
    return converter.flowRunTaskListToVO(taskMapper.selectList(wrapper));
  }

  @Override
  public void deleteById(String id) {
    taskMapper.deleteById(id);
  }

  @Override
  public List<FlowRunTaskVO> findPendingByInstance(String instanceId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getInstanceId, instanceId)
                .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTask::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findPendingByNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getInstanceId, instanceId)
                .eq(FlowRunTask::getNodeCode, nodeCode)
                .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTask::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findPendingByAssignee(String assigneeId, int offset, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getAssigneeId, assigneeId)
                .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTask::getDeleted, 0)
                .orderByDesc(FlowRunTask::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countPendingByAssignee(String assigneeId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getAssigneeId, assigneeId)
            .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
            .eq(FlowRunTask::getDeleted, 0));
  }

  @Override
  public int freezeByInstance(String instanceId) {
    FlowRunTask update = new FlowRunTask();
    update.setTaskStatus(TASK_STATUS_FROZEN);
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getInstanceId, instanceId)
            .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED));
  }

  @Override
  public int unfreezeByInstance(String instanceId) {
    FlowRunTask update = new FlowRunTask();
    update.setTaskStatus(TASK_STATUS_PENDING);
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getInstanceId, instanceId)
            .eq(FlowRunTask::getTaskStatus, TASK_STATUS_FROZEN));
  }

  @Override
  public int updateStatusByInstance(String instanceId, String taskStatus) {
    FlowRunTask update = new FlowRunTask();
    update.setTaskStatus(taskStatus);
    return taskMapper.update(
        update,
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getInstanceId, instanceId)
            .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING));
  }

  @Override
  public FlowRunTaskVO update(FlowRunTaskVO vo) {
    FlowRunTask entity = converter.entityToEntity(vo);
    taskMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowRunTaskVO> findByInstanceId(String instanceId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getInstanceId, instanceId)
                .eq(FlowRunTask::getDeleted, 0)
                .orderByDesc(FlowRunTask::getCreatedAt)));
  }

  @Override
  public List<FlowRunTaskVO> findTodoByAssignee(String userId, String tenantId, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getAssigneeId, userId)
                .eq(FlowRunTask::getTenantId, tenantId)
                .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
                .eq(FlowRunTask::getDeleted, 0)
                .orderByDesc(FlowRunTask::getCreatedAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public List<FlowRunTaskVO> findByCondition(FlowTaskQuery query) {
    LambdaQueryWrapper<FlowRunTask> wrapper = new LambdaQueryWrapper<FlowRunTask>()
        .eq(query.getFlowCode() != null, FlowRunTask::getFlowCode, query.getFlowCode())
        .eq(query.getInstanceId() != null, FlowRunTask::getInstanceId, query.getInstanceId())
        .eq(query.getNodeCode() != null, FlowRunTask::getNodeCode, query.getNodeCode())
        .eq(query.getAssigneeId() != null, FlowRunTask::getAssigneeId, query.getAssigneeId())
        .eq(query.getTaskStatus() != null, FlowRunTask::getTaskStatus, query.getTaskStatus())
        .eq(query.getBusinessType() != null, FlowRunTask::getBusinessType, query.getBusinessType())
        .eq(query.getBusinessId() != null, FlowRunTask::getBusinessId, query.getBusinessId())
        .eq(query.getPriority() != null, FlowRunTask::getPriority, query.getPriority())
        .ge(query.getCreatedAtFrom() != null, FlowRunTask::getCreatedAt, query.getCreatedAtFrom())
        .le(query.getCreatedAtTo() != null, FlowRunTask::getCreatedAt, query.getCreatedAtTo())
        .ge(query.getDueAtFrom() != null, FlowRunTask::getDueAt, query.getDueAtFrom())
        .le(query.getDueAtTo() != null, FlowRunTask::getDueAt, query.getDueAtTo())
        .eq(FlowRunTask::getDeleted, 0);

    // 排序处理
    if (ORDER_DIRECTION_ASC.equalsIgnoreCase(query.getOrderDirection())) {
      wrapper.orderByAsc(
          query.getOrderBy() != null ? FlowRunTask::getCreatedAt : FlowRunTask::getCreatedAt);
    } else {
      wrapper.orderByDesc(FlowRunTask::getCreatedAt);
    }

    // 分页
    if (query.getLimit() > 0) {
      wrapper.last("LIMIT " + query.getLimit() + " OFFSET " + query.getOffset());
    }

    return converter.flowRunTaskListToVO(taskMapper.selectList(wrapper));
  }

  @Override
  public int updateStatusByCondition(
      String instanceId, String nodeCode, String fromStatus, String toStatus) {
    FlowRunTask update = new FlowRunTask();
    update.setTaskStatus(toStatus);
    LambdaQueryWrapper<FlowRunTask> wrapper = new LambdaQueryWrapper<FlowRunTask>()
        .eq(FlowRunTask::getInstanceId, instanceId)
        .eq(nodeCode != null, FlowRunTask::getNodeCode, nodeCode)
        .eq(FlowRunTask::getTaskStatus, fromStatus);
    return taskMapper.update(update, wrapper);
  }

  @Override
  public long countByStatusIn(List<String> statuses) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTask>()
            .in(FlowRunTask::getTaskStatus, statuses)
            .eq(FlowRunTask::getDeleted, 0));
  }

  @Override
  public long countOverdue() {
    return taskMapper.countOverdue(null, null);
  }

  @Override
  public long countPending() {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING)
            .eq(FlowRunTask::getDeleted, 0));
  }

  @Override
  public List<FlowRunTaskVO> findOverdueTasks(LocalDateTime thresholdTime, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getDeleted, 0)
                .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED)
                .le(FlowRunTask::getCreatedAt, thresholdTime)
                .last("LIMIT " + limit)));
  }

  @Override
  public List<FlowRunTaskVO> selectSlaCandidates(int limit) {
    List<FlowRunTask> candidates = taskMapper.selectSlaCandidates(limit);
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
    taskMapper.completeTask(taskId, taskStatus, FLOW_TIMER_COMMENT, finishAt, durationMs);
  }

  @Override
  public int completeTaskWithComment(
      String taskId, String taskStatus, String comment, LocalDateTime finishAt, Long durationMs) {
    // 返回受影响行数（CAS 并发防护：0=已被并发处理）
    return taskMapper.completeTask(taskId, taskStatus, comment, finishAt, durationMs);
  }

  @Override
  public void cancelTask(String taskId, String taskStatus, String comment) {
    taskMapper.cancelTask(taskId, taskStatus, comment);
  }

  @Override
  public List<FlowRunTaskVO> findByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getInstanceId, instanceId)
                .eq(FlowRunTask::getNodeCode, nodeCode)
                .eq(FlowRunTask::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findCompletedByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getInstanceId, instanceId)
                .eq(FlowRunTask::getNodeCode, nodeCode)
                .in(FlowRunTask::getTaskStatus, TASK_STATUS_COMPLETED, TASK_STATUS_REJECTED)
                .eq(FlowRunTask::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> selectTodoByAssignee(String assigneeId, String tenantId) {
    List<FlowRunTask> list = taskMapper.selectTodoByAssignee(assigneeId, tenantId);
    return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
  }

@Override
public List<FlowRunTaskVO> selectTodoByAssigneePage(String assigneeId, String tenantId, int offset, int limit) {
List<FlowRunTask> list = taskMapper.selectTodoByAssigneePage(assigneeId, tenantId, offset, limit);
return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
}

@Override
public List<FlowRunTaskVO> selectTodoByAssigneeCursor(
    String assigneeId,
    String tenantId,
    Integer lastPriority,
    LocalDateTime lastCreatedAt,
    String lastId,
    int limit) {
List<FlowRunTask> list =
    taskMapper.selectTodoByAssigneeCursor(assigneeId, tenantId, lastPriority, lastCreatedAt, lastId, limit);
return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
}

@Override
public long countTodoByAssignee(String assigneeId, String tenantId) {
    return taskMapper.countTodoByAssignee(assigneeId, tenantId);
  }

  @Override
  public List<FlowRunTaskVO> selectOverdue(String assigneeId, String tenantId, int limit) {
    List<FlowRunTask> list = taskMapper.selectOverdue(assigneeId, tenantId, limit);
    return list == null ? Collections.emptyList() : converter.flowRunTaskListToVO(list);
  }

  @Override
  public long countOverdueByAssignee(String assigneeId, String tenantId) {
    return taskMapper.countOverdue(assigneeId, tenantId);
  }

  @Override
  public long countPendingByTenantId(String tenantId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getTenantId, tenantId)
            .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED));
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
  public int incrementApproveFinished(String taskId) {
    // GAP-A1: 原子自增受影响行数直接透出，0 = 任务不存在或计数已饱和
    return taskMapper.incrementApproveFinished(taskId);
  }

  @Override
  public int incrementApproveWeight(String taskId, int weight) {
    // GAP-A1: 权重原子累加，确保并发投票加和精确
    return taskMapper.incrementApproveWeight(taskId, weight);
  }

  @Override
  public List<FlowRunTaskVO> findPendingTasksByAssignee(String assigneeId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getAssigneeId, assigneeId)
                .eq(FlowRunTask::getDeleted, 0)
                .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED)));
  }

  @Override
  public void updateApproveFinished(String taskId, int approveFinished) {
    taskMapper.updateApproveFinished(taskId, approveFinished);
  }

  @Override
  public List<FlowRunTaskVO> selectPendingByAssignee(String assigneeId, String flowCode, String tenantId) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(FlowRunTask::getAssigneeId, assigneeId)
                .eq(FlowRunTask::getDeleted, 0)
                .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED)
                .eq(StringUtils.hasText(flowCode), FlowRunTask::getFlowCode, flowCode)
                .eq(StringUtils.hasText(tenantId), FlowRunTask::getTenantId, tenantId)));
  }

  @Override
  public List<FlowRunTaskVO> findStuckTasks(String tenantId, LocalDateTime threshold, int limit) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTask>()
                .eq(tenantId != null, FlowRunTask::getTenantId, tenantId)
                .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED)
                .lt(FlowRunTask::getCreatedAt, threshold)
                .eq(FlowRunTask::getDeleted, 0)
                .orderByAsc(FlowRunTask::getCreatedAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public long countOverdueByTenantId(String tenantId) {
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTask>()
            .eq(FlowRunTask::getTenantId, tenantId)
            .eq(FlowRunTask::getDeleted, 0)
            .in(FlowRunTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_CLAIMED)
            .lt(FlowRunTask::getDueAt, LocalDateTime.now()));
  }
}
