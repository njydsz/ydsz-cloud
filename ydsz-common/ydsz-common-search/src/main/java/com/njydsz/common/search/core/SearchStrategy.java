package com.njydsz.common.search.core;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.VectorSearchRequest;

/**
 * 搜索策略 SPI
 *
 * <p>所有搜索引擎必须实现此接口。通过策略模式，不同引擎（PG/ES/RediSearch/Solr/OpenSearch/Memory） 可以自由替换，业务模块通过 {@code
 * SearchEngineRegistry} 获取当前策略实例。
 *
 * <p>引擎可以按需额外实现 {@link IndexStrategy} 和 {@link SuggestStrategy}， 通过 {@link #getCapability()}
 * 声明自身能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SearchStrategy {

  /**
   * 执行全文搜索
   *
   * @param request 搜索请求
   * @return 搜索响应
   */
  SearchResponse search(SearchRequest request);

  /**
   * 执行向量语义搜索。
   *
   * <p>默认返回空结果，支持向量搜索的引擎（如 Elasticsearch、Meilisearch）应覆盖此方法。 调用方应在执行前通过 {@link EngineCapability#supportsVector()}
   * 检查引擎是否支持向量搜索。
   *
   * @param request 向量搜索请求
   * @return 搜索响应；不支持向量搜索的引擎返回空响应，不会为 {@code null}
   */
  default SearchResponse vectorSearch(VectorSearchRequest request) {
    return SearchResponse.empty(request.getPage(), request.getPageSize());
  }

  /**
   * 获取引擎名称
   *
   * @return 引擎名称（如 "pg"、"elasticsearch"、"redisearch"）
   */
  String getEngineName();

  /**
   * 检查引擎是否可用
   *
   * @return 可用返回 true
   */
  boolean isAvailable();

  /**
   * 获取引擎能力描述
   *
   * @return 引擎能力
   */
  EngineCapability getCapability();
}
