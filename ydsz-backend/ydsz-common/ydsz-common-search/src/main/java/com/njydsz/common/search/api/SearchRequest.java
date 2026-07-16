package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 统一搜索请求
 * <p>
 * 封装所有搜索引擎通用的搜索参数，包括关键词、分页、排序、过滤、高亮等。
 * 各业务模块通过 {@code SearchProvider} 补充特定于实体的搜索逻辑。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Data
@Builder
@Schema(description = "统一搜索请求")
public class SearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 搜索关键词 */
    @Schema(description = "搜索关键词")
    private String keyword;

    /** 搜索范围（实体类型列表），为空表示搜索全部 */
    @Schema(description = "搜索范围（实体类型列表）")
    @Builder.Default
    private List<String> types = new ArrayList<>();

    /** 页码（从 1 开始） */
    @Schema(description = "页码，从1开始")
    @Builder.Default
    private int page = 1;

    /** 每页大小 */
    @Schema(description = "每页大小")
    @Builder.Default
    private int pageSize = 20;

    /** 排序字段 */
    @Schema(description = "排序字段")
    private String sortBy;

    /** 是否升序 */
    @Schema(description = "是否升序")
    @Builder.Default
    private boolean ascending = false;

    /** 是否启用高亮 */
    @Schema(description = "是否启用高亮")
    @Builder.Default
    private boolean highlight = true;

    /** 高亮前置标签 */
    @Schema(description = "高亮前置标签")
    @Builder.Default
    private String highlightPreTag = "<em>";

    /** 高亮后置标签 */
    @Schema(description = "高亮后置标签")
    @Builder.Default
    private String highlightPostTag = "</em>";

    /** 高亮片段最大长度 */
    @Schema(description = "高亮片段最大长度")
    @Builder.Default
    private int highlightFragmentSize = 120;

    /** 是否启用模糊匹配 */
    @Schema(description = "是否启用模糊匹配")
    @Builder.Default
    private boolean fuzzy = true;

    /** 模糊匹配最小相似度（0~1） */
    @Schema(description = "模糊匹配最小相似度")
    @Builder.Default
    private double fuzzyMinSimilarity = 0.3;

    /** 过滤条件（字段名 → 值列表） */
    @Schema(description = "过滤条件")
    @Builder.Default
    private List<SearchFilter> filters = new ArrayList<>();

    /** 聚合字段列表（用于分面统计） */
    @Schema(description = "聚合字段列表")
    @Builder.Default
    private List<String> aggregations = new ArrayList<>();

    /** 租户 ID（权限隔离） */
    @Schema(description = "租户ID")
    private String tenantId;

    /** 操作人 ID（权限过滤） */
    @Schema(description = "操作人ID")
    private String userId;

    /** 是否仅搜索标题字段（不含内容） */
    @Schema(description = "是否仅搜索标题")
    @Builder.Default
    private boolean titleOnly = false;

    /** P3-21: 游标分页 cursor（base64 编码，为空表示从头开始） */
    @Schema(description = "游标分页cursor")
    private String cursor;

    /**
     * 快速构建搜索请求
     */
    public static SearchRequest of(String keyword) {
        return SearchRequest.builder()
                .keyword(keyword)
                .build();
    }

    /**
     * 快速构建分页搜索请求
     */
    public static SearchRequest of(String keyword, int page, int pageSize) {
        return SearchRequest.builder()
                .keyword(keyword)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    /**
     * 计算偏移量
     */
    public int getOffset() {
        return (Math.max(page, 1) - 1) * Math.max(pageSize, 1);
    }
}
