package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowSkipRepository;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowSkip;
import com.njydsz.workflow.infra.mapper.FlowSkipMapper;

/**
 * 节点跳转仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowSkipRepository} 接口，封装 FlowSkipMapper 数据访问细节。
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
public class FlowSkipRepositoryImpl implements FlowSkipRepository {

  private final FlowSkipMapper skipMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowSkipVO save(FlowSkipVO vo) {
    FlowSkip entity = converter.entityToEntity(vo);
    skipMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowSkipVO> findById(String id) {
    return Optional.ofNullable(skipMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowSkipVO> findByInstanceId(String instanceId) {
    return converter.flowSkipListToVO(
        skipMapper.selectList(
            new LambdaQueryWrapper<FlowSkip>()
                .eq(FlowSkip::getDefinitionId, instanceId)
                .eq(FlowSkip::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    skipMapper.deleteById(id);
  }

  @Override
  public FlowSkipVO update(FlowSkipVO vo) {
    FlowSkip entity = converter.entityToEntity(vo);
    skipMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowSkipVO> findByDefinitionIdAndNodeCode(String definitionId, String nodeCode) {
    return converter.flowSkipListToVO(
        skipMapper.selectList(
            new LambdaQueryWrapper<FlowSkip>()
                .eq(FlowSkip::getDefinitionId, definitionId)
                .eq(FlowSkip::getNextNodeCode, nodeCode)
                .eq(FlowSkip::getDeleted, 0)));
  }

  @Override
  public List<FlowSkipVO> findByDefinitionId(String definitionId) {
    return converter.flowSkipListToVO(
        skipMapper.selectList(
            new LambdaQueryWrapper<FlowSkip>()
                .eq(FlowSkip::getDefinitionId, definitionId)
                .eq(FlowSkip::getDeleted, 0)));
  }

  @Override
  public int deleteByDefinitionId(String definitionId) {
    return skipMapper.deleteByDefinitionId(definitionId);
  }
}
