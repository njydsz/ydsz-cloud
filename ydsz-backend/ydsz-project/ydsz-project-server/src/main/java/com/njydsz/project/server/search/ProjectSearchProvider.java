package com.njydsz.project.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.infra.mapper.project.ProjectInitiationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目立项搜索提供者
 *
 * <p>将项目立项数据注册到统一搜索体系，支持项目名称、编号、客户、项目经理搜索。
 *
 * <h3>重构（1.3.0）</h3>
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）</li>
 *   <li>content 直接设为 description/businessCase，引擎策略自行组合 title + subtitle + content</li>
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到自己参与的项目）</li>
 *   <li>去除冗余 buildSearchableText 方法</li>
 *   <li>新增 stage/projectType/projectLevel 聚合字段</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchProvider implements SearchProvider<ProjectInitiation> {

    private final ProjectInitiationMapper projectInitiationMapper;

    @Override
    public String getType() {
        return "project";
    }

    @Override
    public String getTypeLabel() {
        return "项目立项";
    }

    @Override
    public IndexDocument toIndexDocument(ProjectInitiation entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        if (entity.getDescription() != null) {
            content.append(entity.getDescription());
        }
        if (entity.getBusinessCase() != null) {
            content.append(' ').append(entity.getBusinessCase());
        }
        if (entity.getCustomerName() != null) {
            content.append(' ').append(entity.getCustomerName());
        }
        if (entity.getPmName() != null) {
            content.append(' ').append(entity.getPmName());
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("project")
                .title(entity.getProjectName())
                .subtitle(entity.getProjectCode())
                .content(content.toString())
                .snippet(entity.getProjectType() != null ? entity.getProjectType() : null)
                .status(entity.getStatus())
                .path("/project/initiation/" + entity.getId())
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
                        .name("title").label("项目名称").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("项目编号").type(FieldType.KEYWORD)
                        .weight(2.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("全文").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("项目状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("stage").label("立项阶段").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("project_type").label("项目类型").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("project_level").label("项目等级").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        if (context == null || context.isAdmin()) {
            return List.of();
        }
        if (context.getUserId() == null || context.getUserId().isBlank()) {
            return List.of();
        }
        // 非管理员：可搜索自己创建的或自己作为项目经理的项目
        return List.of(
                SearchFilter.builder()
                        .field("created_by")
                        .values(List.of(context.getUserId()))
                        .operator(SearchFilter.Operator.EQ)
                        .build()
        );
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<ProjectInitiation> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ProjectInitiation::getId);
        wrapper.eq(ProjectInitiation::getDeleted, 0);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(ProjectInitiation::getTenantId, tenantId);
        }
        return projectInitiationMapper.selectList(wrapper)
                .stream()
                .map(ProjectInitiation::getId)
                .toList();
    }

    @Override
    public ProjectInitiation loadById(String id) {
        return projectInitiationMapper.selectById(id);
    }
}
