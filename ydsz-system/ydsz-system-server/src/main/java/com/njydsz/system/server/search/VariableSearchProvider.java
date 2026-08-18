package com.njydsz.system.server.search;

import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.system.infra.entity.Variable;
import com.njydsz.system.domain.repository.VariableRepository;

/**
 * 系统变量搜索提供者（P1-5：补齐变量搜索能力）
 *
 * <p>将 {@link Variable} 注册到统一搜索体系，支持按变量键 / 变量值 / 描述的全文搜索。
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code variableKey} → {@code title}（变量键）
 *   <li>{@code variableValue + description} → {@code content}（全文检索）
 * </ul>
 *
 * <p><b>权限语义：</b>变量可能承载业务侧敏感参数，非管理员仅能检索启用状态（{@code status=ENABLED}）的变量。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see Variable 系统变量实体
 * @see SearchProvider 统一搜索 Provider 接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VariableSearchProvider implements SearchProvider<Variable> {

  private final VariableRepository variableRepository;

  /**
   * 获取搜索类型标识。
   *
   * @return 固定返回 {@code "variable"}，作为 {@link IndexDocument#type} 字段值
   */
  @Override
  public String getType() {
    return "variable";
  }

  /**
   * 将 {@link Variable} 实体转换为搜索索引文档。
   *
   * @param entity 系统变量实体
   * @return 索引文档；入参为 null 或 ID 为空时返回 null
   */
  @Override
  public IndexDocument toIndexDocument(Variable entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }

    StringBuilder content = new StringBuilder();
    if (entity.getVariableValue() != null) {
      content.append(entity.getVariableValue());
    }
    if (entity.getDescription() != null) {
      content.append(' ').append(entity.getDescription());
    }

    return IndexDocument.builder()
        .id(entity.getId())
        .type("variable")
        .title(entity.getVariableKey())
        .subtitle("系统变量")
        .content(content.toString())
        .snippet(entity.getDescription())
        .status(entity.getStatus())
        .path("/system/variable/" + entity.getId())
        .tenantId(entity.getTenantId())
        .createdBy(entity.getCreatedBy())
        .createdAt(
            entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(
            entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }

  /**
   * 计算当前上下文的搜索过滤条件。
   *
   * <p>非管理员仅能检索启用状态的变量（避免业务敏感参数泄露给普通用户）。
   *
   * @param context 搜索上下文（含是否管理员标识）
   * @return 过滤条件列表；管理员返回空列表
   */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    return List.of(
        SearchFilter.builder()
            .field("status")
            .values(List.of("ENABLED"))
            .operator(SearchFilter.Operator.EQ)
            .build());
  }

  /**
   * 加载指定租户下的全部变量实体（用于全量重建索引）。
   *
   * @param tenantId 租户 ID（null 或空表示全量）
   * @return 未删除变量实体列表
   */
  @Override
  public List<Variable> loadAll(String tenantId) {
    return variableRepository.findByTenantId(tenantId);
  }
}
