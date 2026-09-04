package com.njydsz.agent.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.agent.domain.dto.AgentApprovalDTO;
import com.njydsz.agent.domain.dto.AgentDefinitionDTO;
import com.njydsz.agent.domain.dto.AgentTraceDTO;
import com.njydsz.agent.domain.dto.AgentTraceStepDTO;
import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.dto.TokenUsageRecordDTO;
import com.njydsz.agent.domain.entity.AgentApproval;
import com.njydsz.agent.domain.entity.AgentDefinition;
import com.njydsz.agent.domain.entity.AgentTrace;
import com.njydsz.agent.domain.entity.AgentTraceStep;
import com.njydsz.agent.domain.entity.PromptTemplate;
import com.njydsz.agent.domain.entity.PromptVersion;
import com.njydsz.agent.domain.entity.TokenUsageRecord;
import com.njydsz.agent.domain.vo.AgentApprovalVO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.agent.domain.vo.AgentTraceStepVO;
import com.njydsz.agent.domain.vo.AgentTraceVO;
import com.njydsz.agent.domain.vo.PromptTemplateVO;
import com.njydsz.agent.domain.vo.PromptVersionVO;
import com.njydsz.agent.domain.vo.TokenUsageRecordVO;

/**
 * agent 模块统一 MapStruct 转换器。
 *
 * <p>负责 Entity ↔ VO、DTO → Entity 之间的类型转换。
 * 系统字段（id/deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt）由框架自动填充，转换时忽略。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入，开箱即用
 *   <li>同名字段自动映射；不同名字段通过 {@code @Mapping} 显式标注
 *   <li>批量转换使用 {@code @IterableMapping} 避免 MapStruct 歧义
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface AgentConverter {

  /** MapStruct 实例 */
  AgentConverter INSTANT = Mappers.getMapper(AgentConverter.class);

  // ===== AgentDefinition =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  AgentDefinitionVO entityToVO(AgentDefinition entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<AgentDefinitionVO> agentDefinitionListToVO(List<AgentDefinition> entities);

  /**
   * DTO → Entity 转换（创建场景，系统字段自动忽略）
   *
   * @param dto Agent 定义 DTO（id 不传）
   * @return 数据库实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AgentDefinition dtoToEntity(AgentDefinitionDTO dto);

  /**
   * DTO（含 ID）→ Entity 转换（更新场景，系统字段自动忽略）
   *
   * @param dto Agent 定义 DTO（含 id）
   * @return 数据库实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  AgentDefinition dtoToEntityWithId(AgentDefinitionDTO dto);

  // ===== AgentTrace =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  AgentTraceVO entityToVO(AgentTrace entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<AgentTraceVO> agentTraceListToVO(List<AgentTrace> entities);

  /**
   * DTO → Entity 转换（创建场景）
   *
   * @param dto Agent 执行链路 DTO
   * @return 数据库实体
   */
  // P1 修复：返回类型原误写为 AgentTraceDTO（复制粘贴错误），应转换为 AgentTrace
  AgentTrace dtoToEntity(AgentTraceDTO dto);

  /**
   * DTO（含 ID）→ Entity 转换（更新场景）
   *
   * @param dto Agent 执行链路 DTO（含 traceId）
   * @return 数据库实体
   */
  AgentTrace dtoToEntityWithId(AgentTraceDTO dto);

  // ===== AgentTraceStep =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  AgentTraceStepVO entityToVO(AgentTraceStep entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<AgentTraceStepVO> agentTraceStepListToVO(List<AgentTraceStep> entities);

  /**
   * DTO → Entity 转换
   *
   * @param dto Agent 执行链路步骤 DTO
   * @return 数据库实体
   */
  AgentTraceStep dtoToEntity(AgentTraceStepDTO dto);

  // ===== AgentApproval =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  AgentApprovalVO entityToVO(AgentApproval entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<AgentApprovalVO> agentApprovalListToVO(List<AgentApproval> entities);

  /**
   * DTO → Entity 转换（创建场景）
   *
   * @param dto 审批请求 DTO
   * @return 数据库实体
   */
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  AgentApproval dtoToEntity(AgentApprovalDTO dto);

  /**
   * DTO（含 ID）→ Entity 转换（更新场景）
   *
   * @param dto 审批请求 DTO（含 id）
   * @return 数据库实体
   */
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  AgentApproval dtoToEntityWithId(AgentApprovalDTO dto);

  // ===== PromptTemplate =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  PromptTemplateVO entityToVO(PromptTemplate entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<PromptTemplateVO> promptTemplateListToVO(List<PromptTemplate> entities);

  /**
   * DTO → Entity 转换（创建场景，系统字段自动忽略）
   *
   * @param dto Prompt 模板 DTO
   * @return 数据库实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PromptTemplate dtoToEntity(PromptTemplateDTO dto);

  /**
   * DTO（含 ID）→ Entity 转换（更新场景，系统字段自动忽略）
   *
   * @param dto Prompt 模板 DTO（含 id）
   * @return 数据库实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PromptTemplate dtoToEntityWithId(PromptTemplateDTO dto);

  // ===== PromptVersion =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  PromptVersionVO entityToVO(PromptVersion entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<PromptVersionVO> promptVersionListToVO(List<PromptVersion> entities);

  /**
   * DTO → Entity 转换（创建场景，系统字段自动忽略）
   *
   * @param dto Prompt 版本 DTO
   * @return 数据库实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PromptVersion dtoToEntity(PromptVersionDTO dto);

  // ===== TokenUsageRecord =====

  /**
   * Entity → VO 转换
   *
   * @param entity 数据库实体
   * @return 视图对象
   */
  TokenUsageRecordVO entityToVO(TokenUsageRecord entity);

  /**
   * Entity 列表 → VO 列表转换
   *
   * @param entities 实体列表
   * @return VO 列表
   */
  List<TokenUsageRecordVO> tokenUsageRecordListToVO(List<TokenUsageRecord> entities);

  /**
   * DTO → Entity 转换（创建场景，系统字段自动忽略）
   *
   * @param dto Token 用量记录 DTO
   * @return 数据库实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TokenUsageRecord dtoToEntity(TokenUsageRecordDTO dto);
}
