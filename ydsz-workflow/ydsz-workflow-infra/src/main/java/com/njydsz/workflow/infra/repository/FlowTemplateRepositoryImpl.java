package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowTemplateRepository;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowTemplate;
import com.njydsz.workflow.infra.mapper.FlowTemplateMapper;

/**
 * 流程模板仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowTemplateRepository} 接口，封装 FlowTemplateMapper 数据访问细节。
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
public class FlowTemplateRepositoryImpl implements FlowTemplateRepository {

  private final FlowTemplateMapper templateMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowTemplateVO save(FlowTemplateVO vo) {
    FlowTemplate entity = converter.entityToEntity(vo);
    templateMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowTemplateVO> findById(String id) {
    return Optional.ofNullable(templateMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowTemplateVO> findByCode(String code) {
    return templateMapper
        .selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getTemplateCode, code)
                .eq(FlowTemplate::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowTemplateVO> findAll(String tenantId) {
    return converter.flowTemplateListToVO(
        templateMapper.selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(tenantId != null, FlowTemplate::getTenantId, tenantId)
                .eq(FlowTemplate::getDeleted, 0)
                .orderByAsc(FlowTemplate::getSortOrder)));
  }

  @Override
  public List<FlowTemplateVO> findByCategoryId(String categoryId) {
    return converter.flowTemplateListToVO(
        templateMapper.selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getCategory, categoryId)
                .eq(FlowTemplate::getDeleted, 0)
                .orderByAsc(FlowTemplate::getSortOrder)));
  }

  @Override
  public void deleteById(String id) {
    templateMapper.deleteById(id);
  }

  @Override
  public FlowTemplateVO update(FlowTemplateVO vo) {
    FlowTemplate entity = converter.entityToEntity(vo);
    templateMapper.updateById(entity);
    return vo;
  }

  @Override
  public Optional<FlowTemplateVO> findDefaultByCategory(String businessType, String tenantId) {
    return templateMapper
        .selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getCategory, businessType)
                .eq(FlowTemplate::getTenantId, tenantId)
                .eq(FlowTemplate::getIsLatest, 1)
                .eq(FlowTemplate::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public Optional<FlowTemplateVO> findByTemplateCode(String templateCode) {
    return templateMapper
        .selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getTemplateCode, templateCode)
                .eq(FlowTemplate::getIsLatest, 1)
                .eq(FlowTemplate::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowTemplateVO> findLatestByCategory(String category) {
    return converter.flowTemplateListToVO(
        templateMapper.selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(category != null && !category.isEmpty(), FlowTemplate::getCategory, category)
                .eq(FlowTemplate::getIsLatest, 1)
                .eq(FlowTemplate::getDeleted, 0)
                .orderByAsc(FlowTemplate::getSortOrder)));
  }

  @Override
  public void incrementUseCount(String templateCode) {
    templateMapper.incrementUseCount(templateCode);
  }

  @Override
  public void markAsNotLatest(String templateCode) {
    templateMapper.markAsNotLatest(templateCode);
  }

  @Override
  public Optional<Integer> selectMaxVersion(String templateCode) {
    return Optional.ofNullable(templateMapper.selectMaxVersion(templateCode));
  }

  @Override
  public List<FlowTemplateVO> findVersionsByTemplateCode(String templateCode) {
    return converter.flowTemplateListToVO(
        templateMapper.selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getTemplateCode, templateCode)
                .eq(FlowTemplate::getDeleted, 0)
                .orderByDesc(FlowTemplate::getVersion)));
  }

  @Override
  public List<FlowTemplateVO> findByParentTemplateId(String parentTemplateId) {
    return converter.flowTemplateListToVO(
        templateMapper.selectList(
            new LambdaQueryWrapper<FlowTemplate>()
                .eq(FlowTemplate::getParentTemplateId, parentTemplateId)
                .eq(FlowTemplate::getIsLatest, 1)
                .eq(FlowTemplate::getDeleted, 0)));
  }
}
