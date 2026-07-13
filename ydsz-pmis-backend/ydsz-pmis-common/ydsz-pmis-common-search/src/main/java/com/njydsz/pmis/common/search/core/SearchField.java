package com.njydsz.pmis.common.search.core;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可搜索字段定义
 * <p>
 * 描述一个参与搜索的字段及其属性（权重、是否高亮、是否分词等）。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchField implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段名 */
    private String name;

    /** 字段标签（中文显示名） */
    private String label;

    /** 搜索权重（越高越优先，默认 1.0） */
    @Builder.Default
    private float weight = 1.0f;

    /** 是否参与高亮 */
    @Builder.Default
    private boolean highlightable = true;

    /** 是否参与搜索 */
    @Builder.Default
    private boolean searchable = true;

    /** 是否参与聚合 */
    @Builder.Default
    private boolean aggregatable = false;
}
