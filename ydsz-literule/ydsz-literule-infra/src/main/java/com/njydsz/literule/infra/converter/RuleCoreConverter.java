package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RulePack;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.expression.ExpressionFunctionDef;
import com.njydsz.literule.api.expression.ExpressionValidationResult;
import com.njydsz.literule.infra.entity.RuleDefinitionDO;
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
 * @since 1.0.0
 */
@Mapper
public interface RuleCoreConverter {

  RuleCoreConverter INSTANCE = Mappers.getMapper(RuleCoreConverter.class);

  // ===== RuleDefinitionDO ↔ VO =====
  RuleDefinitionVO entityToVO(RuleDefinitionDO entity);

  List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinitionDO> entities);

  // ===== RuleDefinition (api) → RuleDefinitionVO =====
  @Mapping(source = "code", target = "ruleCode")
  @Mapping(source = "name", target = "ruleName")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "canaryConditions", ignore = true)
  @Mapping(target = "effectiveFrom", ignore = true)
  @Mapping(target = "effectiveTo", ignore = true)
  @Mapping(target = "reviewedAt", ignore = true)
  RuleDefinitionVO entityToVO(RuleDefinition entity);

  // ===== RuleResult (api) → RuleResultVO =====
  RuleResultVO entityToVO(RuleResult entity);

  // ===== RuleEngineStats (api) → RuleEngineStatsVO =====
  RuleEngineStatsVO entityToVO(RuleEngineStats entity);

  // ===== RulePack (api) → RulePackVO =====
  // api.RulePack 的 tags/ruleCodes 为 List<String>、ruleSnapshots 为 List<RuleDefinition>、
  // rating 为 double，与 VO 的 String/BigDecimal 不兼容，忽略。
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "ruleCodes", ignore = true)
  @Mapping(target = "ruleSnapshots", ignore = true)
  @Mapping(target = "rating", ignore = true)
  RulePackVO entityToVO(RulePack entity);

  // ===== DecisionTableDefinition (api) → DecisionTableDefinitionVO =====
  DecisionTableDefinitionVO entityToVO(DecisionTableDefinition entity);

  // ===== ExpressionValidationResult (api.expr) → ExpressionValidationResultVO =====
  ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity);

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====
  ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity);
}
