package com.njydsz.literule.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.infra.entity.RuleDependency;

/**
 * 规则依赖关系 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_dependency</code>。
 *
 * <p>规则依赖决定规则的执行顺序（DAG），避免循环依赖，是规则编排的核心元数据。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_dep — (规则+依赖规则) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleDependency 规则依赖实体
 * @see com.njydsz.literule.server.service.RuleDependencyService 规则依赖 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleDependencyMapper extends BaseMapper<RuleDependency> {

  /**
   * 查询某条规则依赖了哪些规则（正向）。
   *
   * @param ruleCode 规则编码
   * @return 依赖关系列表
   */
  List<RuleDependency> selectByRuleCode(@Param("ruleCode") String ruleCode);

  /**
   * 查询哪些规则依赖了指定规则（反向）。
   *
   * @param dependsOnRuleCode 被依赖的规则编码
   * @return 依赖关系列表
   */
  List<RuleDependency> selectByDependsOn(@Param("dependsOnRuleCode") String dependsOnRuleCode);

  /**
   * 查询指定被依赖规则中配置了级联禁用的依赖方。
   *
   * @param dependsOnRuleCode 被依赖的规则编码
   * @return 级联禁用依赖方列表
   */
  List<RuleDependency> selectCascadingByDependsOn(
      @Param("dependsOnRuleCode") String dependsOnRuleCode);

  /**
   * 删除某条规则的所有依赖。
   *
   * @param ruleCode 规则编码
   * @return 受影响行数
   */
  int deleteByRuleCode(@Param("ruleCode") String ruleCode);
}
