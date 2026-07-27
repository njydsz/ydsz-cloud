package com.njydsz.literule.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

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
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleCanaryBucketVO;
import com.njydsz.literule.domain.vo.RuleChainGraphDOVO;
import com.njydsz.literule.domain.vo.RuleDecisionTreeVO;
import com.njydsz.literule.domain.vo.RuleDefinitionDOVO;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceDOVO;
import com.njydsz.literule.domain.vo.RulePackDOVO;
import com.njydsz.literule.domain.vo.RuleScorecardVO;
import com.njydsz.literule.domain.vo.RuleScriptVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.domain.vo.RuleTestCaseDOVO;
import com.njydsz.literule.domain.vo.RuleVariableDefVO;
import com.njydsz.literule.domain.vo.RuleVersionHistoryVO;

/**
 * literule 模块统一 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface LiteruleConverter {

    LiteruleConverter INSTANT = Mappers.getMapper(LiteruleConverter.class);

    // ===== DecisionTable =====
    DecisionTableVO entityToVO(DecisionTable entity);
    List<DecisionTableVO> decisionTableListToVO(List<DecisionTable> entities);

    // ===== RuleABPolicy =====
    RuleABPolicyVO entityToVO(RuleABPolicy entity);
    List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicy> entities);

    // ===== RuleABRollback =====
    RuleABRollbackVO entityToVO(RuleABRollback entity);
    List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollback> entities);

    // ===== RuleCanaryBucket =====
    RuleCanaryBucketVO entityToVO(RuleCanaryBucket entity);
    List<RuleCanaryBucketVO> ruleCanaryBucketListToVO(List<RuleCanaryBucket> entities);

    // ===== RuleChainGraphDO =====
    RuleChainGraphDOVO entityToVO(RuleChainGraphDO entity);
    List<RuleChainGraphDOVO> ruleChainGraphDOListToVO(List<RuleChainGraphDO> entities);

    // ===== RuleDecisionTree =====
    RuleDecisionTreeVO entityToVO(RuleDecisionTree entity);
    List<RuleDecisionTreeVO> ruleDecisionTreeListToVO(List<RuleDecisionTree> entities);

    // ===== RuleDefinitionDO =====
    RuleDefinitionDOVO entityToVO(RuleDefinitionDO entity);
    List<RuleDefinitionDOVO> ruleDefinitionDOListToVO(List<RuleDefinitionDO> entities);

    // ===== RuleDependency =====
    RuleDependencyVO entityToVO(RuleDependency entity);
    List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities);

    // ===== RuleExecutionTraceDO =====
    RuleExecutionTraceDOVO entityToVO(RuleExecutionTraceDO entity);
    List<RuleExecutionTraceDOVO> ruleExecutionTraceDOListToVO(List<RuleExecutionTraceDO> entities);

    // ===== RulePackDO =====
    RulePackDOVO entityToVO(RulePackDO entity);
    List<RulePackDOVO> rulePackDOListToVO(List<RulePackDO> entities);

    // ===== RuleScorecard =====
    RuleScorecardVO entityToVO(RuleScorecard entity);
    List<RuleScorecardVO> ruleScorecardListToVO(List<RuleScorecard> entities);

    // ===== RuleScript =====
    RuleScriptVO entityToVO(RuleScript entity);
    List<RuleScriptVO> ruleScriptListToVO(List<RuleScript> entities);

    // ===== RuleTemplate =====
    RuleTemplateVO entityToVO(RuleTemplate entity);
    List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities);

    // ===== RuleTestCaseDO =====
    RuleTestCaseDOVO entityToVO(RuleTestCaseDO entity);
    List<RuleTestCaseDOVO> ruleTestCaseDOListToVO(List<RuleTestCaseDO> entities);

    // ===== RuleVariableDef =====
    RuleVariableDefVO entityToVO(RuleVariableDef entity);
    List<RuleVariableDefVO> ruleVariableDefListToVO(List<RuleVariableDef> entities);

    // ===== RuleVersionHistory =====
    RuleVersionHistoryVO entityToVO(RuleVersionHistory entity);
    List<RuleVersionHistoryVO> ruleVersionHistoryListToVO(List<RuleVersionHistory> entities);

}