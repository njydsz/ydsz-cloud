package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.infra.entity.RuleDefinition;

/**
 * 规则定义 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_def</code>。
 *
 * <p>规则是业务可配置的判断/计算逻辑（积分/折扣/审批策略/计费），支持决策表/决策树/脚本/评分卡多种表达。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_key — 规则 KEY 唯一索引（业务编码）
 *   <li>idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）
 *   <li>idx_tenant_id — 租户隔离索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleDefinition 规则定义实体
 * @see com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleDefinitionMapper extends BaseMapper<RuleDefinition> {

  /**
   * 根据规则编码查询
   *
   * @param ruleCode 规则编码
   * @return 规则定义 DO
   */
  RuleDefinition selectByCode(@Param("ruleCode") String ruleCode);

  /**
   * 全文搜索规则（数据库级 LIKE 查询）
   *
   * @param query 搜索关键词（空格分隔为 AND 条件，null/空返回全部）
   * @param status 状态过滤（null=不过滤）
   * @param category 分类过滤（null=不过滤）
   * @param enabled 启停过滤（null=不过滤）
   * @param page 分页参数
   * @return 分页结果
   * @since 26.09.01
   */
  IPage<RuleDefinition> searchRules(
      @Param("query") String query,
      @Param("status") String status,
      @Param("category") String category,
      @Param("enabled") Boolean enabled,
      IPage<RuleDefinition> page);

  /**
   * 统计搜索结果总数
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @return 匹配的规则总数
   * @since 26.09.01
   */
  int searchRulesCount(
      @Param("query") String query,
      @Param("status") String status,
      @Param("category") String category,
      @Param("enabled") Boolean enabled);
}
