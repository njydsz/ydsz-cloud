package com.remisoft.cronjob.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.remisoft.common.search.core.IndexDocument;
import com.remisoft.common.search.core.SearchField;
import com.remisoft.common.search.core.SearchField.FieldType;
import com.remisoft.common.search.provider.SearchProvider;
import com.remisoft.cronjob.domain.entity.dag.JobDag;
import com.remisoft.cronjob.infra.mapper.dag.JobDagMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DAG 工作流搜索提供者 — 将 DAG 定义注册到统一搜索体系。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobDagSearchProvider implements SearchProvider<JobDag> {

    private final JobDagMapper jobDagMapper;

    @Override
    public String getType() {
        return "job_dag";
    }

    @Override
    public String getTypeLabel() {
        return "DAG工作流";
    }

    @Override
    public IndexDocument toIndexDocument(JobDag entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return IndexDocument.builder()
                .id(entity.getId())
                .type("job_dag")
                .title(entity.getDagName())
                .subtitle(entity.getDagKey())
                .content(entity.getDescription())
                .snippet(entity.getTriggerType())
                .status(entity.getDagStatus())
                .path("/cronjob/dag/" + entity.getId())
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
        return List.of(
                SearchField.builder()
                        .name("title").label("DAG名称").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("DAG Key").type(FieldType.TEXT)
                        .weight(2.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("描述").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("DAG状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public JobDag loadById(String id) {
        return jobDagMapper.selectById(id);
    }
}
