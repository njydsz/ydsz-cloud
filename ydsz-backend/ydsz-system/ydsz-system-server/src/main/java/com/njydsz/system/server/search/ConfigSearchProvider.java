package com.njydsz.system.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.infra.mapper.ConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置搜索提供者 — 将系统配置数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSearchProvider implements SearchProvider<Config> {

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
        return IndexDocument.builder()
                .id(entity.getId())
                .type("config")
                .title(entity.getConfigKey())
                .subtitle(entity.getConfigGroup())
                .content(entity.getConfigValue())
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
                        .name("title").label("配置键").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("配置组").type(FieldType.KEYWORD)
                        .weight(2.0f).searchable(true).aggregatable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("配置值").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build()
        );
    }

    @Override
    public Config loadById(String id) {
        return configMapper.selectById(id);
    }
}
