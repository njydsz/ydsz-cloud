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
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.infra.repository.MsgTemplateRepository;

/**
 * 消息模板搜索提供者 — 将消息模板数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageTemplateSearchProvider implements SearchProvider<MsgTemplate> {

  private final MsgTemplateRepository msgTemplateRepository;

  @Override
  public String getType() {
    return "message_template";
  }

  @Override
  public String getTypeLabel() {
    return "消息模板";
  }

  @Override
  public IndexDocument toIndexDocument(MsgTemplate entity) {
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

  @Override
  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label("模板主题")
            .type(FieldType.TEXT)
            .weight(3.0f)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("模板编码")
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("模板内容")
            .type(FieldType.TEXT)
            .weight(1.0f)
            .searchable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label("状态")
            .type(FieldType.KEYWORD)
            .weight(0.5f)
            .searchable(false)
            .aggregatable(true)
            .build());
  }

  @Override
  public MsgTemplate loadById(String id) {
    return msgTemplateRepository.selectById(id);
  }
}
