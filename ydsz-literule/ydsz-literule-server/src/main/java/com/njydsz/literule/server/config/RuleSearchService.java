package com.njydsz.literule.server.config;

import java.math.BigDecimal;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.repository.RuleDefinitionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;

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
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class RuleSearchService {

  private final RuleDefinitionRepository ruleDefinitionRepository;

  public RuleSearchService(RuleDefinitionRepository ruleDefinitionRepository) {
    this.ruleDefinitionRepository = ruleDefinitionRepository;
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
   * @since 26.09.01
   */
  public List<RuleDefinitionDTO> search(
      String query, String status, String category, Boolean enabled, int offset, int limit) {
    return ruleDefinitionRepository
        .search(query, status, category, enabled, offset, limit)
        .stream()
        .map(this::voToRuleDefinition)
        .toList();
  }

  /**
   * 统计搜索结果总数（不分页）
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @return 匹配的规则总数
   * @since 26.09.01
   */
  public int searchCount(String query, String status, String category, Boolean enabled) {
    return ruleDefinitionRepository.searchCount(query, status, category, enabled);
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
   * @since 26.09.01
   */
  public PageResponse<List<RuleDefinitionDTO>> searchPage(
      String query, String status, String category, Boolean enabled, PageQuery pageQuery) {
    PageResponse<List<RuleDefinitionVO>> voPage =
        ruleDefinitionRepository.searchPage(query, status, category, enabled, pageQuery);
    List<RuleDefinitionDTO> records =
        voPage.getData().stream().map(this::voToRuleDefinition).toList();
    return PageResponse.success(
        voPage.getTotal(), voPage.getPageNum(), voPage.getPageSize(), records);
  }

  /**
   * RuleDefinitionVO → RuleDefinitionDTO 转换
   *
   * @param vo 规则定义 VO
   * @return RuleDefinitionDTO
   */
  private RuleDefinitionDTO voToRuleDefinition(RuleDefinitionVO vo) {
    RuleDefinitionDTO def = new RuleDefinitionDTO();
    def.setCode(vo.getRuleCode());
    def.setName(vo.getRuleName());
    def.setCategory(vo.getCategory());
    def.setCategoryPath(vo.getCategoryPath());
    def.setOwner(vo.getOwner());
    def.setDescription(vo.getDescription());
    def.setConditionExpression(vo.getConditionExpression());
    def.setSeverityExpression(vo.getSeverityExpression());
    def.setDefaultSeverity(
        vo.getDefaultSeverity() != null
            ? RuleSeverity.fromCode(vo.getDefaultSeverity())
            : null);
    def.setTitleTemplate(vo.getTitleTemplate());
    def.setDescriptionTemplate(vo.getDescriptionTemplate());
    def.setPriority(vo.getPriority());
    def.setEnabled(vo.getEnabled() != null && vo.getEnabled());
    def.setScope(vo.getScope());
    def.setMutexGroup(vo.getMutexGroup());
    def.setVersion(vo.getVersion() != null ? vo.getVersion() : 1);
    def.setStatus(vo.getStatus());
    def.setEffectiveFrom(vo.getEffectiveFrom());
    def.setEffectiveTo(vo.getEffectiveTo());
    def.setReviewedBy(vo.getReviewedBy());
    def.setReviewedAt(vo.getReviewedAt());
    def.setReviewComment(vo.getReviewComment());
    def.setCanaryRatio(vo.getCanaryRatio() != null ? vo.getCanaryRatio() : BigDecimal.ZERO);
    def.setCanaryConditionExpression(vo.getCanaryConditionExpression());
    def.setCanarySeverityExpression(vo.getCanarySeverityExpression());
    return def;
  }
}
