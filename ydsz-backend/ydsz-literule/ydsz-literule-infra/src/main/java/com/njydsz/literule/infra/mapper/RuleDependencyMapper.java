package com.njydsz.literule.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleDependency;

/**
 * 规则依赖关系 Mapper（P1-8）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleDependencyMapper extends BaseMapper<RuleDependency> {

    /**
     * 查询某条规则依赖了哪些规则（正向）
     */
    List<RuleDependency> selectByRuleCode(@Param("ruleCode") String ruleCode);

    /**
     * 查询哪些规则依赖了指定规则（反向）
     */
    List<RuleDependency> selectByDependsOn(@Param("dependsOnRuleCode") String dependsOnRuleCode);

    /**
     * 查询指定被依赖规则中配置了级联禁用的依赖方
     */
    List<RuleDependency> selectCascadingByDependsOn(@Param("dependsOnRuleCode") String dependsOnRuleCode);

    /**
     * 删除某条规则的所有依赖
     */
    int deleteByRuleCode(@Param("ruleCode") String ruleCode);
}
