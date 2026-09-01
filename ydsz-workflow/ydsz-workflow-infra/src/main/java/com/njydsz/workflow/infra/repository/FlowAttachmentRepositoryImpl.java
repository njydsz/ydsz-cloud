package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowAttachmentRepository;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowAttachment;
import com.njydsz.workflow.infra.mapper.FlowAttachmentMapper;

/**
 * 附件仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowAttachmentRepository} 接口，封装 FlowAttachmentMapper 数据访问细节。
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
public class FlowAttachmentRepositoryImpl implements FlowAttachmentRepository {

  private final FlowAttachmentMapper attachmentMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowAttachmentVO save(FlowAttachmentVO vo) {
    FlowAttachment entity = converter.entityToEntity(vo);
    attachmentMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowAttachmentVO> findById(String id) {
    return Optional.ofNullable(attachmentMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowAttachmentVO> findByInstanceId(String instanceId) {
    return converter.flowAttachmentListToVO(
        attachmentMapper.selectList(
            new LambdaQueryWrapper<FlowAttachment>()
                .eq(FlowAttachment::getInstanceId, instanceId)
                .eq(FlowAttachment::getDeleted, 0)));
  }

  @Override
  public List<FlowAttachmentVO> findByTaskId(String taskId) {
    return converter.flowAttachmentListToVO(
        attachmentMapper.selectList(
            new LambdaQueryWrapper<FlowAttachment>()
                .eq(FlowAttachment::getTaskId, taskId)
                .eq(FlowAttachment::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    attachmentMapper.deleteById(id);
  }

  @Override
  public FlowAttachmentVO update(FlowAttachmentVO vo) {
    FlowAttachment entity = converter.entityToEntity(vo);
    attachmentMapper.updateById(entity);
    return vo;
  }
}
