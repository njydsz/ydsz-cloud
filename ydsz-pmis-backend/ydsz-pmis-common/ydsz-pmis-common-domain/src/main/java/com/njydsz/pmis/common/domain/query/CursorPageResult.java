package com.njydsz.pmis.common.domain.query;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 游标分页结果（兼容旧 com.njydsz.pmis.common.entity.CursorPageResult）。
 *
 * <p>用于基于游标的分页查询场景，替代传统 offset 分页，
 * 在大数据量场景下性能更优。
 *
 * @param <T> 结果数据类型
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class CursorPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> records = Collections.emptyList();

    /** 下一页游标（为 null 表示无更多数据） */
    private String nextCursor;

    /** 是否还有更多数据 */
    private boolean hasMore;

    /** 本次查询返回的记录数 */
    private int count;

    public CursorPageResult(List<T> records, String nextCursor, boolean hasMore) {
        this.records = records != null ? records : Collections.emptyList();
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.count = this.records.size();
    }

    public static <T> CursorPageResult<T> empty() {
        return new CursorPageResult<>(Collections.emptyList(), null, false);
    }
}
