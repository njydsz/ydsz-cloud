package com.njydsz.workflow.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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

  private final FlowRunTaskMapper taskMapper;

  private final WorkflowConverter converter;

  @Override
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
                .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
                .eq(FlowRunTaskDO::getDeleted, 0)));
  }

  @Override
  public List<FlowRunTaskVO> findPendingByNode(String instanceId, String nodeCode) {
    return converter.flowRunTaskListToVO(
        taskMapper.selectList(
            new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getNodeCode, nodeCode)
                .eq(FlowRunTaskDO::getTaskStatus, "PENDING")
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
}
