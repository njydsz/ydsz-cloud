package com.njydsz.userinfo.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户搜索提供者。
 *
 * <p>将用户数据注册到统一搜索体系，支持用户名、真实姓名、邮箱搜索。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserinfoSearchProvider implements SearchProvider<UserAccountDO> {

    private final UserAccountMapper userAccountMapper;

    @Override
    public String getType() {
        return "user";
    }

    @Override
    public String getTypeLabel() {
        return "用户";
    }

    @Override
    public IndexDocument toIndexDocument(UserAccountDO entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("user")
                .title(entity.getRealName())
                .subtitle(entity.getUsername())
                .content(buildSearchableText(entity))
                .path("/user/profile/" + entity.getId())
                .tenantId(entity.getTenantId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt() != null
                        ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt() != null
                        ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .status(entity.getStatus())
                .build();
    }

    @Override
    public List<SearchField> getSearchableFields() {
        return List.of(
                SearchField.builder().name("title").label("真实姓名").weight(3.0f).highlightable(true).build(),
                SearchField.builder().name("subtitle").label("用户名").weight(2.0f).highlightable(true).build(),
                SearchField.builder().name("content").label("全文").weight(1.0f).highlightable(true).build()
        );
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserAccountDO::getId);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(UserAccountDO::getTenantId, tenantId);
        }
        return userAccountMapper.selectList(wrapper)
                .stream()
                .map(UserAccountDO::getId)
                .toList();
    }

    @Override
    public UserAccountDO loadById(String id) {
        return userAccountMapper.selectById(id);
    }

    private String buildSearchableText(UserAccountDO entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getRealName() != null) {
            sb.append(entity.getRealName());
        }
        if (entity.getUsername() != null) {
            sb.append(' ').append(entity.getUsername());
        }
        if (entity.getEmail() != null) {
            sb.append(' ').append(entity.getEmail());
        }
        if (entity.getPhone() != null) {
            sb.append(' ').append(entity.getPhone());
        }
        if (entity.getDeptName() != null) {
            sb.append(' ').append(entity.getDeptName());
        }
        return sb.toString();
    }
}