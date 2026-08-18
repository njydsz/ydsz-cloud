package com.njydsz.system.server.search;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.repository.ConfigRepository;

/**
 * 系统配置搜索提供者
 *
 * <p>将 {@link Config} 注册到统一搜索体系，支持配置键、配置值、配置分组的全文搜索与聚合分析。
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code configKey} → {@code title}（配置键）
 *   <li>{@code configGroup} → {@code subtitle}（配置分组）
 *   <li>{@code configValue + description} → {@code content}（全文检索）
 *   <li>{@code description} → {@code snippet}（搜索结果摘要）
 * </ul>
 *
 * <p><b>权限过滤：</b>非管理员仅能检索公开配置（{@code is_public=1}），私有配置（含密钥、连接地址）不会泄露。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigVO 系统配置 VO
 * @see SearchProvider 统一搜索 Provider 接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemSearchProvider implements SearchProvider<ConfigVO> {

  private final ConfigRepository configRepository;

  /**
   * 获取搜索类型标识。
   *
   * @return 固定返回 {@code "config"}，作为 {@link IndexDocument#type} 字段值
   */
  @Override
  public String getType() {
    return "config";
  }

  /**
   * 将 {@link Config} 实体转换为搜索索引文档。
   *
   * @param entity 系统配置 VO（不可为 null，且必须包含 ID）
   * @return 索引文档；入参为 null 或 ID 为空时返回 null
   */
  @Override
  public IndexDocument toIndexDocument(ConfigVO entity) {
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
        .build();
  }

  /**
   * 计算当前上下文的搜索过滤条件。
   *
   * <p>管理员可搜索全部配置；非管理员仅能搜索公开配置（{@code is_public = 1}）。
   *
   * @param context 搜索上下文（含用户 ID / 租户 ID / 角色 / 是否管理员等）
   * @return 过滤条件列表；管理员返回空列表
   */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    return List.of(
        SearchFilter.builder()
            .field("is_public")
            .values(List.of("1"))
            .operator(SearchFilter.Operator.EQ)
            .build());
  }

  /**
   * 加载指定租户下的全部配置实体（用于全量重建索引与一致性校验）。
   *
   * <p>已过滤软删除记录（{@code deleted=0}）。
   *
   * @param tenantId 租户 ID（null 或空表示加载全量）
   * @return 未删除配置实体列表
   */
  @Override
  public List<ConfigVO> loadAll(String tenantId) {
    return configRepository.findByTenantId(tenantId);
  }
}
