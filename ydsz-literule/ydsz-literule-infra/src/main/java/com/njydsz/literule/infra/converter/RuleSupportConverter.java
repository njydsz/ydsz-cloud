package com.njydsz.literule.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.dto.post.DecisionTablePostDTO;
import com.njydsz.literule.domain.dto.post.RuleTestCasePostDTO;
import com.njydsz.literule.domain.dto.put.RuleABPolicyPutDTO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.entity.RuleABPolicy;
import com.njydsz.literule.domain.entity.RuleDependency;
import com.njydsz.literule.domain.entity.RuleExecutionTraceDO;
import com.njydsz.literule.domain.entity.RulePackDO;
import com.njydsz.literule.domain.entity.RuleTestCaseDO;
import com.njydsz.literule.domain.entity.RuleVariableDef;
import com.njydsz.literule.domain.entity.RuleVersionHistory;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleTestCaseVO;
import com.njydsz.literule.domain.vo.RuleVariableDefVO;
import com.njydsz.literule.domain.vo.RuleVersionHistoryVO;

/**
 * 规则支撑转换器（P2-2 拆分）
 *
 * <p>承载规则依赖、执行轨迹、规则包、测试用例、变量定义、版本历史等支撑实体的 Entity ↔ VO 以及 DTO → Entity 转换。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
@Mapper
public interface RuleSupportConverter {

  RuleSupportConverter INSTANT = Mappers.getMapper(RuleSupportConverter.class);

  // ===== RuleDependency =====
  RuleDependencyVO entityToVO(RuleDependency entity);

  List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities);

  // ===== RuleExecutionTraceDO =====
  RuleExecutionTraceVO entityToVO(RuleExecutionTraceDO entity);

  List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTraceDO> entities);

  // ===== RulePackDO =====
  RulePackVO entityToVO(RulePackDO entity);

  List<RulePackVO> rulePackListToVO(List<RulePackDO> entities);

  // ===== RuleTestCaseDO =====
  RuleTestCaseVO entityToVO(RuleTestCaseDO entity);

  List<RuleTestCaseVO> ruleTestCaseListToVO(List<RuleTestCaseDO> entities);

  // ===== RuleVariableDef =====
  RuleVariableDefVO entityToVO(RuleVariableDef entity);

  List<RuleVariableDefVO> ruleVariableDefListToVO(List<RuleVariableDef> entities);

  // ===== RuleVersionHistory =====
  RuleVersionHistoryVO entityToVO(RuleVersionHistory entity);

  List<RuleVersionHistoryVO> ruleVersionHistoryListToVO(List<RuleVersionHistory> entities);

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
