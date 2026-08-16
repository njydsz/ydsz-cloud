package com.njydsz.common.search.provider;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.core.IndexDocument;
import java.util.Collections;
import java.util.List;

/**
 * 搜索数据提供者 SPI。
 *
 * <p>各业务模块实现此接口，将自身实体注册到统一搜索体系。 搜索引擎通过 Provider 获取索引文档、权限过滤与全量数据加载能力。
 *
 * <p><b>实现示例：</b>
 *
 * <pre>{@code
 * @Component
 * public class ProjectSearchProvider implements SearchProvider<ProjectDO> {
 *     @Override
 *     public String getType() { return "project"; }
 *
 *     @Override
 *     public IndexDocument toIndexDocument(ProjectDO entity) {
 *         return IndexDocument.builder()
 *             .id(entity.getId())
 *             .type("project")
 *             .title(entity.getProjectName())
 *             .subtitle(entity.getCustomerName())
 *             .build();
 *     }
 *
 *     @Override
 *     public List<ProjectDO> loadAll(String tenantId) {
 *         return projectMapper.selectAll(tenantId);
 *     }
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SearchProvider<T> {

  /**
   * 获取实体类型标识。
   *
   * @return 类型标识（如 "project"、"contract"、"wiki"）
   */
  String getType();

  /**
   * 将实体转换为索引文档。
   *
   * @param entity 业务实体
   * @return 索引文档
   */
  IndexDocument toIndexDocument(T entity);

  /**
   * 将索引文档转换为搜索命中。
   *
   * @param document 索引文档
   * @param request 搜索请求
   * @return 搜索命中
   */
  default SearchHit toSearchHit(IndexDocument document, SearchRequest request) {
    return SearchHit.builder()
        .id(document.getId())
        .type(document.getType())
        .title(document.getTitle())
        .subtitle(document.getSubtitle())
        .snippet(document.getSnippet())
        .path(document.getPath())
        .status(document.getStatus())
        .tags(document.getTags())
        .createdAt(document.getCreatedAt() != null ? document.getCreatedAt().toString() : null)
        .updatedAt(document.getUpdatedAt() != null ? document.getUpdatedAt().toString() : null)
        .build();
  }

  /**
   * 获取权限过滤条件。
   *
   * <p>返回的过滤条件将被追加到搜索请求中，确保用户只能搜索到有权限的数据。
   *
   * @param context 提供者上下文
   * @return 过滤条件列表，为空表示无额外过滤
   */
  default List<SearchFilter> getFilters(SearchProviderContext context) {
    return Collections.emptyList();
  }

  /**
   * 加载该类型的全量实体列表（用于全量索引重建与一致性校验）。
   *
   * <p>相比于分别调用 {@code getAllDocumentIds} + {@code loadById} 的两步模式， 一次性返回实体列表可减少业务方实现的 SPI 方法数量，
   * 同时避免调用方先获取 ID 再逐个加载的 N+1 查询问题。
   *
   * @param tenantId 租户 ID；为 {@code null} 或空表示加载全量（跨租户）
   * @return 实体列表；无数据时返回空列表而非 {@code null}
   */
  default List<T> loadAll(String tenantId) {
    return Collections.emptyList();
  }
}
