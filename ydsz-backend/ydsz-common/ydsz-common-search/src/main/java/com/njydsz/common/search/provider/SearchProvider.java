package com.njydsz.common.search.provider;

import java.util.Collections;
import java.util.List;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.SearchField;

/**
 * 搜索数据提供者 SPI
 * <p>
 * 各业务模块实现此接口，将自身实体注册到统一搜索体系。
 * 搜索引擎通过 Provider 获取可搜索字段定义、索引文档和权限过滤。
 *
 * <p><b>实现示例：</b>
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
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 * @author ydsz-team
 * @since 1.4.0
 */
public interface SearchProvider<T> {

    /**
     * 获取实体类型标识
     *
     * @return 类型标识（如 "project"、"contract"、"wiki"）
     */
    String getType();

    /**
     * 获取类型标签（中文显示名）
     *
     * @return 类型标签
     */
    default String getTypeLabel() {
        return getType();
    }

    /**
     * 将实体转换为索引文档
     *
     * @param entity 业务实体
     * @return 索引文档
     */
    IndexDocument toIndexDocument(T entity);

    /**
     * 将索引文档转换为搜索命中
     *
     * @param document 索引文档
     * @param request  搜索请求
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
     * 获取可搜索字段定义
     *
     * @return 字段列表
     */
    default List<SearchField> getSearchableFields() {
        return Collections.emptyList();
    }

    /**
     * 获取权限过滤条件
     * <p>
     * 返回的过滤条件将被追加到搜索请求中，确保用户只能搜索到有权限的数据。
     *
     * @param context 提供者上下文
     * @return 过滤条件列表，为空表示无额外过滤
     */
    default List<SearchFilter> getFilters(SearchProviderContext context) {
        return Collections.emptyList();
    }

    /**
     * 获取该类型的全量数据 ID 列表（用于全量索引重建）
     *
     * @param tenantId 租户 ID（为空表示全部）
     * @return ID 列表
     */
    default List<String> getAllDocumentIds(String tenantId) {
        return Collections.emptyList();
    }

    /**
     * 根据 ID 加载实体（用于全量索引重建）
     *
     * @param id 实体 ID
     * @return 实体对象，不存在返回 null
     */
    default T loadById(String id) {
        return null;
    }
}
