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
import com.njydsz.workflow.domain.repository.FlowTemplateRepository;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;

/**
 * 工作流模板搜索提供者 — 将流程模板数据注册到统一搜索体系。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper（符合 §34.2.3）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowSearchProvider implements SearchProvider<FlowTemplateVO> {

    /** 模板名称匹配权重 */
  private static final float FIELD_WEIGHT = 3.0f;

  private final FlowTemplateRepository flowTemplateRepository;

  @Override
  public String getType() {
    return "workflow";
  }

  @Override
  public String getTypeLabel() {
    return MessageUtils.getMessage("workflow.search.typeLabel", "流程模板");
  }

  @Override
  public IndexDocument toIndexDocument(FlowTemplateVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("workflow")
        .title(vo.getTemplateName())
        .subtitle(vo.getCategory())
        .content(vo.getDescription())
        .snippet(vo.getTemplateCode())
        .status(vo.getStatus())
        .path("/workflow/template/" + vo.getId())
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
  public FlowTemplateVO loadById(String id) {
    return flowTemplateRepository.findById(id).orElse(null);
  }
}
