package com.njydsz.pmis.project.mapper.ruleengine;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ruleengine.RuleDefinitionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 规则定义 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface RuleDefinitionMapper extends BaseMapper<RuleDefinitionDO> {

    /**
     * 根据规则编码查询
     *
     * @param ruleCode 规则编码
     * @return 规则定义 DO
     */
    RuleDefinitionDO selectByCode(@Param("ruleCode") String ruleCode);
}
