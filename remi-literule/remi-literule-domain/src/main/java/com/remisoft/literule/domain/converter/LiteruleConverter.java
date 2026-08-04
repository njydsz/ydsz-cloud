package com.remisoft.literule.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.remisoft.literule.domain.entity.DecisionTable;
import com.remisoft.literule.domain.entity.RuleABPolicy;
import com.remisoft.literule.domain.entity.RuleABRollback;
import com.remisoft.literule.domain.entity.RuleCanaryBucket;
import com.remisoft.literule.domain.entity.RuleChainGraphDO;
import com.remisoft.literule.domain.entity.RuleDecisionTree;
import com.remisoft.literule.domain.entity.RuleDefinitionDO;
import com.remisoft.literule.domain.entity.RuleDependency;
import com.remisoft.literule.domain.entity.RuleExecutionTraceDO;
import com.remisoft.literule.domain.entity.RulePackDO;
import com.remisoft.literule.domain.entity.RuleScorecard;
import com.remisoft.literule.domain.entity.RuleScript;
import com.remisoft.literule.domain.entity.RuleTemplate;
import com.remisoft.literule.domain.entity.RuleTestCaseDO;
import com.remisoft.literule.domain.entity.RuleVariableDef;
import com.remisoft.literule.domain.entity.RuleVersionHistory;
import com.remisoft.literule.api.DecisionTableDefinition;
import com.remisoft.literule.api.RuleDefinition;
import com.remisoft.literule.api.RuleEngineStats;
import com.remisoft.literule.api.RulePack;
import com.remisoft.literule.api.RuleResult;
import com.remisoft.literule.api.expr.ExpressionFunctionDef;
import com.remisoft.literule.api.expr.ExpressionValidationResult;
import com.remisoft.literule.domain.vo.DecisionTableVO;
import com.remisoft.literule.domain.vo.RuleABPolicyVO;
import com.remisoft.literule.domain.vo.RuleABRollbackVO;
import com.remisoft.literule.domain.vo.RuleCanaryBucketVO;
import com.remisoft.literule.domain.vo.RuleChainGraphVO;
import com.remisoft.literule.domain.vo.RuleDecisionTreeVO;
import com.remisoft.literule.domain.vo.RuleDefinitionVO;
import com.remisoft.literule.domain.vo.RuleDependencyVO;
import com.remisoft.literule.domain.vo.RuleExecutionTraceVO;
import com.remisoft.literule.domain.vo.RulePackVO;
import com.remisoft.literule.domain.vo.RuleScorecardVO;
import com.remisoft.literule.domain.vo.RuleScriptVO;
import com.remisoft.literule.domain.vo.RuleTemplateVO;
import com.remisoft.literule.domain.vo.RuleTestCaseVO;
import com.remisoft.literule.domain.vo.RuleVariableDefVO;
import com.remisoft.literule.domain.vo.RuleVersionHistoryVO;
import com.remisoft.literule.domain.vo.DecisionTableDefinitionVO;
import com.remisoft.literule.domain.vo.ExpressionFunctionDefVO;
import com.remisoft.literule.domain.vo.ExpressionValidationResultVO;
import com.remisoft.literule.domain.vo.RuleEngineStatsVO;
import com.remisoft.literule.domain.vo.RuleResultVO;
import com.remisoft.literule.domain.dto.post.RuleTestCasePostDTO;
import com.remisoft.literule.domain.dto.post.DecisionTablePostDTO;
import com.remisoft.literule.domain.dto.put.RuleABPolicyPutDTO;

/**
 * literule 模块统一 MapStruct 转换器。
 *
 * <p>承担轻量级规则引擎模块所有 Entity ↔ VO、DTO → Entity 的类型转换。
 * 覆盖决策表、规则策略、规则回滚、灰度发布、规则链图、决策树、规则定义、
 * 规则依赖、执行轨迹、规则包、评分卡、规则脚本、规则模板、测试用例、
 * 变量定义、版本历史等核心实体的转换。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射</li>
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入</li>
 *   <li>同名字段自动映射；系统字段通过 @Mapping(ignore = true) 忽略</li>
 * </ul>
 *
 * @author remi-team
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
    RuleChainGraphVO entityToVO(RuleChainGraphDO entity);
    List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraphDO> entities);

    // ===== RuleDecisionTree =====
    RuleDecisionTreeVO entityToVO(RuleDecisionTree entity);
    List<RuleDecisionTreeVO> ruleDecisionTreeListToVO(List<RuleDecisionTree> entities);

    // ===== RuleDefinitionDO =====
    RuleDefinitionVO entityToVO(RuleDefinitionDO entity);
    List<RuleDefinitionVO> ruleDefinitionListToVO(List<RuleDefinitionDO> entities);

    // ===== RuleDependency =====
    RuleDependencyVO entityToVO(RuleDependency entity);
    List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities);

    // ===== RuleExecutionTraceDO =====
    RuleExecutionTraceVO entityToVO(RuleExecutionTraceDO entity);
    List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTraceDO> entities);

    // ===== RulePackDO =====
    RulePackVO entityToVO(RulePackDO entity);
    List<RulePackVO> rulePackListToVO(List<RulePackDO> entities);

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
    RuleTestCaseVO entityToVO(RuleTestCaseDO entity);
    List<RuleTestCaseVO> ruleTestCaseListToVO(List<RuleTestCaseDO> entities);

    // ===== RuleVariableDef =====
    RuleVariableDefVO entityToVO(RuleVariableDef entity);
    List<RuleVariableDefVO> ruleVariableDefListToVO(List<RuleVariableDef> entities);

    // ===== RuleVersionHistory =====
    RuleVersionHistoryVO entityToVO(RuleVersionHistory entity);
    List<RuleVersionHistoryVO> ruleVersionHistoryListToVO(List<RuleVersionHistory> entities);


    // ===== RuleDefinition (api) → RuleDefinitionVO =====
    // 注意：api.RuleDefinition 与 domain.RuleDefinitionDO 字段结构不同，
    // code/name 需映射到 ruleCode/ruleName；String↔LocalDateTime/List↔String 不兼容字段忽略。
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


    // ===== RuleTestCase PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RuleTestCaseDO postDtoToEntity(RuleTestCasePostDTO dto);

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