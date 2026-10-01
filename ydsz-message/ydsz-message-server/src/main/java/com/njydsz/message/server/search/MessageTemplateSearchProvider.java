package com.njydsz.message.server.search;

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
import com.njydsz.message.domain.repository.MsgTemplateRepository;
import com.njydsz.message.domain.vo.MsgTemplateVO;

/**
 * 消息模板搜索提供者 — 将消息模板数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageTemplateSearchProvider implements SearchProvider<MsgTemplateVO> {
  /** 名称搜索权重 */
  private static final BigDecimal NAME_WEIGHT = new BigDecimal("3.0");

  /** 编码搜索权重 */
  private static final BigDecimal SUBTITLE_WEIGHT = new BigDecimal("2.0");

  /** 内容搜索权重 */
  private static final BigDecimal BODY_WEIGHT = new BigDecimal("1.0");

  /** 状态搜索权重 */
  private static final BigDecimal STATUS_WEIGHT = new BigDecimal("0.5");


  private final MsgTemplateRepository msgTemplateRepository;

  @Override
  public String getType() {
    return "message_template";
  }

  /** {@inheritDoc} */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    List<SearchFilter> filters = new ArrayList<>(3);
    // 租户隔离
    if (context.getTenantId() != null && !context.getTenantId().isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("tenant_id")
              .values(List.of(context.getTenantId()))
              .operator(SearchFilter.Operator.EQ)
              .build());
    }
    // 仅搜索已启用状态的模板
    filters.add(
        SearchFilter.builder()
            .field("status")
            .values(List.of("ENABLED"))
            .operator(SearchFilter.Operator.EQ)
            .build());
    return filters;
  }

  public String getTypeLabel() {
    return I18n.message("message.template.label");
  }

  @Override
  public IndexDocument toIndexDocument(MsgTemplateVO entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(entity.getId())
        .type("message_template")
        .title(entity.getSubject())
        .subtitle(entity.getTemplateCode())
        .content(entity.getContent())
        .snippet(entity.getChannel())
        .status(entity.getStatus())
        .path("/message/template/" + entity.getId())
        .tenantId(entity.getTenantId())
        .createdBy(entity.getCreatedBy())
        .createdAt(
            entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(
            entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }

  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label(I18n.message("message.template.subject"))
            .type(FieldType.TEXT)
            .weight(NAME_WEIGHT)
            .isSearchable(true)
            .isHighlightable(true)
            .isSortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(I18n.message("message.template.code"))
            .type(FieldType.KEYWORD)
            .weight(SUBTITLE_WEIGHT)
            .isSearchable(true)
            .isHighlightable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(I18n.message("message.template.content"))
            .type(FieldType.TEXT)
            .weight(BODY_WEIGHT)
            .isSearchable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label(I18n.message("message.template.status"))
            .type(FieldType.KEYWORD)
            .weight(STATUS_WEIGHT)
            .isSearchable(false)
            .isAggregatable(true)
            .build());
  }

  public MsgTemplateVO loadById(String id) {
    return msgTemplateRepository.findById(id).orElse(null);
  }
}
