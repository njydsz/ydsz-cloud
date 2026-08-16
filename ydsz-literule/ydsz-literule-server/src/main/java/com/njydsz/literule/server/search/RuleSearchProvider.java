package com.njydsz.literule.server.search;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.literule.domain.entity.RuleDefinitionDO;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则定义搜索提供者 — 将规则定义数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSearchProvider implements SearchProvider<RuleDefinitionDO> {

  private final RuleDefinitionMapper ruleDefinitionMapper;

  @Override
  public String getType() {
    return "rule";
  }

  @Override
  public String getTypeLabel() {
    return "规则";
  }

  @Override
  public IndexDocument toIndexDocument(RuleDefinitionDO entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(entity.getId())
        .type("rule")
        .title(entity.getRuleName())
        .subtitle(entity.getCategory())
        .content(entity.getRuleCode())
        .snippet(entity.getCategoryPath())
        .status(entity.getStatus())
        .path("/literule/rule/" + entity.getId())
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
            .label("规则名称")
            .type(FieldType.TEXT)
            .weight(3.0f)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("分类")
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("规则编码")
            .type(FieldType.KEYWORD)
            .weight(1.5f)
            .searchable(true)
            .highlightable(true)
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
  public RuleDefinitionDO loadById(String id) {
    return ruleDefinitionMapper.selectById(id);
  }
}
