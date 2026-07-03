package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.RuleVariableDefDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 规则变量定义 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Mapper
public interface RuleVariableDefMapper extends BaseMapper<RuleVariableDefDO> {

    /**
     * 按变量名查询（不限启用状态，供管理端 upsert / 删除使用）
     *
     * @param varName 变量名
     * @return 变量定义 DO；不存在返回 null
     */
    @Select("SELECT * FROM pmis_rule_variable_def WHERE var_name = #{varName} LIMIT 1")
    RuleVariableDefDO selectByVarName(@Param("varName") String varName);
}
