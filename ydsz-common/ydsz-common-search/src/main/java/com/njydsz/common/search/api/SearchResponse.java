package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 统一搜索响应
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "统一搜索响应")
public class SearchResponse implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 搜索结果列表 */
  @Schema(description = "搜索结果列表")
  @Builder.Default
  private List<SearchHit> hits = Collections.emptyList();

  /** 总匹配数 */
  @Schema(description = "总匹配数")
  @Builder.Default
  private long total = 0L;

  /** 当前页码 */
  @Schema(description = "当前页码")
  private int page;

  /** 每页大小 */
  @Schema(description = "每页大小")
  private int pageSize;

  /** 搜索耗时（毫秒） */
  @Schema(description = "搜索耗时（毫秒）")
  @Builder.Default
  private long tookMs = 0L;

  /** 聚合结果列表 */
  @Schema(description = "聚合结果")
  @Builder.Default
  private List<SearchAggregation> aggregations = Collections.emptyList();

  /** 搜索建议（"您是不是要找"） */
  @Schema(description = "搜索建议")
  private SearchSuggestion suggestion;

  /** 使用的搜索引擎名称 */
  @Schema(description = "搜索引擎名称")
  private String engine;

  /** 是否为降级结果 */
  @Schema(description = "是否为降级结果")
  @Builder.Default
  private boolean isDegraded = false;

  /** P3-21: 下一页游标（为空表示无更多数据） */
  @Schema(description = "下一页游标")
  private String nextCursor;

  /** P5-13: 各阶段耗时详情（毫秒），用于可观测性分析与性能诊断 */
  @Schema(description = "各阶段耗时详情（毫秒）：textProcess/cacheQuery/engineQuery/ranking")
  private Map<String, Long> timing;

  /** Phase 3-U2: 错误信息（null 表示请求成功；非 null 表示限流 / 参数错 / 引擎故障） */
  @Schema(description = "错误信息；null 表示正常，非 null 表示请求被拒绝或出错")
  private SearchError error;

  /**
   * 创建空响应。
   *
   * @param page 页码
   * @param pageSize 每页条数
   * @return 空搜索结果响应
   */
  public static SearchResponse empty(int page, int pageSize) {
    return SearchResponse.builder()
        .hits(Collections.emptyList())
        .total(0L)
        .page(page)
        .pageSize(pageSize)
        .tookMs(0L)
        .build();
  }

  /**
   * 创建被拒绝的响应（带错误码，前端可据此展示对应语言文案）。
   *
   * @param page 页码
   * @param pageSize 每页条数
   * @param error 错误信息（不可为 {@code null}）
   * @return 携带错误信息的空结果响应
   */
  public static SearchResponse rejected(int page, int pageSize, SearchError error) {
    return SearchResponse.builder()
        .hits(Collections.emptyList())
        .total(0L)
        .page(page)
        .pageSize(pageSize)
        .tookMs(0L)
        .error(error)
        .build();
  }
}
