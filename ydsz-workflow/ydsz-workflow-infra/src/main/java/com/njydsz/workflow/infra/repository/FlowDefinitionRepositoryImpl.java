package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.dto.FlowDefinitionDTO;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;

/**
 * 流程定义仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowDefinitionRepository} 接口，封装 FlowDefinitionMapper 数据访问细节。
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
public class FlowDefinitionRepositoryImpl implements FlowDefinitionRepository {

  private final FlowDefinitionMapper definitionMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowDefinitionVO save(FlowDefinitionDTO dto) {
    FlowDefinitionDO entity = converter.dtoToDO(dto);
    definitionMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  @Deprecated
  public FlowDefinitionVO save(FlowDefinitionVO vo) {
    FlowDefinitionDO entity = converter.entityToDO(vo);
    definitionMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowDefinitionVO> findById(String id) {
    return Optional.ofNullable(definitionMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowDefinitionVO> findPublished(String flowCode, String version, String tenantId) {
    return definitionMapper
        .selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getStatus, "PUBLISHED")
                .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
                .eq(version != null, FlowDefinitionDO::getFlowVersion, version)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getFlowVersion)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowDefinitionVO> findByFlowCode(String flowCode) {
    return converter.flowDefinitionListToVO(
        definitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getFlowVersion)));
  }

  @Override
  public Optional<FlowDefinitionVO> findByFlowCodeAndVersion(String flowCode, String version) {
    return definitionMapper
        .selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getFlowVersion, version)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public void deleteById(String id) {
    definitionMapper.deleteById(id);
  }

  @Override
  public FlowDefinitionVO update(FlowDefinitionDTO dto) {
    FlowDefinitionDO entity = converter.dtoToDO(dto);
    definitionMapper.updateById(entity);
    return converter.entityToVO(entity);
  }

  @Override
  @Deprecated
  public FlowDefinitionVO update(FlowDefinitionVO vo) {
    FlowDefinitionDO entity = converter.entityToDO(vo);
    definitionMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<FlowDefinitionVO> findPage(
      String flowCode, String flowName, String tenantId, int offset, int limit) {
    return converter.flowDefinitionListToVO(
        definitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(flowCode != null, FlowDefinitionDO::getFlowCode, flowCode)
                .like(flowName != null, FlowDefinitionDO::getFlowName, flowName)
                .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset)));
  }

  @Override
  public long countPage(String flowCode, String flowName, String tenantId) {
    return definitionMapper.selectCount(
        new LambdaQueryWrapper<FlowDefinitionDO>()
            .eq(flowCode != null, FlowDefinitionDO::getFlowCode, flowCode)
            .like(flowName != null, FlowDefinitionDO::getFlowName, flowName)
            .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
            .eq(FlowDefinitionDO::getDeleted, 0));
  }

  @Override
  public Optional<FlowDefinitionVO> findLatestByCode(String flowCode, String tenantId) {
    return definitionMapper
        .selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowDefinitionVO> findEnabledByCategory(String categoryCode, String tenantId) {
    return converter.flowDefinitionListToVO(
        definitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getCategory, categoryCode)
                .eq(FlowDefinitionDO::getTenantId, tenantId)
                .eq(FlowDefinitionDO::getActivityStatus, 1)
                .eq(FlowDefinitionDO::getIsPublish, 1)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt)));
  }

  @Override
  public List<FlowDefinitionVO> findActivePage(
      int pageNo, int pageSize, String category, String flowCode) {
    return converter.flowDefinitionListToVO(
        definitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(StringUtils.hasText(category),
                    FlowDefinitionDO::getCategory, category)
                .like(StringUtils.hasText(flowCode),
                    FlowDefinitionDO::getFlowCode, flowCode)
                .eq(FlowDefinitionDO::getActivityStatus, 1)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt)
                .last("LIMIT " + pageSize + " OFFSET " + (long) (pageNo - 1) * pageSize)));
  }

  @Override
  public List<FlowDefinitionVO> findByFlowCodeAndTenantId(String flowCode, String tenantId) {
    return converter.flowDefinitionListToVO(
        definitionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getFlowVersion)));
  }

  @Override
  public int casLock(
      String definitionId,
      String userId,
      LocalDateTime now,
      String lockedBy,
      LocalDateTime timeoutExpired,
      Integer revision) {
    return definitionMapper.casLock(definitionId, userId, now, lockedBy, timeoutExpired, revision);
  }

  @Override
  public int casUnlock(String definitionId, String userId, Integer revision) {
    return definitionMapper.casUnlock(definitionId, userId, revision);
  }

  @Override
  public void publish(String definitionId, int isPublish) {
    definitionMapper.publish(definitionId, isPublish);
  }

  @Override
  public void deactivateByFlowCode(String flowCode, String targetDefinitionId, String tenantId) {
    definitionMapper.deactivateByFlowCode(flowCode, targetDefinitionId, tenantId);
  }

  @Override
  public void updateActivityStatus(String definitionId, int activityStatus) {
    FlowDefinitionDO entity = new FlowDefinitionDO();
    entity.setId(definitionId);
    entity.setActivityStatus(activityStatus);
    definitionMapper.updateById(entity);
  }

  @Override
  public Optional<FlowDefinitionVO> findPreviousPublishedVersion(
      String flowCode, String tenantId, String excludeDefinitionId) {
    return definitionMapper
        .selectList(
            new LambdaQueryWrapper<FlowDefinitionDO>()
                .eq(FlowDefinitionDO::getFlowCode, flowCode)
                .eq(tenantId != null, FlowDefinitionDO::getTenantId, tenantId)
                .ne(StringUtils.hasText(excludeDefinitionId),
                    FlowDefinitionDO::getId, excludeDefinitionId)
                .eq(FlowDefinitionDO::getIsPublish, 1)
                .eq(FlowDefinitionDO::getDeleted, 0)
                .orderByDesc(FlowDefinitionDO::getCreatedAt)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }
}
