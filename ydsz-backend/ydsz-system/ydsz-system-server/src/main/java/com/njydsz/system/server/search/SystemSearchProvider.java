package com.njydsz.system.server.search;

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
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.infra.mapper.ConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置搜索提供者
 *
 * <p>将系统配置注册到统一搜索体系，支持配置键、配置值、配置分组搜索。
 *
 * <h3>重构（1.3.0）</h3>
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）</li>
 *   <li>content 直接设为 configValue + description，引擎策略自行组合 title + subtitle + content</li>
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到 public 配置）</li>
 *   <li>去除冗余 buildSearchableText 方法</li>
 *   <li>新增 configGroup 聚合字段</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemSearchProvider implements SearchProvider<Config> {

    private final ConfigMapper configMapper;

    @Override
    public String getType() {
        return "config";
    }

    @Override
    public String getTypeLabel() {
        return "系统配置";
    }

    @Override
    public IndexDocument toIndexDocument(Config entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        if (entity.getConfigValue() != null) {
            content.append(entity.getConfigValue());
        }
        if (entity.getDescription() != null) {
            content.append(' ').append(entity.getDescription());
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("config")
                .title(entity.getConfigKey())
                .subtitle(entity.getConfigGroup())
                .content(content.toString())
                .snippet(entity.getDescription())
                .status(entity.getStatus())
                .path("/system/config/" + entity.getId())
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
                        .name("title").label("配置键").type(FieldType.KEYWORD)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("配置分组").type(FieldType.KEYWORD)
                        .weight(2.0f).searchable(true).highlightable(true).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("配置值").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true).sortable(true)
                        .build()
        );
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        if (context == null || context.isAdmin()) {
            return List.of();
        }
        // 非管理员：仅搜到公开配置
        return List.of(SearchFilter.builder()
                .field("is_public")
                .values(List.of("1"))
                .operator(SearchFilter.Operator.EQ)
                .build());
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Config::getId);
        wrapper.eq(Config::getDeleted, 0);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(Config::getTenantId, tenantId);
        }
        return configMapper.selectList(wrapper)
                .stream()
                .map(Config::getId)
                .toList();
    }

    @Override
    public Config loadById(String id) {
        return configMapper.selectById(id);
    }
}
