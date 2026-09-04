package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;

/**
 * 流程节点仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowNodeRepository} 接口，封装 FlowNodeMapper 数据访问细节。
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
public class FlowNodeRepositoryImpl implements FlowNodeRepository {

  private final FlowNodeMapper nodeMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowNodeVO save(FlowNodeVO vo) {
    FlowNode entity = converter.entityToEntity(vo);
    nodeMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public List<FlowNodeVO> saveBatch(List<FlowNodeVO> nodes) {
    List<FlowNode> entities = nodes.stream().map(converter::entityToEntity).toList();
    entities.forEach(nodeMapper::insert);
    return nodes;
  }

  @Override
  public Optional<FlowNodeVO> findById(String id) {
    return Optional.ofNullable(nodeMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowNodeVO> findByCode(String definitionId, String nodeCode) {
    return nodeMapper
        .selectList(
            new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getDefinitionId, definitionId)
                .eq(FlowNode::getNodeCode, nodeCode)
                .eq(FlowNode::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowNodeVO> findByDefinitionId(String definitionId) {
    return converter.flowNodeListToVO(
        nodeMapper.selectList(
            new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getDefinitionId, definitionId)
                .eq(FlowNode::getDeleted, 0)));
  }

  @Override
  public void deleteByDefinitionId(String definitionId) {
    nodeMapper.delete(
        new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getDefinitionId, definitionId));
  }

  @Override
  public void deleteById(String id) {
    nodeMapper.deleteById(id);
  }
}
