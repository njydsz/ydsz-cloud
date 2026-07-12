package com.njydsz.pmis.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.RuleChainGraphDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 规则链画布 Mapper（P0-1）
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
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
