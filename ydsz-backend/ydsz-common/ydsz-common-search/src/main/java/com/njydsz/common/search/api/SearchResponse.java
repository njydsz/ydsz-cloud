package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 统一搜索响应
 *
 * @author ydsz-team
 * @since 1.4.0
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
    private boolean degraded = false;

    /** P3-21: 下一页游标（为空表示无更多数据） */
    @Schema(description = "下一页游标")
    private String nextCursor;

    /**
     * 创建空响应
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
}
