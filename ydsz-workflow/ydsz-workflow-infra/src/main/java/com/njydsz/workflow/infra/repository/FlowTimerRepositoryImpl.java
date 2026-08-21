package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowTimerRepository;
import com.njydsz.workflow.domain.vo.FlowTimerVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowTimerDO;
import com.njydsz.workflow.infra.mapper.FlowTimerMapper;

/**
 * 定时器仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowTimerRepository} 接口，封装 FlowTimerMapper 数据访问细节。
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
public class FlowTimerRepositoryImpl implements FlowTimerRepository {

  private final FlowTimerMapper timerMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowTimerVO save(FlowTimerVO vo) {
    FlowTimerDO entity = converter.entityToDO(vo);
    timerMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowTimerVO> findById(String id) {
    return Optional.ofNullable(timerMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowTimerVO> findByTaskId(String taskId) {
    return timerMapper
        .selectList(
            new LambdaQueryWrapper<FlowTimerDO>()
                .eq(FlowTimerDO::getTaskId, taskId)
                .eq(FlowTimerDO::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowTimerVO> findByInstanceId(String instanceId) {
    return converter.flowTimerListToVO(
        timerMapper.selectList(
            new LambdaQueryWrapper<FlowTimerDO>()
                .eq(FlowTimerDO::getInstanceId, instanceId)
                .eq(FlowTimerDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    timerMapper.deleteById(id);
  }

  @Override
  public void deleteByInstanceId(String instanceId) {
    timerMapper.delete(
        new LambdaQueryWrapper<FlowTimerDO>().eq(FlowTimerDO::getInstanceId, instanceId));
  }

  @Override
  public FlowTimerVO update(FlowTimerVO vo) {
    FlowTimerDO entity = converter.entityToDO(vo);
    timerMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowTimerVO> findDueTimers(LocalDateTime now, int limit) {
    return converter.flowTimerListToVO(
        timerMapper.selectList(
            new LambdaQueryWrapper<FlowTimerDO>()
                .le(FlowTimerDO::getFireAt, now)
                .eq(FlowTimerDO::getTimerStatus, "PENDING")
                .eq(FlowTimerDO::getDeleted, 0)
                .orderByAsc(FlowTimerDO::getFireAt)
                .last("LIMIT " + limit)));
  }

  @Override
  public void markFired(String id) {
    FlowTimerDO update = new FlowTimerDO();
    update.setTimerStatus("FIRED");
    update.setFiredAt(LocalDateTime.now());
    update.setId(id);
    timerMapper.updateById(update);
  }

  @Override
  public void cancelByTask(String taskId) {
    FlowTimerDO update = new FlowTimerDO();
    update.setTimerStatus("CANCELLED");
    update.setCancelReason("TASK_COMPLETED");
    timerMapper.update(
        update,
        new LambdaQueryWrapper<FlowTimerDO>()
            .eq(FlowTimerDO::getBoundaryTaskId, taskId)
            .eq(FlowTimerDO::getTimerStatus, "PENDING"));
  }

  @Override
  public List<FlowTimerVO> findByInstanceIdOrderByFireTime(String instanceId) {
    return converter.flowTimerListToVO(
        timerMapper.selectList(
            new LambdaQueryWrapper<FlowTimerDO>()
                .eq(FlowTimerDO::getInstanceId, instanceId)
                .eq(FlowTimerDO::getDeleted, 0)
                .orderByAsc(FlowTimerDO::getFireAt)));
  }

  @Override
  public int cancelByInstance(String instanceId, String reason) {
    FlowTimerDO update = new FlowTimerDO();
    update.setTimerStatus("CANCELLED");
    update.setCancelReason(reason);
    return timerMapper.update(
        update,
        new LambdaQueryWrapper<FlowTimerDO>()
            .eq(FlowTimerDO::getInstanceId, instanceId)
            .eq(FlowTimerDO::getTimerStatus, "PENDING")
            .eq(FlowTimerDO::getDeleted, 0));
  }

  @Override
  public long countPendingByInstance(String instanceId) {
    return timerMapper.selectCount(
        new LambdaQueryWrapper<FlowTimerDO>()
            .eq(FlowTimerDO::getInstanceId, instanceId)
            .eq(FlowTimerDO::getTimerStatus, "PENDING")
            .eq(FlowTimerDO::getDeleted, 0));
  }

  @Override
  public void markSnoozed(String id, LocalDateTime nextTime) {
    FlowTimerDO update = new FlowTimerDO();
    update.setFireAt(nextTime);
    update.setTimerStatus("PENDING");
    update.setId(id);
    timerMapper.updateById(update);
  }

  @Override
  public List<FlowTimerVO> findByInstanceOrderByCreatedAtDesc(String instanceId) {
    return converter.flowTimerListToVO(
        timerMapper.selectList(
            new LambdaQueryWrapper<FlowTimerDO>()
                .eq(FlowTimerDO::getInstanceId, instanceId)
                .eq(FlowTimerDO::getDeleted, 0)
                .orderByDesc(FlowTimerDO::getCreatedAt)));
  }
}
