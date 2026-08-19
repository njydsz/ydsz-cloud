package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.infra.entity.DecisionTable;
import com.njydsz.literule.infra.entity.RuleABPolicy;
import com.njydsz.literule.infra.entity.RuleABRollback;
import com.njydsz.literule.infra.entity.RuleCanaryBucket;
import com.njydsz.literule.infra.entity.RuleChainGraphDO;
import com.njydsz.literule.infra.entity.RuleDecisionTree;
import com.njydsz.literule.infra.entity.RuleScorecard;
import com.njydsz.literule.infra.entity.RuleScript;
import com.njydsz.literule.infra.entity.RuleTemplate;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleCanaryBucketVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleDecisionTreeVO;
import com.njydsz.literule.domain.vo.RuleScorecardVO;
import com.njydsz.literule.domain.vo.RuleScriptVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;

/**
 * 规则组件转换器（P2-2 拆分）
 *
 * <p>承载决策表、AB 测试策略、回滚记录、灰度桶、规则链画布、 决策树、评分卡、规则脚本、规则模板等组件实体的 Entity ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
@Mapper
public interface RuleComponentConverter {

  RuleComponentConverter INSTANT = Mappers.getMapper(RuleComponentConverter.class);

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

  // ===== RuleScorecard =====
  RuleScorecardVO entityToVO(RuleScorecard entity);

  List<RuleScorecardVO> ruleScorecardListToVO(List<RuleScorecard> entities);

  // ===== RuleScript =====
  RuleScriptVO entityToVO(RuleScript entity);

  List<RuleScriptVO> ruleScriptListToVO(List<RuleScript> entities);

  // ===== RuleTemplate =====
  RuleTemplateVO entityToVO(RuleTemplate entity);

  List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities);
}
