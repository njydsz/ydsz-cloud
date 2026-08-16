package com.njydsz.common.search.core;

import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;

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
 * @since 1.0.0
 */
public interface SearchStrategy {

  /**
   * 执行搜索
   *
   * @param request 搜索请求
   * @return 搜索响应
   */
  SearchResponse search(SearchRequest request);

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
