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
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;

/**
 * 站内通知全文搜索提供者 — 将站内通知注册到统一搜索体系。
 *
 * <p>替代原来的 Redis 倒排索引（{@link
 * com.njydsz.message.server.service.impl.NotificationSearchService}），通过 {@link SearchProvider}
 * SPI 将通知数据交由 ydsz-common-search 统一管理。
 *
 * <p>索引数据来源于数据库（{@code ydzs_msg_notification}），通过 {@link #loadAll(String)} 全量加载，
 * 搜索服务定期重建索引，无需业务侧手动维护。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSearchProvider implements SearchProvider<MsgNotificationVO> {

  /** 搜索类型标识 */
  public static final String TYPE = "notification";

  /** 标题搜索权重 */
  private static final BigDecimal TITLE_WEIGHT = new BigDecimal("3.0");

  /** 分类搜索权重 */
  private static final BigDecimal CATEGORY_WEIGHT = new BigDecimal("2.0");

  /** 内容搜索权重 */
  private static final BigDecimal CONTENT_WEIGHT = new BigDecimal("1.0");

  /** 状态搜索权重 */
  private static final BigDecimal STATUS_WEIGHT = new BigDecimal("0.5");

  private final MsgNotificationRepository msgNotificationRepository;

  /** {@inheritDoc} */
  @Override
  public String getType() {
    return TYPE;
  }

  /** {@inheritDoc} */
  @Override
  public List<MsgNotificationVO> loadAll(String tenantId) {
    NotificationQueryDTO query = new NotificationQueryDTO();
    if (tenantId != null && !tenantId.isBlank()) {
      query.setTenantId(tenantId);
    }
    // 仅索引未撤回的通知
    query.setRecallStatus("NONE");
    List<MsgNotificationVO> results = msgNotificationRepository.findList(query);
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    return results;
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
    // 通知只搜索与自己相关的（接收人匹配当前用户）
    if (context.getUserId() != null && !context.getUserId().isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("receiver_id")
              .values(List.of(context.getUserId()))
              .operator(SearchFilter.Operator.EQ)
              .build());
    }
    return filters;
  }

  /**
   * 获取该类型的展示标签。
   *
   * @return 国际化标签文本
   */
  public String getTypeLabel() {
    return I18n.message("message.notification.label");
  }

  /** {@inheritDoc} */
  @Override
  public IndexDocument toIndexDocument(MsgNotificationVO entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(entity.getId())
        .type(TYPE)
        .title(entity.getTitle())
        .subtitle(entity.getCategory())
        .content(entity.getContent())
        .snippet(entity.getBizType())
        .status(entity.getStatus())
        .path("/message/notification/" + entity.getId())
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

  /**
   * 获取可搜索字段列表。
   *
   * @return 搜索字段配置列表
   */
  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label(I18n.message("message.notification.title"))
            .type(FieldType.TEXT)
            .weight(TITLE_WEIGHT)
            .isSearchable(true)
            .isHighlightable(true)
            .isSortable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(I18n.message("message.notification.content"))
            .type(FieldType.TEXT)
            .weight(CONTENT_WEIGHT)
            .isSearchable(true)
            .isHighlightable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(I18n.message("message.notification.category"))
            .type(FieldType.KEYWORD)
            .weight(CATEGORY_WEIGHT)
            .isSearchable(true)
            .isAggregatable(true)
            .build(),
        SearchField.builder()
            .name("snippet")
            .label(I18n.message("message.notification.bizType"))
            .type(FieldType.KEYWORD)
            .isSearchable(false)
            .isAggregatable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label(I18n.message("message.notification.status"))
            .type(FieldType.KEYWORD)
            .weight(STATUS_WEIGHT)
            .isSearchable(false)
            .isAggregatable(true)
            .build());
  }

  /**
   * 根据 ID 加载单条通知。
   *
   * @param id 通知 ID
   * @return 通知 VO；不存在返回 {@code null}
   */
  public MsgNotificationVO loadById(String id) {
    return msgNotificationRepository.findById(id).orElse(null);
  }
}
