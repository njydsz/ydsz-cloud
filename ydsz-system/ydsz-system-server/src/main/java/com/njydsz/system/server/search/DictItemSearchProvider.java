package com.njydsz.system.server.search;

import java.time.ZoneId;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.infra.mapper.DictItemMapper;

/**
 * 字典项搜索提供者（P1-5：补齐字典搜索能力）
 *
 * <p>将 {@link DictItem} 注册到统一搜索体系，支持按字典项编码 / 展示值 / 类型编码的全文搜索。
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code itemCode} → {@code title}（{@code FieldType.KEYWORD}，权重 3.0）
 *   <li>{@code typeCode} → {@code subtitle}（{@code FieldType.KEYWORD}，权重 2.0，可聚合）
 *   <li>{@code itemValue + description} → {@code content}（{@code FieldType.TEXT}，权重 1.0）
 *   <li>{@code status} → 状态（可排序 / 可聚合，不可搜索）
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

  private final DictItemMapper dictItemMapper;

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
   * 获取搜索类型中文标签。
   *
   * @return 固定返回 {@code "数据字典"}，用于前端搜索结果分类展示
   */
  @Override
  public String getTypeLabel() {
    return "数据字典";
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
   * 声明可搜索字段 schema。
   *
   * @return 可搜索字段列表
   */
  @Override
  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label("字典项编码")
            .type(FieldType.KEYWORD)
            .weight(3.0f)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("字典类型")
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .highlightable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("展示值/描述")
            .type(FieldType.TEXT)
            .weight(1.0f)
            .searchable(true)
            .highlightable(true)
            .build(),
        SearchField.builder()
            .name("status")
            .label("状态")
            .type(FieldType.KEYWORD)
            .weight(0.5f)
            .searchable(false)
            .aggregatable(true)
            .sortable(true)
            .build());
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
   * 加载指定租户下的全部字典项 ID（用于全量重建索引）。
   *
   * @param tenantId 租户 ID（null 或空表示全量）
   * @return 未删除字典项 ID 列表
   */
  @Override
  public List<String> getAllDocumentIds(String tenantId) {
    LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
    wrapper.select(DictItem::getId);
    wrapper.eq(DictItem::getDeleted, 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq(DictItem::getTenantId, tenantId);
    }
    return dictItemMapper.selectList(wrapper).stream().map(DictItem::getId).toList();
  }

  /**
   * 按 ID 加载字典项实体。
   *
   * @param id 字典项 ID
   * @return 字典项实体；不存在或已删除时返回 null
   */
  @Override
  public DictItem loadById(String id) {
    return dictItemMapper.selectById(id);
  }
}
