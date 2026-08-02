package com.njydsz.agent.server.rag;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.infra.mapper.AgentDefinitionMapper;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 定义搜索提供者 — 将 Agent 定义数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDefinitionSearchProvider implements SearchProvider<AgentDefinitionDO> {

    private final AgentDefinitionMapper agentDefinitionMapper;

    @Override
    public String getType() {
        return "agent";
    }

    @Override
    public String getTypeLabel() {
        return "Agent";
    }

    @Override
    public IndexDocument toIndexDocument(AgentDefinitionDO entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return IndexDocument.builder()
                .id(entity.getId())
                .type("agent")
                .title(entity.getAgentName())
                .subtitle(entity.getAgentType())
                .content(entity.getDescription())
                .snippet(entity.getAgentCode())
                .status(entity.getStatus())
                .path("/agent/definition/" + entity.getId())
                .tenantId(entity.getTenantId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt() != null
                        ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt() != null
                        ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .build();
    }

    @Override
    public List<SearchField> getSearchableFields() {
        // 字段权重反映相关性重要性：名称(title 3.0) > 类型(subtitle 2.0) > 描述(content 1.0) > 状态(status 0.5)
        // status 不进入全文检索(searchable=false)，仅用于聚合筛选
        return List.of(
                SearchField.builder()
                        .name("title").label("Agent名称").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("类型").type(FieldType.KEYWORD)
                        .weight(2.0f).searchable(true).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("描述").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public AgentDefinitionDO loadById(String id) {
        return agentDefinitionMapper.selectById(id);
    }
}
