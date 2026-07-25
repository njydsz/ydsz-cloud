package com.njydsz.common.search.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索字段配置
 *
 * <p>定义不同字段在全文检索中的权重、高亮、聚合等属性。
 * 各 {@code SearchProvider} 通过 {@link #builder()} 声明其可搜索字段。
 *
 * @author ydsz-team
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

    /** 权重（影响相关性排序，默认 1.0） */
    @Builder.Default
    private float weight = 1.0f;

    /** 是否支持高亮 */
    @Builder.Default
    private boolean highlightable = false;

    /** 是否支持聚合（facet） */
    @Builder.Default
    private boolean aggregatable = false;
}
