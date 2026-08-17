package com.njydsz.literule.domain.converter;

import java.util.List;

import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.api.RulePack;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.expression.ExpressionFunctionDef;
import com.njydsz.literule.api.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.dto.post.DecisionTablePostDTO;
import com.njydsz.literule.domain.dto.post.RuleTestCasePostDTO;
import com.njydsz.literule.domain.dto.put.RuleABPolicyPutDTO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.entity.RuleABPolicy;
import com.njydsz.literule.domain.entity.RuleABRollback;
import com.njydsz.literule.domain.entity.RuleCanaryBucket;
import com.njydsz.literule.domain.entity.RuleChainGraphDO;
import com.njydsz.literule.domain.entity.RuleDecisionTree;
import com.njydsz.literule.domain.entity.RuleDefinitionDO;
import com.njydsz.literule.domain.entity.RuleDependency;
import com.njydsz.literule.domain.entity.RuleExecutionTraceDO;
import com.njydsz.literule.domain.entity.RulePackDO;
import com.njydsz.literule.domain.entity.RuleScorecard;
import com.njydsz.literule.domain.entity.RuleScript;
import com.njydsz.literule.domain.entity.RuleTemplate;
import com.njydsz.literule.domain.entity.RuleTestCaseDO;
import com.njydsz.literule.domain.entity.RuleVariableDef;
import com.njydsz.literule.domain.entity.RuleVersionHistory;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleCanaryBucketVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleDecisionTreeVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleScorecardVO;
import com.njydsz.literule.domain.vo.RuleScriptVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.domain.vo.RuleTestCaseVO;
import com.njydsz.literule.domain.vo.RuleVariableDefVO;
import com.njydsz.literule.domain.vo.RuleVersionHistoryVO;

/**
 * literule 模块统一转换器门面。
 *
 * <p>委托给三个子转换器：
 *
 * <ul>
 *   <li>{@link RuleCoreConverter} - 规则定义、规则结果、引擎统计
 *   <li>{@link RuleComponentConverter} - 决策表、AB 策略、回滚、灰度桶、画布、决策树、评分卡、脚本、模板
 *   <li>{@link RuleSupportConverter} - 依赖、执行轨迹、规则包、测试用例、变量定义、版本历史
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 2.1.0 重构为门面模式，委托给子转换器
 */
public class LiteruleConverter {

  public static final LiteruleConverter INSTANT = new LiteruleConverter();

  private final RuleCoreConverter core = RuleCoreConverter.INSTANT;
  private final RuleComponentConverter component = RuleComponentConverter.INSTANT;
  private final RuleSupportConverter support = RuleSupportConverter.INSTANT;

  private LiteruleConverter() {
    // 单例门面
  }

  // ===== DecisionTable =====
  public DecisionTableVO entityToVO(DecisionTable entity) {
    return component.entityToVO(entity);
  }

  public List<DecisionTableVO> decisionTableListToVO(List<DecisionTable> entities) {
    return component.decisionTableListToVO(entities);
  }

  // ===== RuleABPolicy =====
  public RuleABPolicyVO entityToVO(RuleABPolicy entity) {
    return component.entityToVO(entity);
  }

  public List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicy> entities) {
    return component.ruleABPolicyListToVO(entities);
  }

  // ===== RuleABRollback =====
  public RuleABRollbackVO entityToVO(RuleABRollback entity) {
    return component.entityToVO(entity);
  }

  public List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollback> entities) {
    return component.ruleABRollbackListToVO(entities);
  }

  // ===== RuleCanaryBucket =====
  public RuleCanaryBucketVO entityToVO(RuleCanaryBucket entity) {
    return component.entityToVO(entity);
  }

  public List<RuleCanaryBucketVO> ruleCanaryBucketListToVO(List<RuleCanaryBucket> entities) {
    return component.ruleCanaryBucketListToVO(entities);
  }

  // ===== RuleChainGraphDO =====
  public RuleChainGraphVO entityToVO(RuleChainGraphDO entity) {
    return component.entityToVO(entity);
  }

  public List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraphDO> entities) {
    return component.ruleChainGraphListToVO(entities);
  }

  // ===== RuleDecisionTree =====
  public RuleDecisionTreeVO entityToVO(RuleDecisionTree entity) {
    return component.entityToVO(entity);
  }

  public List<RuleDecisionTreeVO> ruleDecisionTreeListToVO(List<RuleDecisionTree> entities) {
    return component.ruleDecisionTreeListToVO(entities);
  }

  // ===== RuleDefinitionDO =====
  public RuleDefinitionVO entityToVO(RuleDefinitionDO entity) {
    return core.entityToVO(entity);
  }

  public List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinitionDO> entities) {
    return core.ruleDefinitionListToVO(entities);
  }

  // ===== RuleDependency =====
  public RuleDependencyVO entityToVO(RuleDependency entity) {
    return support.entityToVO(entity);
  }

  public List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities) {
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

  // ===== RuleScorecard =====
  public RuleScorecardVO entityToVO(RuleScorecard entity) {
    return component.entityToVO(entity);
  }

  public List<RuleScorecardVO> ruleScorecardListToVO(List<RuleScorecard> entities) {
    return component.ruleScorecardListToVO(entities);
  }

  // ===== RuleScript =====
  public RuleScriptVO entityToVO(RuleScript entity) {
    return component.entityToVO(entity);
  }

  public List<RuleScriptVO> ruleScriptListToVO(List<RuleScript> entities) {
    return component.ruleScriptListToVO(entities);
  }

  // ===== RuleTemplate =====
  public RuleTemplateVO entityToVO(RuleTemplate entity) {
    return component.entityToVO(entity);
  }

  public List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities) {
    return component.ruleTemplateListToVO(entities);
  }

  // ===== RuleTestCaseDO =====
  public RuleTestCaseVO entityToVO(RuleTestCaseDO entity) {
    return support.entityToVO(entity);
  }

  public List<RuleTestCaseVO> ruleTestCaseListToVO(List<RuleTestCaseDO> entities) {
    return support.ruleTestCaseListToVO(entities);
  }

  // ===== RuleVariableDef =====
  public RuleVariableDefVO entityToVO(RuleVariableDef entity) {
    return support.entityToVO(entity);
  }

  public List<RuleVariableDefVO> ruleVariableDefListToVO(List<RuleVariableDef> entities) {
    return support.ruleVariableDefListToVO(entities);
  }

  // ===== RuleVersionHistory =====
  public RuleVersionHistoryVO entityToVO(RuleVersionHistory entity) {
    return support.entityToVO(entity);
  }

  public List<RuleVersionHistoryVO> ruleVersionHistoryListToVO(List<RuleVersionHistory> entities) {
    return support.ruleVersionHistoryListToVO(entities);
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

  // ===== RuleTestCase PostDTO → Entity =====
  public RuleTestCaseDO postDtoToEntity(RuleTestCasePostDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== DecisionTable PostDTO → Entity =====
  public DecisionTable postDtoToEntity(DecisionTablePostDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleABPolicy PutDTO → Entity =====
  public RuleABPolicy putDtoToEntity(RuleABPolicyPutDTO dto) {
    return support.putDtoToEntity(dto);
  }
}
