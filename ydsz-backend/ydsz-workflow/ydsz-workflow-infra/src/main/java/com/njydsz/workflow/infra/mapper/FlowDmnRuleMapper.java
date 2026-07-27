package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowDmnRule;

/**
 * P0-1: DMN 决策规则 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowDmnRuleMapper extends BaseMapper<FlowDmnRule> {

    /**
     * 根据决策表 ID 查全部启用的规则（按 ruleOrder 正序）
     */
    List<FlowDmnRule> selectEnabledByDecisionId(@Param("decisionId") String decisionId);

    /**
     * 根据决策表 ID 删除全部规则（重编辑时用）
     */
    int deleteByDecisionId(@Param("decisionId") String decisionId);
}
