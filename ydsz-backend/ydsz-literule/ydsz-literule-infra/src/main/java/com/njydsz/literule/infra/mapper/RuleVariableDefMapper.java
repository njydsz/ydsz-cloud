package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleVariableDef;

/**
 * 规则变量定义 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleVariableDefMapper extends BaseMapper<RuleVariableDef> {

    /**
     * 按变量名查询（不限启用状态，供管理端 upsert / 删除使用）
     *
     * @param varName 变量名
     * @return 变量定义 DO；不存在返回 null
     */
    @Select("SELECT * FROM ydsz_rule_variable_def WHERE var_name = #{varName} LIMIT 1")
    RuleVariableDef selectByVarName(@Param("varName") String varName);
}
