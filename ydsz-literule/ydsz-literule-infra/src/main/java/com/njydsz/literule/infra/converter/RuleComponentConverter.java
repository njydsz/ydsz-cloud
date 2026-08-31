package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.infra.entity.DecisionTable;
import com.njydsz.literule.infra.entity.RuleABPolicyDO;
import com.njydsz.literule.infra.entity.RuleABRollbackDO;
import com.njydsz.literule.infra.entity.RuleChainGraphDO;
import com.njydsz.literule.infra.entity.RuleTemplate;

/**
 * 规则组件转换器（P2-2 拆分）
 *
 * <p>承载决策表、AB 测试策略、回滚记录、规则链画布、规则模板等组件实体的 Entity ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleComponentConverter {

  /** MapStruct 单例实例 */
  RuleComponentConverter INSTANCE = Mappers.getMapper(RuleComponentConverter.class);

  // ===== DecisionTable =====
  DecisionTableVO entityToVO(DecisionTable entity);

  List<DecisionTableVO> decisionTableListToVO(List<DecisionTable> entities);

  // ===== RuleABPolicyDO =====
  RuleABPolicyVO entityToVO(RuleABPolicyDO entity);

  List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicyDO> entities);

  // ===== RuleABRollbackDO =====
  RuleABRollbackVO entityToVO(RuleABRollbackDO entity);

  List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollbackDO> entities);

  // ===== RuleChainGraphDO =====
  RuleChainGraphVO entityToVO(RuleChainGraphDO entity);

  List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraphDO> entities);

  // ===== RuleTemplate =====
  RuleTemplateVO entityToVO(RuleTemplate entity);

  List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities);
}
