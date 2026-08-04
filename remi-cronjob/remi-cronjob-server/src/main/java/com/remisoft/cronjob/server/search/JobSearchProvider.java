package com.remisoft.cronjob.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.remisoft.common.search.core.IndexDocument;
import com.remisoft.common.search.core.SearchField;
import com.remisoft.common.search.core.SearchField.FieldType;
import com.remisoft.common.search.provider.SearchProvider;
import com.remisoft.cronjob.domain.entity.job.Job;
import com.remisoft.cronjob.infra.mapper.job.JobMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务搜索提供者 — 将任务定义注册到统一搜索体系。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSearchProvider implements SearchProvider<Job> {

    private final JobMapper jobMapper;

    @Override
    public String getType() {
        return "job";
    }

    @Override
    public String getTypeLabel() {
        return "定时任务";
    }

    @Override
    public IndexDocument toIndexDocument(Job entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return IndexDocument.builder()
                .id(entity.getId())
                .type("job")
                .title(entity.getJobName())
                .subtitle(entity.getJobKey())
                .content(entity.getJobRemark())
                .snippet(entity.getCronExpression())
                .status(entity.getScheduleType() != null ? entity.getScheduleType() : "CRON")
                .path("/cronjob/job/" + entity.getId())
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
                        .name("title").label("任务名称").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("任务Key").type(FieldType.TEXT)
                        .weight(2.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("任务备注").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("调度类型").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public Job loadById(String id) {
        return jobMapper.selectById(id);
    }
}
