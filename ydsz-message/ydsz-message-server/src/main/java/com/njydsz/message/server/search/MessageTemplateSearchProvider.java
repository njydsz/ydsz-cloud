package com.njydsz.message.server.search;

import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.util.message.MessageUtils;
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
  private static final float NAME_WEIGHT = 3.0f;

  /** 内容搜索权重 */
  private static final float CONTENT_WEIGHT = 0.5f;


  private final MsgTemplateRepository msgTemplateRepository;

  @Override
  public String getType() {
    return "message_template";
  }

  public String getTypeLabel() {
    return MessageUtils.getMessage("message.search.typeLabel", "消息模板");
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
            .label(MessageUtils.getMessage("message.search.field.subject", "模板主题"))
            .type(FieldType.TEXT)
            .weight(NAME_WEIGHT)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(MessageUtils.getMessage("message.search.field.code", "模板编码"))
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(MessageUtils.getMessage("message.search.field.content", "模板内容"))
            .type(FieldType.TEXT)
            .weight(1.0f)
            .searchable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label(MessageUtils.getMessage("message.search.field.status", "状态"))
            .type(FieldType.KEYWORD)
            .weight(CONTENT_WEIGHT)
            .searchable(false)
            .aggregatable(true)
            .build());
  }

  public MsgTemplateVO loadById(String id) {
    return msgTemplateRepository.findById(id).orElse(null);
  }
}
