package com.njydsz.literule.server.config;

import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.infra.entity.RuleDefinitionDO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;

/**
 * 规则搜索服务
 *
 * <p>封装规则全文搜索能力，使用数据库级 LIKE 查询替代内存过滤，提升大规则量场景下的搜索性能。
 *
 * <h3>搜索字段</h3>
 *
 * <ul>
 *   <li>rule_code - 规则编码
 *   <li>rule_name - 规则名称
 *   <li>description - 规则描述
 *   <li>condition_expression - 条件表达式
 *   <li>category / category_path - 分类/分类路径
 *   <li>owner - 责任人
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleSearchService {

  private final RuleDefinitionMapper ruleDefinitionMapper;

  public RuleSearchService(RuleDefinitionMapper ruleDefinitionMapper) {
    this.ruleDefinitionMapper = ruleDefinitionMapper;
  }

  /**
   * 全文搜索规则（数据库级 LIKE 查询）
   *
   * <p>使用 MyBatis-Plus 的 LIKE 查询在数据库层完成过滤，避免全量加载内存。支持多关键词空格分隔（AND 语义），大小写不敏感。
   *
   * @param query 搜索关键词（空格分隔为 AND 条件，null/空返回全部）
   * @param status 状态过滤（null=不过滤）
   * @param category 分类过滤（null=不过滤）
   * @param enabled 启停过滤（null=不过滤）
   * @param offset 分页偏移
   * @param limit 分页大小
   * @return 搜索结果列表
   * @since 1.0.0
   */
  public List<RuleDefinition> search(
      String query, String status, String category, Boolean enabled, int offset, int limit) {
    // 使用数据库级搜索
    IPage<RuleDefinitionDO> page =
        ruleDefinitionMapper.searchRules(
            buildSearchQuery(query),
            status,
            category,
            enabled,
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                (offset / Math.max(limit, 1)) + 1, Math.max(limit, 1)));
    return page.getRecords().stream().map(this::doToRuleDefinition).toList();
  }

  /**
   * 统计搜索结果总数（不分页）
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @return 匹配的规则总数
   * @since 1.0.0
   */
  public int searchCount(String query, String status, String category, Boolean enabled) {
    return ruleDefinitionMapper.searchRulesCount(buildSearchQuery(query), status, category, enabled);
  }

  /**
   * 分页搜索规则
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @param pageQuery 分页查询参数
   * @return 分页结果
   * @since 1.0.0
   */
  public IPage<RuleDefinition> searchPage(
      String query, String status, String category, Boolean enabled, PageQuery pageQuery) {
    IPage<RuleDefinitionDO> page =
        ruleDefinitionMapper.searchRules(
            buildSearchQuery(query),
            status,
            category,
            enabled,
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                pageQuery.getEffectivePageNum(), pageQuery.getEffectivePageSize()));
    List<RuleDefinition> records =
        page.getRecords().stream().map(this::doToRuleDefinition).toList();
    IPage<RuleDefinition> result =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
            page.getCurrent(), page.getSize(), page.getTotal());
    result.setRecords(records);
    return result;
  }

  /** 构建搜索查询字符串 */
  private String buildSearchQuery(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    // 去除首尾空格，保留空格用于 AND 语义分割
    return query.trim();
  }

  /** RuleDefinitionDO → RuleDefinition 转换 */
  private RuleDefinition doToRuleDefinition(RuleDefinitionDO ruleDO) {
    RuleDefinition def = new RuleDefinition();
    def.setCode(ruleDO.getRuleCode());
    def.setName(ruleDO.getRuleName());
    def.setCategory(ruleDO.getCategory());
    def.setCategoryPath(ruleDO.getCategoryPath());
    def.setOwner(ruleDO.getOwner());
    def.setDescription(ruleDO.getDescription());
    def.setConditionExpression(ruleDO.getConditionExpression());
    def.setSeverityExpression(ruleDO.getSeverityExpression());
    def.setDefaultSeverity(
        ruleDO.getDefaultSeverity() != null
            ? com.njydsz.literule.api.RuleSeverity.fromCode(ruleDO.getDefaultSeverity())
            : null);
    def.setTitleTemplate(ruleDO.getTitleTemplate());
    def.setDescriptionTemplate(ruleDO.getDescriptionTemplate());
    def.setPriority(ruleDO.getPriority());
    def.setEnabled(ruleDO.getEnabled() != null && ruleDO.getEnabled());
    def.setScope(ruleDO.getScope());
    def.setMutexGroup(ruleDO.getMutexGroup());
    def.setVersion(ruleDO.getVersion() != null ? ruleDO.getVersion() : 1);
    def.setStatus(ruleDO.getStatus());
    def.setEffectiveFrom(ruleDO.getEffectiveFrom());
    def.setEffectiveTo(ruleDO.getEffectiveTo());
    def.setReviewedBy(ruleDO.getReviewedBy());
    def.setReviewedAt(ruleDO.getReviewedAt());
    def.setReviewComment(ruleDO.getReviewComment());
    def.setCanaryRatio(ruleDO.getCanaryRatio() != null ? ruleDO.getCanaryRatio() : 0.0);
    def.setCanaryConditionExpression(ruleDO.getCanaryConditionExpression());
    def.setCanarySeverityExpression(ruleDO.getCanarySeverityExpression());
    return def;
  }
}
