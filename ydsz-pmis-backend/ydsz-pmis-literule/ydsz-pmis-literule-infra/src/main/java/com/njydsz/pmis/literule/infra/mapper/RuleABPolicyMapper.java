package com.njydsz.pmis.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.RuleABPolicyDO;

/**
 * AB Test 自动回滚策略 Mapper（P1-10）。
 *
 * <p>对应 {@code pmis_rule_ab_policy} 表，管理 AB Test 的回滚策略配置。
 * 每条规则可配置一个 AB Test 策略，包含灰度比例、错误率阈值、自动回滚开关等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-10)
 */
@Mapper
public interface RuleABPolicyMapper extends BaseMapper<RuleABPolicyDO> {

    /**
     * 根据规则编码查询 AB Test 策略。
     *
     * @param ruleCode 规则编码
     * @return AB Test 策略实体；不存在时返回 null
     */
    RuleABPolicyDO selectByRuleCode(@Param("ruleCode") String ruleCode);
}
