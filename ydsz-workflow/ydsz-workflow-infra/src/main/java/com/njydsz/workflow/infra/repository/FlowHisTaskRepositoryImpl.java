package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowHisTaskDO;
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
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FlowHisTaskRepositoryImpl implements FlowHisTaskRepository {

  private final FlowHisTaskMapper hisTaskMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowHisTaskVO save(FlowHisTaskVO vo) {
    FlowHisTaskDO entity = converter.entityToDO(vo);
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
            new LambdaQueryWrapper<FlowHisTaskDO>()
                .eq(FlowHisTaskDO::getInstanceId, instanceId)
                .eq(FlowHisTaskDO::getDeleted, 0)
                .orderByDesc(FlowHisTaskDO::getOperatedAt)));
  }

  @Override
  public List<Map<String, Object>> listPassedNodes(String instanceId) {
    return hisTaskMapper.listPassedNodes(instanceId);
  }

  @Override
  public List<FlowHisTaskVO> findByInstanceAndNode(String instanceId, String nodeCode) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTaskDO>()
                .eq(FlowHisTaskDO::getInstanceId, instanceId)
                .eq(FlowHisTaskDO::getNodeCode, nodeCode)
                .eq(FlowHisTaskDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    hisTaskMapper.deleteById(id);
  }

  @Override
  public List<FlowHisTaskVO> findByAssignee(String userId, int limit) {
    return converter.flowHisTaskListToVO(
        hisTaskMapper.selectList(
            new LambdaQueryWrapper<FlowHisTaskDO>()
                .eq(FlowHisTaskDO::getAssigneeId, userId)
                .orderByDesc(FlowHisTaskDO::getFinishAt)
                .last("LIMIT " + limit)));
  }
}
