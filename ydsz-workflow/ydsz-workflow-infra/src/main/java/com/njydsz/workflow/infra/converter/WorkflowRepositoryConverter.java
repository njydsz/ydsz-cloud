package com.njydsz.workflow.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.infra.entity.FlowInstance;

/**
 * 流程实例仓储专用 MapStruct 转换器（Infra 层）。
 *
 * <p>承担流程实例 Repository 所需的 DO ↔ VO、DTO → Entity 转换，
 * 位于基础设施层，避免 domain 层反向依赖 infra.entity。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANT} 单例访问，由 Spring 注入到 Repository 实现
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WorkflowRepositoryConverter {

  /** MapStruct 单例实例 */
  WorkflowRepositoryConverter INSTANT = Mappers.getMapper(WorkflowRepositoryConverter.class);

  // ===== Entity ↔ VO =====

  /**
   * 流程实例实体 → 流程实例 VO。
   *
   * @param entity 流程实例实体
   * @return 流程实例 VO
   */
  FlowInstanceVO entityToVO(FlowInstance entity);

  /**
   * 流程实例实体列表 → 流程实例 VO 列表。
   *
   * @param entities 流程实例实体列表
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> flowInstanceListToVO(List<FlowInstance> entities);

  // ===== DTO → Entity =====

  /**
   * 流程实例 DTO → 流程实例实体（创建场景）。
   *
   * <p>用于新增流程实例。MpBaseEntity 的自动填充字段通过 @Mapping(ignore = true) 忽略。
   *
   * @param dto 流程实例 DTO
   * @return 流程实例实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FlowInstance dtoToEntity(FlowInstanceDTO dto);

  /**
   * 流程实例 DTO（含 ID）→ 流程实例实体（更新场景）。
   *
   * <p>用于更新流程实例，保留 id 字段用于定位更新记录。
   *
   * @param dto 流程实例 DTO（含 id）
   * @return 流程实例实体（含 id，用于条件更新）
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FlowInstance dtoToEntityWithId(FlowInstanceDTO dto);

  /**
   * 流程实例 VO → 流程实例实体（更新场景）。
   *
   * <p>用于更新流程实例，将领域层 VO 转换为持久化实体。
   *
   * @param vo 流程实例 VO
   * @return 流程实例实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FlowInstance entityToEntity(FlowInstanceVO vo);

  // ===== DTO → VO =====

  /**
   * 流程实例 DTO → 流程实例 VO。
   *
   * <p>用于 save 方法返回领域层 VO，同名字段自动映射。
   *
   * @param dto 流程实例 DTO
   * @return 流程实例 VO
   */
  FlowInstanceVO dtoToVO(FlowInstanceDTO dto);
}
