package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.infra.entity.DecisionTableDO;
import com.njydsz.literule.infra.entity.RuleABPolicyDO;
import com.njydsz.literule.infra.entity.RuleABRollbackDO;
import com.njydsz.literule.infra.entity.RuleChainGraphDO;
import com.njydsz.literule.infra.entity.RuleTemplateDO;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;

/**
 * 规则组件转换器（P2-2 拆分）
 *
 * <p>承载决策表、AB 测试策略、回滚记录、规则链画布、规则模板等组件实体的 Entity ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
@Mapper
public interface RuleComponentConverter {

  RuleComponentConverter INSTANT = Mappers.getMapper(RuleComponentConverter.class);

  // ===== DecisionTableDO =====
  DecisionTableVO entityToVO(DecisionTableDO entity);

  List<DecisionTableVO> decisionTableListToVO(List<DecisionTableDO> entities);

  // ===== RuleABPolicyDO =====
  RuleABPolicyVO entityToVO(RuleABPolicyDO entity);

  List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicyDO> entities);

  // ===== RuleABRollbackDO =====
  RuleABRollbackVO entityToVO(RuleABRollbackDO entity);

  List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollbackDO> entities);

  // ===== RuleChainGraphDO =====
  RuleChainGraphVO entityToVO(RuleChainGraphDO entity);

  List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraphDO> entities);

  // ===== RuleTemplateDO =====
  RuleTemplateVO entityToVO(RuleTemplateDO entity);

  List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplateDO> entities);
}
