package com.njydsz.common.search.core;

/**
 * 引擎能力描述
 *
 * <p>各引擎通过 {@link SearchStrategy#getCapability()} 声明自身能力， 服务层按能力自动降级（如引擎不支持高亮，则不传高亮参数）。
 *
 * @param supportsFullText 是否支持全文检索
 * @param supportsFuzzy 是否支持模糊匹配
 * @param supportsHighlight 是否支持高亮
 * @param supportsAggregation 是否支持聚合分面
 * @param supportsCursor 是否支持游标分页
 * @param supportsSuggest 是否支持搜索建议
 * @param supportsIndexing 是否支持显式索引操作（false = 无需显式索引，如 RediSearch）
 * @author ydsz-team
 * @since 26.09.01
 */
public record EngineCapability(
    boolean supportsFullText,
    boolean supportsFuzzy,
    boolean supportsHighlight,
    boolean supportsAggregation,
    boolean supportsCursor,
    boolean supportsSuggest,
    boolean supportsIndexing) {
  /**
   * 全部能力支持（PG/ES/Solr/OpenSearch）。
   *
   * @return 七项能力全部为 {@code true} 的能力描述，不会为 {@code null}
   */
  public static EngineCapability full() {
    return new EngineCapability(true, true, true, true, true, true, true);
  }

  /**
   * 仅搜索，不支持显式索引（RediSearch 直接索引数据源）。
   *
   * @return 关闭游标分页与显式索引的能力描述，不会为 {@code null}
   */
  public static EngineCapability searchOnly() {
    return new EngineCapability(true, true, true, true, false, true, false);
  }

  /**
   * 最小能力（内存引擎）。
   *
   * @return 仅开启全文检索、高亮、建议与显式索引，关闭模糊匹配、聚合与游标分页的能力描述，不会为 {@code null}
   */
  public static EngineCapability minimal() {
    return new EngineCapability(true, false, true, false, false, true, true);
  }
}
