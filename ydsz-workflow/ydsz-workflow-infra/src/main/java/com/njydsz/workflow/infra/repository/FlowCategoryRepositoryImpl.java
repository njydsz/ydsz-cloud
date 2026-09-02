package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.repository.FlowCategoryRepository;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowCategory;
import com.njydsz.workflow.infra.entity.FlowDefinition;
import com.njydsz.workflow.infra.mapper.FlowCategoryMapper;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;

/**
 * 流程分类仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowCategoryRepository} 接口，封装 FlowCategoryMapper 数据访问细节。
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
public class FlowCategoryRepositoryImpl implements FlowCategoryRepository {

  private final FlowCategoryMapper categoryMapper;

  private final FlowDefinitionMapper definitionMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowCategoryVO save(FlowCategoryDTO dto) {
    FlowCategory entity = converter.dtoToEntity(dto);
    categoryMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public Optional<FlowCategoryVO> findById(String id) {
    return Optional.ofNullable(categoryMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<FlowCategoryVO> findByCode(String code) {
    return categoryMapper
        .selectList(
            new LambdaQueryWrapper<FlowCategory>()
                .eq(FlowCategory::getCategoryCode, code)
                .eq(FlowCategory::getDeleted, 0)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(converter::entityToVO);
  }

  @Override
  public List<FlowCategoryVO> findAll(String tenantId) {
    return converter.flowCategoryListToVO(
        categoryMapper.selectList(
            new LambdaQueryWrapper<FlowCategory>()
                .eq(tenantId != null, FlowCategory::getTenantId, tenantId)
                .eq(FlowCategory::getDeleted, 0)
                .orderByAsc(FlowCategory::getSortNum)));
  }

  @Override
  public List<FlowCategoryVO> findByParentId(String parentId) {
    return converter.flowCategoryListToVO(
        categoryMapper.selectList(
            new LambdaQueryWrapper<FlowCategory>()
                .eq(FlowCategory::getParentId, parentId)
                .eq(FlowCategory::getDeleted, 0)
                .orderByAsc(FlowCategory::getSortNum)));
  }

  @Override
  public void deleteById(String id) {
    categoryMapper.deleteById(id);
  }

  @Override
  public FlowCategoryVO update(FlowCategoryDTO dto) {
    FlowCategory entity = converter.dtoToEntity(dto);
    categoryMapper.updateById(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public long countByCodeAndTenantId(String code, String tenantId) {
    return categoryMapper.selectCount(
        new LambdaQueryWrapper<FlowCategory>()
            .eq(FlowCategory::getCategoryCode, code)
            .eq(tenantId != null, FlowCategory::getTenantId, tenantId)
            .eq(FlowCategory::getDeleted, 0));
  }

  @Override
  public long countByParentId(String parentId) {
    return categoryMapper.selectCount(
        new LambdaQueryWrapper<FlowCategory>()
            .eq(FlowCategory::getParentId, parentId)
            .eq(FlowCategory::getDeleted, 0));
  }

  @Override
  public long countDefinitionsByCategory(String categoryId) {
    return definitionMapper.selectCount(
        new LambdaQueryWrapper<FlowDefinition>()
            .eq(FlowDefinition::getCategory, categoryId)
            .eq(FlowDefinition::getDeleted, 0));
  }
}
