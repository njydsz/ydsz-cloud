package com.njydsz.literule.server.search;

import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.literule.domain.repository.RuleDefinitionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;

/**
 * 规则定义搜索提供者 — 将规则定义数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleSearchProvider implements SearchProvider<RuleDefinitionVO> {

    /** 搜索权重：规则名称完全匹配 */
  private static final float WEIGHT_NAME_MATCH = 3.0f;

  /** 搜索权重：规则编码匹配 */
  private static final float WEIGHT_CODE_MATCH = 1.5f;

  /** 搜索权重：规则描述匹配 */
  private static final float WEIGHT_DESC_MATCH = 0.5f;

  private final RuleDefinitionRepository ruleDefinitionRepository;

  @Override
  public String getType() {
    return "rule";
  }

  public String getTypeLabel() {
    return "规则";
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
            .label("规则名称")
            .type(FieldType.TEXT)
            .weight(WEIGHT_NAME_MATCH)
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
            .weight(WEIGHT_CODE_MATCH)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label("状态")
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_DESC_MATCH)
            .searchable(false)
            .aggregatable(true)
            .build());
  }

  public RuleDefinitionVO loadById(String id) {
    return ruleDefinitionRepository.findById(id).orElse(null);
  }
}
