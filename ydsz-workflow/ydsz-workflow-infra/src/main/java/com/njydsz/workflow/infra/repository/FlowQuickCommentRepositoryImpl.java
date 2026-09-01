package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowQuickCommentRepository;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowQuickComment;
import com.njydsz.workflow.infra.mapper.FlowQuickCommentMapper;

/**
 * 审批常用语仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowQuickCommentRepository} 接口，封装 FlowQuickCommentMapper 数据访问细节。
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
public class FlowQuickCommentRepositoryImpl implements FlowQuickCommentRepository {

  private final FlowQuickCommentMapper quickCommentMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowQuickCommentVO save(FlowQuickCommentVO vo) {
    FlowQuickComment entity = converter.entityToEntity(vo);
    quickCommentMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowQuickCommentVO> findById(String id) {
    return Optional.ofNullable(quickCommentMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public FlowQuickCommentVO update(FlowQuickCommentVO vo) {
    FlowQuickComment entity = converter.entityToEntity(vo);
    quickCommentMapper.updateById(entity);
    return vo;
  }

  @Override
  public void deleteById(String id) {
    quickCommentMapper.deleteById(id);
  }

  @Override
  public List<FlowQuickCommentVO> findActiveByUser(String userId, String tenantId) {
    return converter.flowQuickCommentListToVO(
        quickCommentMapper.selectList(
            new LambdaQueryWrapper<FlowQuickComment>()
                .eq(FlowQuickComment::getUserId, userId)
                .eq(FlowQuickComment::getTenantId, tenantId)
                .eq(FlowQuickComment::getDeleted, 0)));
  }

  @Override
  public List<FlowQuickCommentVO> findActiveSystemByTenant(String tenantId) {
    return converter.flowQuickCommentListToVO(
        quickCommentMapper.selectList(
            new LambdaQueryWrapper<FlowQuickComment>()
                .eq(FlowQuickComment::getIsSystem, 1)
                .eq(FlowQuickComment::getTenantId, tenantId)
                .eq(FlowQuickComment::getDeleted, 0)));
  }
}
