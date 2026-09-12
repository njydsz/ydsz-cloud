package com.njydsz.literule.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.entity.RuleDefinition;
import com.njydsz.literule.domain.expression.ExpressionFunctionDef;
import com.njydsz.literule.domain.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 规则核心转换器（P2-2 拆分）
 *
 * <p>承载规则定义、规则结果、引擎统计等核心实体的 Entity ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface RuleCoreConverter {

  /** MapStruct 单例实例 */
  RuleCoreConverter INSTANCE = Mappers.getMapper(RuleCoreConverter.class);

  // ===== RuleDefinition ↔ VO =====
  RuleDefinitionVO entityToVO(RuleDefinition entity);

  List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinition> entities);

  // ===== RuleDefinitionDTO (api) → RuleDefinitionVO =====
  // 源 boolean isEnabled / isDrilldownAvailable 解析为 enabled / drilldownAvailable（MapStruct 去掉 is 前缀）
  @Mapping(source = "code", target = "ruleCode")
  @Mapping(source = "name", target = "ruleName")
  @Mapping(source = "enabled", target = "isEnabled")
  @Mapping(source = "drilldownAvailable", target = "isDrilldownAvailable")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "canaryConditions", ignore = true)
  @Mapping(target = "effectiveFrom", ignore = true)
  @Mapping(target = "effectiveTo", ignore = true)
  @Mapping(target = "reviewedAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  RuleDefinitionVO entityToVO(RuleDefinitionDTO entity);

  // ===== RuleResultVO (api) → RuleResultVO（自映射，用于对象拷贝）=====
  // 源 boolean isTriggered / isCanary 解析为 triggered / canary（MapStruct 去掉 is 前缀）
  @Mapping(source = "triggered", target = "isTriggered")
  @Mapping(source = "canary", target = "isCanary")
  RuleResultVO entityToVO(RuleResultVO entity);

  // ===== RuleEngineStatsVO (api) → RuleEngineStatsVO =====
  RuleEngineStatsVO entityToVO(RuleEngineStatsVO entity);

  // ===== RulePackVO (api) → RulePackVO =====
  // api.RulePackVO 的 tags/ruleCodes 为 List<String>、ruleSnapshots 为 List<RuleDefinitionDTO>、
  // rating 为 double，与 VO 的 String/BigDecimal 不兼容，忽略。
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "ruleCodes", ignore = true)
  @Mapping(target = "ruleSnapshots", ignore = true)
  @Mapping(target = "rating", ignore = true)
  RulePackVO entityToVO(RulePackVO entity);

  // ===== DecisionTableDefinitionDTO (api) → DecisionTableDefinitionVO =====
  // VO 含 name/label/type/conditions/actions 属于 Row/Column 嵌套结构，DTO 顶层无对应源属性
  @Mapping(target = "name", ignore = true)
  @Mapping(target = "label", ignore = true)
  @Mapping(target = "type", ignore = true)
  @Mapping(target = "conditions", ignore = true)
  @Mapping(target = "actions", ignore = true)
  DecisionTableDefinitionVO entityToVO(DecisionTableDefinitionDTO entity);

  // ===== ExpressionValidationResult (api.expr) → ExpressionValidationResultVO =====
  ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity);

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====
  ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity);
}
