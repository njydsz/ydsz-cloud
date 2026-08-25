package com.njydsz.workflow.server.search;

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
import com.njydsz.workflow.infra.entity.FlowTemplate;
import com.njydsz.workflow.infra.mapper.FlowTemplateMapper;

/**
 * 工作流模板搜索提供者 — 将流程模板数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowSearchProvider implements SearchProvider<FlowTemplate> {

    /** 模板名称匹配权重 */
  private static final float FIELD_WEIGHT = 3.0f;

  private final FlowTemplateMapper flowTemplateMapper;

  @Override
  public String getType() {
    return "workflow";
  }

  @Override
  public String getTypeLabel() {
    return MessageUtils.getMessage("workflow.search.typeLabel", "流程模板");
  }

  @Override
  public IndexDocument toIndexDocument(FlowTemplate entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(entity.getId())
        .type("workflow")
        .title(entity.getTemplateName())
        .subtitle(entity.getCategory())
        .content(entity.getDescription())
        .snippet(entity.getTemplateCode())
        .status(entity.getStatus())
        .path("/workflow/template/" + entity.getId())
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
            .label(MessageUtils.getMessage("workflow.search.field.name", "模板名称"))
            .type(FieldType.TEXT)
            .weight(FIELD_WEIGHT)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label(MessageUtils.getMessage("workflow.search.field.category", "分类"))
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label(MessageUtils.getMessage("workflow.search.field.description", "描述"))
            .type(FieldType.TEXT)
            .weight(1.0f)
            .searchable(true)
            .build());
  }

  @Override
  public FlowTemplate loadById(String id) {
    return flowTemplateMapper.selectById(id);
  }
}
