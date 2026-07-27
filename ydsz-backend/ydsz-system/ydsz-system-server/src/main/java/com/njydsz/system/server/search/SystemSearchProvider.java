package com.njydsz.system.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.infra.mapper.ConfigMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统配置搜索提供者。
 *
 * <p>将系统配置、字典项注册到统一搜索体系，支持配置键、值搜索。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemSearchProvider implements SearchProvider<ConfigDO> {

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
    public IndexDocument toIndexDocument(ConfigDO entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("config")
                .title(entity.getConfigKey())
                .subtitle(entity.getConfigGroup())
                .content(buildSearchableText(entity))
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
                SearchField.builder().name("title").label("配置键").weight(3.0f).highlightable(true).build(),
                SearchField.builder().name("subtitle").label("配置分组").weight(2.0f).highlightable(true).build(),
                SearchField.builder().name("content").label("配置值").weight(1.0f).highlightable(true).build()
        );
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ConfigDO::getId);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(ConfigDO::getTenantId, tenantId);
        }
        return configMapper.selectList(wrapper)
                .stream()
                .map(ConfigDO::getId)
                .toList();
    }

    @Override
    public ConfigDO loadById(String id) {
        return configMapper.selectById(id);
    }

    private String buildSearchableText(ConfigDO entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getConfigKey() != null) {
            sb.append(entity.getConfigKey());
        }
        if (entity.getConfigGroup() != null) {
            sb.append(' ').append(entity.getConfigGroup());
        }
        if (entity.getConfigValue() != null) {
            sb.append(' ').append(entity.getConfigValue());
        }
        if (entity.getDescription() != null) {
            sb.append(' ').append(entity.getDescription());
        }
        return sb.toString();
    }
}