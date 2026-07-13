package com.njydsz.pmis.common.search.core;

import java.util.List;

import com.njydsz.pmis.common.search.api.SearchRequest;
import com.njydsz.pmis.common.search.api.SearchResponse;
import com.njydsz.pmis.common.search.api.SearchSuggestion;

/**
 * 搜索引擎 SPI
 * <p>
 * 统一搜索引擎抽象，支持多种后端实现（PG tsvector、Elasticsearch、内存等）。
 * 业务模块通过 {@code SearchProvider} 注册可搜索实体，搜索引擎负责执行实际搜索。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #search} - 执行搜索</li>
 *   <li>{@link #index} - 索引单文档</li>
 *   <li>{@link #bulkIndex} - 批量索引</li>
 *   <li>{@link #deleteIndex} - 删除索引</li>
 *   <li>{@link #suggest} - 搜索建议</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface SearchEngine {

    /**
     * 执行搜索
     *
     * @param request 搜索请求
     * @return 搜索响应
     */
    SearchResponse search(SearchRequest request);

    /**
     * 索引单文档（新增/更新）
     *
     * @param document 索引文档
     */
    void index(IndexDocument document);

    /**
     * 批量索引
     *
     * @param documents 索引文档列表
     */
    void bulkIndex(List<IndexDocument> documents);

    /**
     * 删除索引
     *
     * @param type       实体类型
     * @param documentId 文档 ID
     */
    void deleteIndex(String type, String documentId);

    /**
     * 搜索建议（自动补全）
     *
     * @param prefix 前缀
     * @param limit  最大返回数
     * @return 搜索建议
     */
    SearchSuggestion suggest(String prefix, int limit);

    /**
     * 删除指定类型的全部索引
     *
     * @param type 实体类型
     */
    void deleteAllIndices(String type);

    /**
     * 获取引擎名称
     *
     * @return 引擎名称（如 "pg-tsvector"、"elasticsearch"）
     */
    String getName();

    /**
     * 检查引擎是否可用
     *
     * @return 可用返回 true
     */
    boolean isAvailable();
}
