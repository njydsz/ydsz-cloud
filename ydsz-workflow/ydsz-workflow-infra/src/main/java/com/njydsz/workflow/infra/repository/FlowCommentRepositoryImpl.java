package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowCommentRepository;
import com.njydsz.workflow.domain.vo.FlowCommentVO;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.entity.FlowComment;
import com.njydsz.workflow.infra.mapper.FlowCommentMapper;

/**
 * 审批意见仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowCommentRepository} 接口，封装 FlowCommentMapper 数据访问细节。
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
public class FlowCommentRepositoryImpl implements FlowCommentRepository {

  private final FlowCommentMapper commentMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowCommentVO save(FlowCommentVO vo) {
    FlowComment entity = converter.entityToEntity(vo);
    commentMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowCommentVO> findById(String id) {
    return Optional.ofNullable(commentMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowCommentVO> findByInstanceId(String instanceId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getInstanceId, instanceId)
                .eq(FlowComment::getDeleted, 0)
                .orderByDesc(FlowComment::getCreatedAt)));
  }

  @Override
  public List<FlowCommentVO> findByTaskId(String taskId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getTaskId, taskId)
                .eq(FlowComment::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    commentMapper.deleteById(id);
  }

  @Override
  public FlowCommentVO update(FlowCommentVO vo) {
    FlowComment entity = converter.entityToEntity(vo);
    commentMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowCommentVO> findRootComments(String instanceId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getInstanceId, instanceId)
                .isNull(FlowComment::getParentCommentId)
                .eq(FlowComment::getDeleted, 0)
                .orderByAsc(FlowComment::getCreatedAt)));
  }

  @Override
  public List<FlowCommentVO> findReplies(String commentId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getParentCommentId, commentId)
                .eq(FlowComment::getDeleted, 0)
                .orderByAsc(FlowComment::getCreatedAt)));
  }

  @Override
  public List<FlowCommentVO> findByInstanceAndTenant(String tenantId, String instanceId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getTenantId, tenantId)
                .eq(FlowComment::getInstanceId, instanceId)
                .eq(FlowComment::getDeleted, 0)
                .orderByAsc(FlowComment::getCreatedAt)));
  }

  @Override
  public List<FlowCommentVO> findRootCommentsByTenant(String tenantId, String instanceId) {
    return converter.flowCommentListToVO(
        commentMapper.selectList(
            new LambdaQueryWrapper<FlowComment>()
                .eq(FlowComment::getTenantId, tenantId)
                .eq(FlowComment::getInstanceId, instanceId)
                .isNull(FlowComment::getParentCommentId)
                .eq(FlowComment::getDeleted, 0)
                .orderByAsc(FlowComment::getCreatedAt)));
  }
}
