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
import com.njydsz.literule.infra.entity.DecisionTable;
import com.njydsz.literule.infra.entity.RuleABPolicy;
import com.njydsz.literule.infra.entity.RuleABRollback;
import com.njydsz.literule.infra.entity.RuleChainGraph;
import com.njydsz.literule.infra.entity.RuleDefinition;
import com.njydsz.literule.infra.entity.RuleDependency;
import com.njydsz.literule.infra.entity.RuleExecutionTrace;
import com.njydsz.literule.infra.entity.RulePack;
import com.njydsz.literule.infra.entity.RuleTemplate;
import com.njydsz.literule.infra.entity.RuleVersionHistory;

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

  // ===== RuleChainGraph =====
  public RuleChainGraphVO entityToVO(RuleChainGraph entity) {
    return component.entityToVO(entity);
  }

  public List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraph> entities) {
    return component.ruleChainGraphListToVO(entities);
  }

  // ===== RuleDefinition =====
  public RuleDefinitionVO entityToVO(RuleDefinition entity) {
    return core.entityToVO(entity);
  }

  public List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinition> entities) {
    return core.ruleDefinitionListToVO(entities);
  }

  // ===== RuleDependency =====
  public RuleDependencyVO entityToVO(RuleDependency entity) {
    return support.entityToVO(entity);
  }

  public List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities) {
    return support.ruleDependencyListToVO(entities);
  }

  // ===== RuleExecutionTrace =====
  public RuleExecutionTraceVO entityToVO(RuleExecutionTrace entity) {
    return support.entityToVO(entity);
  }

  public List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTrace> entities) {
    return support.ruleExecutionTraceListToVO(entities);
  }

  // ===== RulePack =====
  public RulePackVO entityToVO(RulePack entity) {
    return support.entityToVO(entity);
  }

  public List<RulePackVO> rulePackListToVO(List<RulePack> entities) {
    return support.rulePackListToVO(entities);
  }

  // ===== RuleTemplate =====
  public RuleTemplateVO entityToVO(RuleTemplate entity) {
    return component.entityToVO(entity);
  }

  public List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities) {
    return component.ruleTemplateListToVO(entities);
  }

  // ===== RuleVersionHistory → RuleVersionVO =====
  public RuleVersionVO ruleVersionHistoryToVO(RuleVersionHistory entity) {
    return support.ruleVersionHistoryToVO(entity);
  }

  public List<RuleVersionVO> ruleVersionListToVO(List<RuleVersionHistory> entities) {
    return support.ruleVersionListToVO(entities);
  }

  // ===== RuleVersionSaveDTO → RuleVersionHistory =====
  public RuleVersionHistory postDtoToEntity(RuleVersionSaveDTO dto) {
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

  // ===== DecisionTable PostDTO → Entity =====
  public DecisionTable postDtoToEntity(DecisionTablePostDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleABPolicy PutDTO → Entity =====
  public RuleABPolicy putDtoToEntity(RuleABPolicyPutDTO dto) {
    return support.putDtoToEntity(dto);
  }
}
