package com.njydsz.literule.infra.converter;

import java.util.List;

import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RulePack;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.expression.ExpressionFunctionDef;
import com.njydsz.literule.api.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.dto.post.DecisionTablePostDTO;
import com.njydsz.literule.domain.dto.post.RuleVersionSaveDTO;
import com.njydsz.literule.domain.dto.put.RuleABPolicyPutDTO;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.entity.DecisionTableDO;
import com.njydsz.literule.infra.entity.RuleABPolicyDO;
import com.njydsz.literule.infra.entity.RuleABRollbackDO;
import com.njydsz.literule.infra.entity.RuleChainGraphDO;
import com.njydsz.literule.infra.entity.RuleDefinitionDO;
import com.njydsz.literule.infra.entity.RuleDependencyDO;
import com.njydsz.literule.infra.entity.RuleExecutionTraceDO;
import com.njydsz.literule.infra.entity.RulePackDO;
import com.njydsz.literule.infra.entity.RuleTemplateDO;
import com.njydsz.literule.infra.entity.RuleVersionHistoryDO;

/**
 * literule 模块统一转换器门面。
 *
 * <p>委托给三个子转换器：
 *
 * <ul>
 *   <li>{@link RuleCoreConverter} - 规则定义、规则结果、引擎统计
 *   <li>{@link RuleComponentConverter} - 决策表、AB 策略、回滚、画布、模板
 *   <li>{@link RuleSupportConverter} - 依赖、执行轨迹、规则包、测试用例、版本
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0 重构为门面模式，委托给子转换器
 */
public class LiteruleConverter {

  /** 单例实例（门面转换器） */
  public static final LiteruleConverter INSTANCE = new LiteruleConverter();

  private final RuleCoreConverter core = RuleCoreConverter.INSTANCE;
  private final RuleComponentConverter component = RuleComponentConverter.INSTANCE;
  private final RuleSupportConverter support = RuleSupportConverter.INSTANCE;

  private LiteruleConverter() {
    // 单例门面
  }

  // ===== DecisionTableDO =====
  public DecisionTableVO entityToVO(DecisionTableDO entity) {
    return component.entityToVO(entity);
  }

  public List<DecisionTableVO> decisionTableListToVO(List<DecisionTableDO> entities) {
    return component.decisionTableListToVO(entities);
  }

  // ===== RuleABPolicyDO =====
  public RuleABPolicyVO entityToVO(RuleABPolicyDO entity) {
    return component.entityToVO(entity);
  }

  public List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicyDO> entities) {
    return component.ruleABPolicyListToVO(entities);
  }

  // ===== RuleABRollbackDO =====
  public RuleABRollbackVO entityToVO(RuleABRollbackDO entity) {
    return component.entityToVO(entity);
  }

  public List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollbackDO> entities) {
    return component.ruleABRollbackListToVO(entities);
  }

  // ===== RuleChainGraphDO =====
  public RuleChainGraphVO entityToVO(RuleChainGraphDO entity) {
    return component.entityToVO(entity);
  }

  public List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraphDO> entities) {
    return component.ruleChainGraphListToVO(entities);
  }

  // ===== RuleDefinitionDO =====
  public RuleDefinitionVO entityToVO(RuleDefinitionDO entity) {
    return core.entityToVO(entity);
  }

  public List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinitionDO> entities) {
    return core.ruleDefinitionListToVO(entities);
  }

  // ===== RuleDependencyDO =====
  public RuleDependencyVO entityToVO(RuleDependencyDO entity) {
    return support.entityToVO(entity);
  }

  public List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependencyDO> entities) {
    return support.ruleDependencyListToVO(entities);
  }

  // ===== RuleExecutionTraceDO =====
  public RuleExecutionTraceVO entityToVO(RuleExecutionTraceDO entity) {
    return support.entityToVO(entity);
  }

  public List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTraceDO> entities) {
    return support.ruleExecutionTraceListToVO(entities);
  }

  // ===== RulePackDO =====
  public RulePackVO entityToVO(RulePackDO entity) {
    return support.entityToVO(entity);
  }

  public List<RulePackVO> rulePackListToVO(List<RulePackDO> entities) {
    return support.rulePackListToVO(entities);
  }

  // ===== RuleTemplateDO =====
  public RuleTemplateVO entityToVO(RuleTemplateDO entity) {
    return component.entityToVO(entity);
  }

  public List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplateDO> entities) {
    return component.ruleTemplateListToVO(entities);
  }

  // ===== RuleVersionHistoryDO → RuleVersionVO =====
  public RuleVersionVO ruleVersionHistoryToVO(RuleVersionHistoryDO entity) {
    return support.ruleVersionHistoryToVO(entity);
  }

  public List<RuleVersionVO> ruleVersionListToVO(List<RuleVersionHistoryDO> entities) {
    return support.ruleVersionListToVO(entities);
  }

  // ===== RuleVersionSaveDTO → RuleVersionHistoryDO =====
  public RuleVersionHistoryDO postDtoToEntity(RuleVersionSaveDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleDefinition (api) → RuleDefinitionVO =====
  public RuleDefinitionVO entityToVO(RuleDefinition entity) {
    return core.entityToVO(entity);
  }

  // ===== RuleResult (api) → RuleResultVO =====
  public RuleResultVO entityToVO(RuleResult entity) {
    return core.entityToVO(entity);
  }

  // ===== RuleEngineStats (api) → RuleEngineStatsVO =====
  public RuleEngineStatsVO entityToVO(RuleEngineStats entity) {
    return core.entityToVO(entity);
  }

  // ===== RulePack (api) → RulePackVO =====
  public RulePackVO entityToVO(RulePack entity) {
    return core.entityToVO(entity);
  }

  // ===== DecisionTableDefinition (api) → DecisionTableDefinitionVO =====
  public DecisionTableDefinitionVO entityToVO(DecisionTableDefinition entity) {
    return core.entityToVO(entity);
  }

  // ===== ExpressionValidationResult (api.expr) → ExpressionValidationResultVO =====
  public ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity) {
    return core.entityToVO(entity);
  }

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====
  public ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity) {
    return core.entityToVO(entity);
  }

  // ===== DecisionTableDO PostDTO → Entity =====
  public DecisionTableDO postDtoToEntity(DecisionTablePostDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleABPolicyDO PutDTO → Entity =====
  public RuleABPolicyDO putDtoToEntity(RuleABPolicyPutDTO dto) {
    return support.putDtoToEntity(dto);
  }
}
