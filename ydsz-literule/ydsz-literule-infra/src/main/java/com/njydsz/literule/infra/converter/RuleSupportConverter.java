package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.dto.post.DecisionTablePostDTO;
import com.njydsz.literule.domain.dto.post.RuleVersionSaveDTO;
import com.njydsz.literule.domain.dto.put.RuleABPolicyPutDTO;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.entity.DecisionTable;
import com.njydsz.literule.infra.entity.RuleABPolicy;
import com.njydsz.literule.infra.entity.RuleDependency;
import com.njydsz.literule.infra.entity.RuleExecutionTrace;
import com.njydsz.literule.infra.entity.RulePack;
import com.njydsz.literule.infra.entity.RuleVersionHistory;

/**
 * 规则支撑转换器（P2-2 拆分）
 *
 * <p>承载规则依赖、执行轨迹、规则包、测试用例、版本历史等支撑实体的 Entity ↔ VO 以及 DTO → Entity 转换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleSupportConverter {

  /** MapStruct 单例实例 */
  RuleSupportConverter INSTANCE = Mappers.getMapper(RuleSupportConverter.class);

  // ===== RuleDependency =====
  RuleDependencyVO entityToVO(RuleDependency entity);

  List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities);

  // ===== RuleExecutionTrace =====
  RuleExecutionTraceVO entityToVO(RuleExecutionTrace entity);

  List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTrace> entities);

  // ===== RulePack =====
  RulePackVO entityToVO(RulePack entity);

  List<RulePackVO> rulePackListToVO(List<RulePack> entities);

  // ===== RuleVersionHistory → RuleVersionVO =====
  @Mapping(target = "id", source = "id")
  @Mapping(target = "ruleCode", source = "ruleCode")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "definitionJson", source = "definitionJson")
  @Mapping(target = "changeDesc", source = "changeDesc")
  @Mapping(target = "operator", source = "operator")
  @Mapping(target = "createdAt", ignore = true)
  RuleVersionVO ruleVersionHistoryToVO(RuleVersionHistory entity);

  List<RuleVersionVO> ruleVersionListToVO(List<RuleVersionHistory> entities);

  // ===== RuleVersionSaveDTO → RuleVersionHistory =====
  @Mapping(target = "id", ignore = true)
  RuleVersionHistory postDtoToEntity(RuleVersionSaveDTO dto);

  // ===== DecisionTable PostDTO → Entity =====
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DecisionTable postDtoToEntity(DecisionTablePostDTO dto);

  // ===== RuleABPolicy PutDTO → Entity =====
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "lastEvaluatedAt", ignore = true)
  @Mapping(target = "lastRollbackAt", ignore = true)
  RuleABPolicy putDtoToEntity(RuleABPolicyPutDTO dto);
}
