package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowCcRepository;
import com.njydsz.workflow.domain.vo.FlowCcVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowCc;
import com.njydsz.workflow.infra.mapper.FlowCcMapper;

/**
 * 抄送仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowCcRepository} 接口，封装 FlowCcMapper 数据访问细节。
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
public class FlowCcRepositoryImpl implements FlowCcRepository {

  private final FlowCcMapper ccMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowCcVO save(FlowCcVO vo) {
    FlowCc entity = converter.entityToEntity(vo);
    ccMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public List<FlowCcVO> saveBatch(List<FlowCcVO> ccList) {
    List<FlowCc> entities = ccList.stream().map(converter::entityToEntity).toList();
    entities.forEach(ccMapper::insert);
    return ccList;
  }

  @Override
  public Optional<FlowCcVO> findById(String id) {
    return Optional.ofNullable(ccMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowCcVO> findByInstanceId(String instanceId) {
    return converter.flowCcListToVO(
        ccMapper.selectList(
            new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getInstanceId, instanceId)
                .eq(FlowCc::getDeleted, 0)));
  }

  @Override
  public List<FlowCcVO> findByReceiverId(String receiverId, int offset, int limit) {
    return converter.flowCcListToVO(
        ccMapper.selectList(
            new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getReceiverId, receiverId)
                .eq(FlowCc::getDeleted, 0)
                .orderByDesc(FlowCc::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public void deleteById(String id) {
    ccMapper.deleteById(id);
  }

  @Override
  public FlowCcVO update(FlowCcVO vo) {
    FlowCc entity = converter.entityToEntity(vo);
    ccMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowCcVO> findCcByUserPage(String userId, String tenantId, int offset, int limit) {
    return converter.flowCcListToVO(
        ccMapper.selectList(
            new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getCcUserId, userId)
                .eq(FlowCc::getTenantId, tenantId)
                .eq(FlowCc::getDeleted, 0)
                .orderByDesc(FlowCc::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countCcByUser(String userId, String tenantId) {
    return ccMapper.selectCount(
        new LambdaQueryWrapper<FlowCc>()
            .eq(FlowCc::getCcUserId, userId)
            .eq(FlowCc::getTenantId, tenantId)
            .eq(FlowCc::getDeleted, 0));
  }

  @Override
  public void markRead(String id) {
    FlowCc update = new FlowCc();
    update.setReadStatus("READ");
    update.setReadAt(LocalDateTime.now());
    update.setId(id);
    ccMapper.updateById(update);
  }

  @Override
  public int markRead(String id, String userId, LocalDateTime readAt) {
    FlowCc update = new FlowCc();
    update.setReadStatus("READ");
    update.setReadAt(readAt);
    return ccMapper.update(
        update,
        new LambdaQueryWrapper<FlowCc>()
            .eq(FlowCc::getId, id)
            .eq(FlowCc::getCcUserId, userId));
  }

  @Override
  public int markAllRead(String tenantId, String userId, LocalDateTime readAt) {
    FlowCc update = new FlowCc();
    update.setReadStatus("READ");
    update.setReadAt(readAt);
    return ccMapper.update(
        update,
        new LambdaQueryWrapper<FlowCc>()
            .eq(FlowCc::getTenantId, tenantId)
            .eq(FlowCc::getCcUserId, userId)
            .eq(FlowCc::getReadStatus, "UNREAD"));
  }

  @Override
  public long countUnread(String userId, String tenantId) {
    return ccMapper.selectCount(
        new LambdaQueryWrapper<FlowCc>()
            .eq(FlowCc::getCcUserId, userId)
            .eq(FlowCc::getTenantId, tenantId)
            .eq(FlowCc::getReadStatus, "UNREAD")
            .eq(FlowCc::getDeleted, 0));
  }

  @Override
  public List<FlowCcVO> findCcByUserPage(
      String userId, String tenantId, String readStatus, String flowCode, int offset, int limit) {
    return converter.flowCcListToVO(
        ccMapper.selectList(
            new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getCcUserId, userId)
                .eq(FlowCc::getTenantId, tenantId)
                .eq(readStatus != null, FlowCc::getReadStatus, readStatus)
                .eq(flowCode != null, FlowCc::getFlowCode, flowCode)
                .eq(FlowCc::getDeleted, 0)
                .orderByDesc(FlowCc::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countCcByUser(
      String userId, String tenantId, String readStatus, String flowCode) {
    return ccMapper.selectCount(
        new LambdaQueryWrapper<FlowCc>()
            .eq(FlowCc::getCcUserId, userId)
            .eq(FlowCc::getTenantId, tenantId)
            .eq(readStatus != null, FlowCc::getReadStatus, readStatus)
            .eq(flowCode != null, FlowCc::getFlowCode, flowCode)
            .eq(FlowCc::getDeleted, 0));
  }

  @Override
  public List<FlowCcVO> findByInstanceIdAndTenant(String tenantId, String instanceId) {
    return converter.flowCcListToVO(
        ccMapper.selectList(
            new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getTenantId, tenantId)
                .eq(FlowCc::getInstanceId, instanceId)
                .eq(FlowCc::getDeleted, 0)));
  }
}
