package com.njydsz.literule.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.entity.RuleABPolicy;
import com.njydsz.literule.domain.entity.RuleABRollback;
import com.njydsz.literule.domain.entity.RuleChainGraph;
import com.njydsz.literule.domain.entity.RuleTemplate;

/**
 * 规则组件转换器（P2-2 拆分）
 *
 * <p>承载决策表、AB 测试策略、回滚记录、规则链画布、规则模板等组件实体的 Entity ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface RuleComponentConverter {

  /** MapStruct 单例实例 */
  RuleComponentConverter INSTANCE = Mappers.getMapper(RuleComponentConverter.class);

  // ===== DecisionTable =====
  DecisionTableVO entityToVO(DecisionTable entity);

  List<DecisionTableVO> decisionTableListToVO(List<DecisionTable> entities);

  // ===== RuleABPolicy =====
  RuleABPolicyVO entityToVO(RuleABPolicy entity);

  List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicy> entities);

  // ===== RuleABRollback =====
  RuleABRollbackVO entityToVO(RuleABRollback entity);

  List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollback> entities);

  // ===== RuleChainGraph =====
  RuleChainGraphVO entityToVO(RuleChainGraph entity);

  List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraph> entities);

  // ===== RuleTemplate =====
  RuleTemplateVO entityToVO(RuleTemplate entity);

  List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities);
}
