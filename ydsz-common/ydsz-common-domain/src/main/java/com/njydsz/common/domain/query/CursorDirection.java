package com.njydsz.common.domain.query;

/**
 * 游标方向枚举。
 *
 * <p>用于指定游标分页的查询方向：
 * <ul>
 *   <li>{@link #NEXT} — 从游标向后查询（更晚/更新的数据）</li>
 *   <li>{@link #PREV} — 从游标向前查询（更早/更旧的数据）</li>
 * </ul>
 *
 * <p><b>v1.8.0 变更：</b>从 {@link PageQuery} 的内部枚举提取为顶层枚举，
 * 支持跨类复用与独立演进。
 *
 * <p><b>状态：</b>实验性 API（v1.8.0 引入），与 {@link SliceQuery} 配合使用。
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see SliceQuery
 * @see PageQuery
 */
public enum CursorDirection {
    /** 游标之后（更晚/更新的数据） */
    NEXT,
    /** 游标之前（更早/更旧的数据） */
    PREV
}
