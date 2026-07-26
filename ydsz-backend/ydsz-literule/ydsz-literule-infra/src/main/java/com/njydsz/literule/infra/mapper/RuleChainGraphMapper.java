package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleChainGraphDO;

/**
 * 规则链画布 Mapper（P0-1）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleChainGraphMapper extends BaseMapper<RuleChainGraphDO> {

    /**
     * 根据规则编码查询画布
     *
     * @param ruleCode 规则编码
     * @return 画布 DO
     */
    RuleChainGraphDO selectByRuleCode(@Param("ruleCode") String ruleCode);

    /**
     * 根据规则编码删除画布
     *
     * @param ruleCode 规则编码
     * @return 删除条数
     */
    int deleteByRuleCode(@Param("ruleCode") String ruleCode);
}
