package com.njydsz.system.server.search;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;
import com.njydsz.common.search.core.SearchField.FieldType;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.infra.mapper.ConfigMapper;

/**
 * 系统配置搜索提供者
 *
 * <p>将 {@link com.njydsz.system.domain.entity.Config} 注册到统一搜索体系， 支持配置键、配置值、配置分组的全文搜索与聚合分析。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>声明搜索字段 schema（{@code configKey} / {@code configValue} / {@code configGroup} / {@code
 *       description}）
 *   <li>提供文档数据源：从 {@code ConfigMapper} 拉取全量配置
 *   <li>权限过滤：{@link #getFilters} 限制非管理员仅搜索 {@code isPublic=1} 的配置
 *   <li>聚合字段：{@code configGroup} 支持按分组聚合（如「工作流」「消息」「功能开关」）
 * </ul>
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code configKey} → {@code title}（{@code FieldType.KEYWORD}）
 *   <li>{@code configGroup} → {@code subtitle}（{@code FieldType.KEYWORD}）
 *   <li>{@code configValue + description} → {@code content}（{@code FieldType.TEXT}）
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用新 {@link SearchField} API（{@code FieldType + searchable + sortable + aggregatable}）
 *   <li>{@code content} 直接设为 {@code configValue + description}，引擎策略自行组合 title + subtitle + content
 *   <li>权限过滤由 {@link #getFilters(SearchProviderContext)} 实现，遵循 RBAC 模型
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 * @see SearchProvider 统一搜索 Provider 接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemSearchProvider implements SearchProvider<Config> {

  private final ConfigMapper configMapper;

  /**
   * 获取搜索类型标识
   *
   * @return 固定返回 {@code "config"}，作为 {@link IndexDocument#type} 字段值
   */
  @Override
  public String getType() {
    return "config";
  }

  /**
   * 获取搜索类型中文标签
   *
   * @return 固定返回 {@code "系统配置"}，用于前端搜索结果分类展示
   */
  @Override
  public String getTypeLabel() {
    return "系统配置";
  }

  /**
   * 将 {@link Config} 实体转换为搜索索引文档
   *
   * <p>字段映射规则：
   *
   * <ul>
   *   <li>{@code configKey} → {@code title}（用于列表展示 + 高亮）
   *   <li>{@code configGroup} → {@code subtitle}（用于分组聚合）
   *   <li>{@code configValue + description} → {@code content}（用于全文搜索）
   *   <li>{@code description} → {@code snippet}（用于搜索结果摘要）
   *   <li>{@code path} 固定为 {@code /system/config/{id}}，点击搜索结果跳转系统配置详情页
   * </ul>
   *
   * @param entity 系统配置实体（不可为 null，且必须包含 ID）
   * @return 索引文档；入参为 null 或 ID 为空时返回 null（视为无效文档）
   */
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
   * 声明可搜索字段 schema
   *
   * <p>四个搜索字段：
   *
   * <ul>
   *   <li><b>title</b>（配置键）— {@code KEYWORD} 类型，权重 3.0，可高亮、可排序
   *   <li><b>subtitle</b>（配置分组）— {@code KEYWORD} 类型，权重 2.0，可高亮、<b>可聚合</b>
   *   <li><b>content</b>（配置值）— {@code TEXT} 类型，权重 1.0，可高亮
   *   <li><b>status</b>（状态）— {@code KEYWORD} 类型，<b>不可搜索</b>，可聚合、可排序
   * </ul>
   *
   * <p>权重设计：title &gt; subtitle &gt; content &gt; status，<b>关键字命中标题比命中正文分数高</b>， 搜索结果更相关。
   *
   * @return 可搜索字段列表
   */
  @Override
  public List<SearchField> getSearchableFields() {
    return List.of(
        SearchField.builder()
            .name("title")
            .label("配置键")
            .type(FieldType.KEYWORD)
            .weight(3.0f)
            .searchable(true)
            .highlightable(true)
            .sortable(true)
            .build(),
        SearchField.builder()
            .name("subtitle")
            .label("配置分组")
            .type(FieldType.KEYWORD)
            .weight(2.0f)
            .searchable(true)
            .highlightable(true)
            .aggregatable(true)
            .build(),
        SearchField.builder()
            .name("content")
            .label("配置值")
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
   * 计算当前上下文的搜索过滤条件
   *
   * <p>权限语义：
   *
   * <ul>
   *   <li>管理员（{@link SearchProviderContext#isAdmin()} 返回 {@code true}）— 无任何过滤，可搜索全部配置（含 {@code
   *       isPublic=0} 私有配置）
   *   <li>非管理员 — 强制追加 {@code is_public = 1} 过滤条件，<b>只能搜到公开配置</b>， 私有配置（含密钥、连接地址）不会泄露给普通用户
   * </ul>
   *
   * <p>租户隔离：依赖 {@link IndexDocument#tenantId} 字段，搜索服务层在 ES / DB 查询时 自动追加 {@code tenant_id}
   * 条件，本方法不重复处理。
   *
   * @param context 搜索上下文（含用户 ID / 租户 ID / 角色 / 是否管理员等）
   * @return 过滤条件列表；管理员返回空列表
   */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    // 非管理员：仅搜到公开配置
    return List.of(
        SearchFilter.builder()
            .field("is_public")
            .values(List.of("1"))
            .operator(SearchFilter.Operator.EQ)
            .build());
  }

  /**
   * 加载指定租户下的全部配置 ID
   *
   * <p>用于全量重建索引场景：调用方遍历返回的 ID 列表逐条调用 {@link #loadById} 获取实体， 再通过 {@link #toIndexDocument} 转换为索引文档写入
   * ES。
   *
   * <p>本方法只返回 ID 列表（不返回实体），<b>避免一次性加载大量数据导致 OOM</b>。
   *
   * <p>已过滤软删除记录（{@code deleted=0}）。
   *
   * @param tenantId 租户 ID（{@code null} 或空字符串表示加载全量）
   * @return 该租户下全部未删除配置 ID 列表
   */
  @Override
  public List<String> getAllDocumentIds(String tenantId) {
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.select(Config::getId);
    wrapper.eq(Config::getDeleted, 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq(Config::getTenantId, tenantId);
    }
    return configMapper.selectList(wrapper).stream().map(Config::getId).toList();
  }

  /**
   * 按 ID 加载配置实体
   *
   * <p>全量重建索引时由索引服务调用，用于逐条加载实体并转换为索引文档。
   *
   * <p>已删除（{@code deleted=1}）的配置会返回 null，由调用方跳过。
   *
   * @param id 配置 ID
   * @return 配置实体；不存在或已删除时返回 null
   */
  @Override
  public Config loadById(String id) {
    return configMapper.selectById(id);
  }
}
