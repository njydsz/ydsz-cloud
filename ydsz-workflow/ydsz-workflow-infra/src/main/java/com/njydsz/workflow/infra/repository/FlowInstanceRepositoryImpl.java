package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.infra.converter.WorkflowRepositoryConverter;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;

/**
 * 流程实例仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowInstanceRepository} 接口，封装 FlowInstanceMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link WorkflowRepositoryConverter} 将 DO 转换为 VO 后返回领域层
 *   <li>CUD 入参 DTO 通过 {@link WorkflowRepositoryConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * <p><b>分层定位：</b>依赖方向为 infra → domain（符合 DDD 依赖倒置原则）， domain 层定义接口契约，infra 层提供适配器实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FlowInstanceRepositoryImpl implements FlowInstanceRepository {

  private final FlowInstanceMapper instanceMapper;

  private final WorkflowRepositoryConverter converter;

  @Override
  public FlowInstanceVO save(FlowInstanceDTO dto) {
    if (dto.getId() == null) {
      // 新增：DTO → entity (忽略 id)，insert 后回填 id 到 DTO
      FlowInstanceDO entity = converter.dtoToEntity(dto);
      instanceMapper.insert(entity);
      dto.setId(entity.getId());
    } else {
      // 更新：DTO → entity (含 id)，按 id 更新
      FlowInstanceDO entity = converter.dtoToEntityWithId(dto);
      instanceMapper.updateById(entity);
    }
    return converter.dtoToVO(dto);
  }

  @Override
  public Optional<FlowInstanceVO> findById(String id) {
    return Optional.ofNullable(instanceMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowInstanceVO> findByBusiness(
      String tenantId, String businessType, String businessId) {
    return Optional.ofNullable(
            instanceMapper.selectByBusiness(tenantId, businessType, businessId))
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowInstanceVO> findByInitiatorId(String initiatorId) {
    return converter.flowInstanceListToVO(
        instanceMapper.selectList(
            new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getInitiatorId, initiatorId)
                .eq(FlowInstanceDO::getDeleted, 0)
                .orderByDesc(FlowInstanceDO::getCreatedAt)));
  }

  @Override
  public List<FlowInstanceVO> selectByInitiator(String initiatorId, String flowCode) {
    return converter.flowInstanceListToVO(
        instanceMapper.selectList(
            new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getInitiatorId, initiatorId)
                .eq(flowCode != null, FlowInstanceDO::getFlowCode, flowCode)
                .eq(FlowInstanceDO::getDeleted, 0)
                .orderByDesc(FlowInstanceDO::getCreatedAt)));
  }

  @Override
  public List<FlowInstanceVO> findChildren(String parentInstanceId) {
    return converter.flowInstanceListToVO(
        instanceMapper.selectList(
            new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getParentInstanceId, parentInstanceId)
                .eq(FlowInstanceDO::getDeleted, 0)));
  }

  @Override
  public long countByStatus(String flowStatus) {
    return instanceMapper.selectCount(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getFlowStatus, flowStatus)
            .eq(FlowInstanceDO::getDeleted, 0));
  }

  @Override
  public List<FlowInstanceVO> findSuspendedBefore(LocalDateTime before, int limit) {
    return converter.flowInstanceListToVO(
        instanceMapper.selectList(
            new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getFlowStatus, "SUSPENDED")
                .le(FlowInstanceDO::getUpdatedAt, before)
                .eq(FlowInstanceDO::getDeleted, 0)
                .last("LIMIT " + limit)));
  }

  @Override
  public void deleteById(String id) {
    instanceMapper.deleteById(id);
  }

  @Override
  public void updateVariable(String id, String variable) {
    instanceMapper.updateVariable(id, variable);
  }

  @Override
  public void updateStatus(
      String id,
      String flowStatus,
      String currentNodeCode,
      String currentNodeName,
      LocalDateTime endAt,
      Long durationMs) {
    instanceMapper.updateStatus(id, flowStatus, currentNodeCode, currentNodeName, endAt, durationMs);
  }

  @Override
  public void updateDueAt(String id, LocalDateTime dueAt) {
    instanceMapper.updateDueAt(id, dueAt);
  }

  @Override
  public List<FlowInstanceVO> findPage(FlowInstancePageQuery query) {
    return converter.flowInstanceListToVO(instanceMapper.selectPage(query));
  }

  @Override
  public long countPage(FlowInstancePageQuery query) {
    return instanceMapper.countPage(query);
  }

  @Override
  public long countRunningByDefinition(String definitionId) {
    return instanceMapper.countRunningByDefinition(definitionId);
  }

  @Override
  public Map<String, Long> countRunningGroupByNode(String definitionId) {
    return instanceMapper.selectRunningGroupByNode(definitionId);
  }

  @Override
  public List<Map<String, Object>> selectRunningGroupByNode(String definitionId) {
    return instanceMapper.selectRunningGroupByNode(definitionId);
  }

  @Override
  public List<Map<String, Object>> selectCountGroupByStatus(String tenantId) {
    return instanceMapper.selectCountGroupByStatus(tenantId);
  }

  @Override
  public Map<String, Object> selectTodayCount(String tenantId) {
    return instanceMapper.selectTodayCount(tenantId);
  }

  @Override
  public List<Map<String, Object>> selectDailyNewCount(String tenantId, LocalDateTime start, LocalDateTime end) {
    return instanceMapper.selectDailyNewCount(tenantId, start, end);
  }

  @Override
  public List<Map<String, Object>> selectDailyCompletedCount(String tenantId, LocalDateTime start, LocalDateTime end) {
    return instanceMapper.selectDailyCompletedCount(tenantId, start, end);
  }

  @Override
  public List<Map<String, Object>> selectFlowTypeDistribution(
      String tenantId, LocalDateTime start, LocalDateTime end) {
    return instanceMapper.selectFlowTypeDistribution(tenantId, start, end);
  }

  @Override
  public List<FlowInstanceVO> findRunningChildrenByParentId(String parentInstanceId) {
    return converter.flowInstanceListToVO(
        instanceMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getParentInstanceId, parentInstanceId)
                .eq(FlowInstanceDO::getFlowStatus, "RUNNING")
                .eq(FlowInstanceDO::getDeleted, 0)));
  }

  @Override
  public List<FlowInstanceVO> findRunningByDefinition(String definitionId, String tenantId) {
    LambdaQueryWrapper<FlowInstanceDO> wrapper = new LambdaQueryWrapper<FlowInstanceDO>()
        .eq(FlowInstanceDO::getDefinitionId, definitionId)
        .eq(FlowInstanceDO::getFlowStatus, "RUNNING")
        .eq(FlowInstanceDO::getDeleted, 0);
    if (tenantId != null) {
      wrapper.eq(FlowInstanceDO::getTenantId, tenantId);
    }
    return converter.flowInstanceListToVO(instanceMapper.selectList(wrapper));
  }

  @Override
  public FlowInstanceVO update(FlowInstanceVO vo) {
    FlowInstanceDO entity = converter.entityToDOWithId(vo);
    instanceMapper.updateById(entity);
    return vo;
  }
}
