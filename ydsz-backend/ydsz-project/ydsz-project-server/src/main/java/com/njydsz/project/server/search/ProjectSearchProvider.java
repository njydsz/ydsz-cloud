package com.njydsz.project.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.project.domain.entity.project.ProjectInitiationDO;
import com.njydsz.project.infra.mapper.project.ProjectInitiationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目立项搜索提供者
 *
 * <p>将项目立项数据注册到统一搜索体系，支持项目名称、租户、创建者搜索。
 * 随着实体字段扩展，可搜索内容将自动增强。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchProvider implements SearchProvider<ProjectInitiationDO> {

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
    public IndexDocument toIndexDocument(ProjectInitiationDO entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("project")
                .title(entity.getId())
                .subtitle(entity.getTenantId())
                .content(buildSearchableText(entity))
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
                SearchField.builder().name("title").label("项目名称").weight(3.0f).highlightable(true).build(),
                SearchField.builder().name("subtitle").label("租户").weight(1.0f).aggregatable(true).build(),
                SearchField.builder().name("content").label("全文").weight(1.0f).highlightable(true).build()
        );
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<ProjectInitiationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ProjectInitiationDO::getId);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(ProjectInitiationDO::getTenantId, tenantId);
        }
        return projectInitiationMapper.selectList(wrapper)
                .stream()
                .map(ProjectInitiationDO::getId)
                .toList();
    }

    @Override
    public ProjectInitiationDO loadById(String id) {
        return projectInitiationMapper.selectById(id);
    }

    private String buildSearchableText(ProjectInitiationDO entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getId() != null) {
            sb.append(entity.getId());
        }
        if (entity.getTenantId() != null) {
            sb.append(' ').append(entity.getTenantId());
        }
        if (entity.getCreatedBy() != null) {
            sb.append(' ').append(entity.getCreatedBy());
        }
        return sb.toString();
    }
}
