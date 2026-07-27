package com.njydsz.userinfo.server.search;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户搜索提供者
 *
 * <p>将用户数据注册到统一搜索体系，支持用户名、真实姓名、邮箱、手机号搜索。
 *
 * <h3>重构（1.3.0）</h3>
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）</li>
 *   <li>content 直接设为 email + phone，引擎策略自行组合 title + subtitle + content</li>
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到同租户启用用户）</li>
 *   <li>去除冗余 buildSearchableText 方法</li>
 *   <li>新增 userType 聚合字段</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserinfoSearchProvider implements SearchProvider<UserAccount> {

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
    public IndexDocument toIndexDocument(UserAccount entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        if (entity.getEmail() != null) {
            content.append(entity.getEmail());
        }
        if (entity.getPhone() != null) {
            content.append(' ').append(entity.getPhone());
        }

        return IndexDocument.builder()
                .id(entity.getId())
                .type("user")
                .title(entity.getRealName())
                .subtitle(entity.getUsername())
                .content(content.toString())
                .snippet(entity.getUserType())
                .status(entity.getStatus())
                .path("/user/profile/" + entity.getId())
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
                        .name("title").label("真实姓名").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("用户名").type(FieldType.KEYWORD)
                        .weight(2.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("联系方式").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("账号状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("user_type").label("用户类型").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public List<SearchFilter> getFilters(SearchProviderContext context) {
        if (context == null || context.isAdmin()) {
            return List.of();
        }
        List<SearchFilter> filters = new ArrayList<>();
        // 租户隔离
        if (context.getTenantId() != null && !context.getTenantId().isBlank()) {
            filters.add(SearchFilter.builder()
                    .field("tenant_id")
                    .values(List.of(context.getTenantId()))
                    .operator(SearchFilter.Operator.EQ)
                    .build());
        }
        return filters;
    }

    @Override
    public List<String> getAllDocumentIds(String tenantId) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserAccount::getId);
        wrapper.eq(UserAccount::getDeleted, 0);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(UserAccount::getTenantId, tenantId);
        }
        return userAccountMapper.selectList(wrapper)
                .stream()
                .map(UserAccount::getId)
                .toList();
    }

    @Override
    public UserAccount loadById(String id) {
        return userAccountMapper.selectById(id);
    }
}
