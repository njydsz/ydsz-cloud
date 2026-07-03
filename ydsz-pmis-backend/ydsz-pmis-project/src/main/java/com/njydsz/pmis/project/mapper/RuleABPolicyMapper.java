package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.RuleABPolicyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AB Test 自动回滚策略 Mapper（P1-10）
 */
@Mapper
public interface RuleABPolicyMapper extends BaseMapper<RuleABPolicyDO> {

    /**
     * 根据规则编码查询策略
     */
    RuleABPolicyDO selectByRuleCode(@Param("ruleCode") String ruleCode);
}
