package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.literule.infra.entity.RuleVariableDef;

/**
 * 规则变量定义 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_variable_def</code>。
 *
 * <p>变量定义决定规则入参/出参的数据类型、来源、必填、约束，是规则配置的核心元数据。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_var — (规则+变量名) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleVariableDef 规则变量实体
 * @see com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
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
