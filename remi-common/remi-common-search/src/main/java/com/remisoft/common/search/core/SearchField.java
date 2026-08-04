package com.remisoft.common.search.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索字段配置（引擎无关）
 *
 * <p>定义不同字段在全文检索中的权重、高亮、聚合等属性。
 * 各 {@code SearchProvider} 通过 {@link #builder()} 声明其可搜索字段。
 * 各引擎实现将此定义映射为各自的 schema。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchField {

    /** 字段名（对应 IndexDocument 中的 key） */
    private String name;

    /** 显示标签（用于 UI 展示和聚合 facet 名称） */
    private String label;

    /** 字段类型 */
    @Builder.Default
    private FieldType type = FieldType.TEXT;

    /** 权重（影响相关性排序，默认 1.0） */
    @Builder.Default
    private float weight = 1.0f;

    /** 是否参与搜索 */
    @Builder.Default
    private boolean searchable = true;

    /** 是否支持高亮 */
    @Builder.Default
    private boolean highlightable = false;

    /** 是否支持聚合（facet） */
    @Builder.Default
    private boolean aggregatable = false;

    /** 是否支持排序 */
    @Builder.Default
    private boolean sortable = false;

    /** 分析器（引擎可忽略，如 PG 的 search_zh / ES 的 ik_smart） */
    private String analyzer;

    /**
     * 字段类型枚举（引擎通用）
     */
    public enum FieldType {
        /** 全文本（参与分词搜索） */
        TEXT,
        /** 关键词（精确匹配，不分词） */
        KEYWORD,
        /** 数值型 */
        NUMERIC,
        /** 日期型 */
        DATE,
        /** 布尔型 */
        BOOLEAN,
        /** 标签型（多值） */
        TAG
    }
}
