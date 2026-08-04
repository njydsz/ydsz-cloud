package com.remisoft.common.search.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索聚合/分面结果
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索聚合结果")
public class SearchAggregation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合字段名 */
    @Schema(description = "聚合字段名")
    private String field;

    /** 聚合标签（如"类型"、"标签"） */
    @Schema(description = "聚合标签")
    private String label;

    /** 聚合桶列表 */
    @Schema(description = "聚合桶列表")
    @Builder.Default
    private List<Bucket> buckets = Collections.emptyList();

    /**
     * 聚合桶
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "聚合桶")
    public static class Bucket implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 桶键值 */
        @Schema(description = "桶键值")
        private String key;

        /** 桶文档数 */
        @Schema(description = "文档数")
        private long count;
    }
}
