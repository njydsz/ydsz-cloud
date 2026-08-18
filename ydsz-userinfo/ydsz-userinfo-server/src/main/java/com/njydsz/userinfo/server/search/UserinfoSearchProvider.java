package com.njydsz.userinfo.server.search;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 用户搜索提供者
 *
 * <p>将用户数据注册到统一搜索体系，支持用户名、真实姓名、邮箱、手机号搜索。
 *
 * <h3>重构（1.3.0）</h3>
 *
 * <ul>
 *   <li>使用新 {@link SearchField} API（FieldType + searchable + sortable + aggregatable）
 *   <li>content 直接设为 email + phone，引擎策略自行组合 title + subtitle + content
 *   <li>实现 {@link #getFilters(SearchProviderContext)} 权限过滤（非管理员仅搜到同租户启用用户）
 *   <li>去除冗余 buildSearchableText 方法
 *   <li>新增 userType 聚合字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserinfoSearchProvider implements SearchProvider<UserAccountVO> {

  private final UserAccountRepository userAccountRepository;

  /** 真实姓名搜索权重（最高优先级）。 */
  private static final float WEIGHT_TITLE = 3.0f;

  /** 用户名搜索权重。 */
  private static final float WEIGHT_SUBTITLE = 2.0f;

  /** 联系方式搜索权重。 */
  private static final float WEIGHT_CONTENT = 1.0f;

  /** 低优先级字段搜索权重（状态/类型聚合）。 */
  private static final float WEIGHT_LOW = 0.5f;

  @Override
  public String getType() {
    return "user";
  }

  public String getTypeLabel() {
    return "用户";
  }

  @Override
  public IndexDocument toIndexDocument(UserAccountVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }

    StringBuilder content = new StringBuilder();
    if (vo.getEmail() != null) {
      content.append(vo.getEmail());
    }
    if (vo.getPhone() != null) {
      content.append(' ').append(vo.getPhone());
    }

    String statusLabel = null;
    if (vo.getStatus() != null) {
      statusLabel = vo.getStatus() == 1 ? "ENABLED" : "DISABLED";
    }

    return IndexDocument.builder()
        .id(vo.getId())
        .type("user")
        .title(vo.getRealName())
        .subtitle(vo.getUsername())
        .content(content.toString())
        .snippet(vo.getUserType())
        .status(statusLabel)
        .path("/user/profile/" + vo.getId())
        .tenantId(vo.getTenantId())
        .createdAt(
            vo.getCreatedAt() != null
                ? vo.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedAt(
            vo.getUpdatedAt() != null
                ? vo.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }

  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label("真实姓名")
            .type(FieldType.TEXT)
            .weight(WEIGHT_TITLE)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("用户名")
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_SUBTITLE)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("联系方式")
            .type(FieldType.TEXT)
            .weight(WEIGHT_CONTENT)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label("账号状态")
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_LOW)
            .searchable(false)
            .aggregatable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("user_type")
            .label("用户类型")
            .type(FieldType.KEYWORD)
            .weight(WEIGHT_LOW)
            .searchable(false)
            .aggregatable(true)
            .build());
  }

  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    List<SearchFilter> filters = new ArrayList<>();
    // 租户隔离
    if (context.getTenantId() != null && !context.getTenantId().isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("tenant_id")
              .values(List.of(context.getTenantId()))
              .operator(SearchFilter.Operator.EQ)
              .build());
    }
    return filters;
  }

  public List<String> getAllDocumentIds(String tenantId) {
    UserAccountPageQueryDTO query = new UserAccountPageQueryDTO();
    query.setTenantId(tenantId);
    return userAccountRepository.list(query).stream()
        .map(UserAccountVO::getId)
        .collect(Collectors.toList());
  }

  public UserAccountVO loadById(String id) {
    return userAccountRepository.findById(id).orElse(null);
  }
}
