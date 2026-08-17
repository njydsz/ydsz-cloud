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
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.infra.repository.DictRepository;

/**
 * 字典项搜索提供者（P1-5：补齐字典搜索能力）
 *
 * <p>将 {@link DictItem} 注册到统一搜索体系，支持按字典项编码 / 展示值 / 类型编码的全文搜索。
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code itemCode} → {@code title}（字典项编码）
 *   <li>{@code typeCode} → {@code subtitle}（字典类型）
 *   <li>{@code itemValue + description} → {@code content}（全文检索）
 * </ul>
 *
 * <p><b>权限语义：</b>字典项为业务基础数据（无敏感字段），登录用户均可检索，不做额外过滤； 租户隔离由搜索服务层按
 * {@code tenant_id} 自动注入。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see DictItem 字典项实体
 * @see SearchProvider 统一搜索 Provider 接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictItemSearchProvider implements SearchProvider<DictItem> {

  private final DictRepository dictRepository;

  /**
   * 获取搜索类型标识。
   *
   * @return 固定返回 {@code "dict"}，作为 {@link IndexDocument#type} 字段值
   */
  @Override
  public String getType() {
    return "dict";
  }

  /**
   * 将 {@link DictItem} 实体转换为搜索索引文档。
   *
   * @param entity 字典项实体
   * @return 索引文档；入参为 null 或 ID 为空时返回 null
   */
  @Override
  public IndexDocument toIndexDocument(DictItem entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }

    StringBuilder content = new StringBuilder();
    if (entity.getItemValue() != null) {
      content.append(entity.getItemValue());
    }
    if (entity.getDescription() != null) {
      content.append(' ').append(entity.getDescription());
    }

    return IndexDocument.builder()
        .id(entity.getId())
        .type("dict")
        .title(entity.getItemCode())
        .subtitle(entity.getTypeCode())
        .content(content.toString())
        .snippet(entity.getDescription())
        .status(entity.getStatus())
        .path("/system/dict/item/" + entity.getId())
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
   * <p>字典项为业务基础数据，登录用户均可检索，不返回额外过滤条件。
   *
   * @param context 搜索上下文
   * @return 空列表（无额外过滤）
   */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    return List.of();
  }

  /**
   * 加载指定租户下的全部字典项实体（用于全量重建索引）。
   *
   * @param tenantId 租户 ID（null 或空表示全量）
   * @return 未删除字典项实体列表
   */
  @Override
  public List<DictItem> loadAll(String tenantId) {
    return dictRepository.findByTenantId(tenantId);
  }
}
