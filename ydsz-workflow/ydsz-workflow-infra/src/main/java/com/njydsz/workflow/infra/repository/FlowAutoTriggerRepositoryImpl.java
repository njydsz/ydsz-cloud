package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowAutoTriggerRepository;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowAutoTrigger;
import com.njydsz.workflow.infra.mapper.FlowAutoTriggerMapper;

/**
 * 自动触发仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowAutoTriggerRepository} 接口，封装 FlowAutoTriggerMapper 数据访问细节。
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
public class FlowAutoTriggerRepositoryImpl implements FlowAutoTriggerRepository {

  private final FlowAutoTriggerMapper autoTriggerMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAutoTriggerVO save(FlowAutoTriggerVO vo) {
    FlowAutoTrigger entity = converter.entityToEntity(vo);
    autoTriggerMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowAutoTriggerVO> findById(String id) {
    return Optional.ofNullable(autoTriggerMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowAutoTriggerVO> findByFlowCode(String flowCode) {
    return converter.flowAutoTriggerListToVO(
        autoTriggerMapper.selectList(
            new LambdaQueryWrapper<FlowAutoTrigger>()
                .eq(FlowAutoTrigger::getSourceFlowCode, flowCode)
                .eq(FlowAutoTrigger::getDeleted, 0)));
  }

  @Override
  public List<FlowAutoTriggerVO> findByTriggerType(String triggerType) {
    return converter.flowAutoTriggerListToVO(
        autoTriggerMapper.selectList(
            new LambdaQueryWrapper<FlowAutoTrigger>()
                .eq(FlowAutoTrigger::getConditionExpression, triggerType)
                .eq(FlowAutoTrigger::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    autoTriggerMapper.deleteById(id);
  }

  @Override
  public FlowAutoTriggerVO update(FlowAutoTriggerVO vo) {
    FlowAutoTrigger entity = converter.entityToEntity(vo);
    autoTriggerMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowAutoTriggerVO> findEnabledBySourceFlowCode(String sourceFlowCode) {
    return converter.flowAutoTriggerListToVO(
        autoTriggerMapper.selectList(
            new LambdaQueryWrapper<FlowAutoTrigger>()
                .eq(FlowAutoTrigger::getSourceFlowCode, sourceFlowCode)
                .eq(FlowAutoTrigger::getEnabled, 1)
                .eq(FlowAutoTrigger::getDeleted, 0)));
  }

  @Override
  public void deleteBySourceFlowCode(String sourceFlowCode) {
    autoTriggerMapper.delete(
        new LambdaQueryWrapper<FlowAutoTrigger>()
            .eq(FlowAutoTrigger::getSourceFlowCode, sourceFlowCode));
  }

  @Override
  public List<FlowAutoTriggerVO> findAllOrderBySort() {
    return converter.flowAutoTriggerListToVO(
        autoTriggerMapper.selectList(
            new LambdaQueryWrapper<FlowAutoTrigger>()
                .eq(FlowAutoTrigger::getDeleted, 0)
                .orderByAsc(FlowAutoTrigger::getSortOrder)
                .orderByAsc(FlowAutoTrigger::getId)));
  }
}
