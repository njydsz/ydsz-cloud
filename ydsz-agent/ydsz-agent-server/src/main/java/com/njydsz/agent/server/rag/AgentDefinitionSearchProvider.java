package com.njydsz.agent.server.rag;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;

/**
 * Agent 定义搜索提供者 — 将 Agent 定义数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDefinitionSearchProvider implements SearchProvider<AgentDefinitionVO> {

  private final AgentDefinitionRepository agentDefinitionRepository;

  @Override
  public String getType() {
    return "agent";
  }

  @Override
  public String getTypeLabel() {
    return "Agent";
  }

  @Override
  public IndexDocument toIndexDocument(AgentDefinitionVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("agent")
        .title(vo.getAgentName())
        .subtitle(vo.getAgentType())
        .content(vo.getDescription())
        .snippet(vo.getAgentCode())
        .path("/agent/definition/" + vo.getId())
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
    // 字段权重反映相关性重要性：名称(title 3.0) > 类型(subtitle 2.0) > 描述(content 1.0)
    return List.of(
        SearchField.builder()
            .name("title")
            .label("Agent名称")
            .type(FieldType.TEXT)
            .weight(3.0f)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("类型")
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("描述")
            .type(FieldType.TEXT)
            .weight(1.0f)
            .searchable(true)
            .build());
  }

  @Override
  public AgentDefinitionVO loadById(String id) {
    return agentDefinitionRepository.findById(id).orElse(null);
  }
}
