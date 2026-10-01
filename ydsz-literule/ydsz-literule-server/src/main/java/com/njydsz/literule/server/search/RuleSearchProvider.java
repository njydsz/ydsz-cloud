package com.njydsz.literule.server.search;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.literule.domain.repository.RuleDefinitionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;

/**
 * 规则定义搜索提供者 — 将规则定义数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSearchProvider implements SearchProvider<RuleDefinitionVO> {

    /** 搜索权重：规则名称完全匹配 */
  private static final BigDecimal WEIGHT_NAME_MATCH = new BigDecimal("3.0");

  /** 分类搜索权重 */
  private static final BigDecimal WEIGHT_SUBTITLE_MATCH = new BigDecimal("2.0");

  /** 搜索权重：规则编码匹配 */
  private static final BigDecimal WEIGHT_CODE_MATCH = new BigDecimal("1.5");

  /** 搜索权重：规则描述匹配 */
  private static final BigDecimal WEIGHT_DESC_MATCH = new BigDecimal("0.5");

  private final RuleDefinitionRepository ruleDefinitionRepository;

  @Override
  public String getType() {
    return "rule";
  }

  /** {@inheritDoc} */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    List<SearchFilter> filters = new ArrayList<>(2);
    // 租户隔离
    if (context.getTenantId() != null && !context.getTenantId().isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("tenant_id")
              .values(List.of(context.getTenantId()))
              .operator(SearchFilter.Operator.EQ)
              .build());
    }
    return filters;
  }

  public String getTypeLabel() {
    return I18n.message("literule.search.rule_label");
  }

  @Override
  public IndexDocument toIndexDocument(RuleDefinitionVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("rule")
        .title(vo.getRuleName())
        .subtitle(vo.getCategory())
        .content(vo.getRuleCode())
        .snippet(vo.getCategoryPath())
        .status(vo.getStatus())
        .path("/literule/rule/" + vo.getId())
        .tenantId(vo.getTenantId())
        .createdBy(vo.getCreatedBy())
        .createdAt(
            vo.getCreatedAt() != null
                ? vo.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(vo.getUpdatedBy())
        .updatedAt(
            vo.getUpdatedAt() != null
                ? vo.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }

  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label(I18n.message("literule.search.rule_name"))
            .type(FieldType.TEXT)
            .weight(WEIGHT_NAME_MATCH)
            .isSearchable(true)
            .isHighlightable(true)
            .isSortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(I18n.message("literule.search.category"))
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_SUBTITLE_MATCH)
            .isSearchable(true)
            .isAggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(I18n.message("literule.search.rule_code"))
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_CODE_MATCH)
            .isSearchable(true)
            .isHighlightable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label(I18n.message("literule.search.status"))
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_DESC_MATCH)
            .isSearchable(false)
            .isAggregatable(true)
            .build());
  }

  public RuleDefinitionVO loadById(String id) {
    return ruleDefinitionRepository.findById(id).orElse(null);
  }
}
