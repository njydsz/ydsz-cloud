package com.njydsz.pmis.project.server.search;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.search.core.IndexDocument;
import com.njydsz.pmis.common.search.core.SearchField;
import com.njydsz.pmis.common.search.provider.SearchProvider;
import com.njydsz.pmis.project.domain.entity.InitiationDO;

import lombok.extern.slf4j.Slf4j;

/**
 * 项目搜索提供者
 * <p>
 * 将项目立项实体注册到统一搜索体系，支持项目名称、客户名称、项目经理姓名、
 * 项目编号等字段的全文检索。
 *
 * <p>接入 {@code ydsz-pmis-common-search} SPI，替代原 {@code SearchServiceImpl} 中
 * 硬编码的 PG tsvector SQL，统一由 {@code PgSearchEngine} 执行搜索。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Component
public class ProjectSearchProvider implements SearchProvider<InitiationDO> {

    @Override
    public String getType() {
        return "project";
    }

    @Override
    public String getTypeLabel() {
        return "项目";
    }

    @Override
    public IndexDocument toIndexDocument(InitiationDO entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        // 构建可搜索文本（项目名称 + 客户名称 + 项目经理 + 项目编号 + 描述）
        StringBuilder searchableText = new StringBuilder();
        appendIfNotNull(searchableText, entity.getProjectName());
        appendIfNotNull(searchableText, entity.getCustomerName());
        appendIfNotNull(searchableText, entity.getPmName());
        appendIfNotNull(searchableText, entity.getProjectCode());
        appendIfNotNull(searchableText, entity.getSponsorName());
        appendIfNotNull(searchableText, entity.getDescription());

        return IndexDocument.builder()
                .id(entity.getId())
                .type("project")
                .title(entity.getProjectName())
                .subtitle(joinNonBlank(entity.getCustomerName(), entity.getPmName()))
                .content(searchableText.toString())
                .snippet(entity.getDescription() != null && entity.getDescription().length() > 200
                        ? entity.getDescription().substring(0, 200) + "..."
                        : entity.getDescription())
                .status(entity.getStage())
                .path("/project/initiation?highlight=" + entity.getId())
                .tenantId(entity.getTenantId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt() != null
                        ? Instant.from(entity.getCreatedAt()) : null)
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt() != null
                        ? Instant.from(entity.getUpdatedAt()) : null)
                .build();
    }

    @Override
    public List<SearchField> getSearchableFields() {
        return List.of(
                SearchField.builder().name("title").label("项目名称").weight(3.0f).highlightable(true).build(),
                SearchField.builder().name("subtitle").label("客户/经理").weight(2.0f).highlightable(true).build(),
                SearchField.builder().name("content").label("全文").weight(1.0f).highlightable(true).build(),
                SearchField.builder().name("status").label("阶段").weight(1.0f).aggregatable(true).build()
        );
    }

    private void appendIfNotNull(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(value);
        }
    }

    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append(" · ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
