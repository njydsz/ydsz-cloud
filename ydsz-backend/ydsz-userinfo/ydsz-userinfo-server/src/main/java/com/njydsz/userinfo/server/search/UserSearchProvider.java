package com.njydsz.userinfo.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户搜索提供者 — 将用户账号数据注册到统一搜索体系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSearchProvider implements SearchProvider<UserAccount> {

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
        return IndexDocument.builder()
                .id(entity.getId())
                .type("user")
                .title(entity.getRealName())
                .subtitle(entity.getUsername())
                .content(entity.getEmail())
                .snippet(entity.getUserType())
                .status(entity.getStatus())
                .path("/user/detail/" + entity.getId())
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
                        .name("title").label("姓名").type(FieldType.TEXT)
                        .weight(3.0f).searchable(true).highlightable(true).sortable(true)
                        .build(),
                SearchField.builder()
                        .name("subtitle").label("用户名").type(FieldType.TEXT)
                        .weight(2.0f).searchable(true).highlightable(true)
                        .build(),
                SearchField.builder()
                        .name("content").label("邮箱").type(FieldType.TEXT)
                        .weight(1.0f).searchable(true)
                        .build(),
                SearchField.builder()
                        .name("status").label("状态").type(FieldType.KEYWORD)
                        .weight(0.5f).searchable(false).aggregatable(true)
                        .build()
        );
    }

    @Override
    public UserAccount loadById(String id) {
        return userAccountMapper.selectById(id);
    }
}
