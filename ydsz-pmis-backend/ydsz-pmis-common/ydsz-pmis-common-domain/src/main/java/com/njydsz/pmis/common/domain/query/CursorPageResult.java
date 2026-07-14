package com.njydsz.pmis.common.domain.query;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页结果（兼容旧 com.njydsz.pmis.common.entity.CursorPageResult）�?
 *
 * <p>用于基于游标的分页查询场景，替代传统 offset 分页�?
 * 在大数据量场景下性能更优�?
 *
 * @param <T> 结果数据类型
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class CursorPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列�?*/
    private List<T> records = Collections.emptyList();

    /** 下一页游标（�?null 表示无更多数据） */
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

    /**
     * 基于记录列表和游标编码器创建游标分页结果�?
     *
     * <p>取前 {@code pageSize} 条作为当前页数据�?
     * 如果列表长度大于 {@code pageSize}，则表示还有更多数据�?
     * 下一页游标由最后一条记录编码生成�?
     *
     * @param records       查询结果列表（已多查 1 条用于判�?hasMore�?
     * @param cursorEncoder 游标编码函数
     * @param pageSize      每页大小
     * @param <T>           数据类型
     * @return 游标分页结果
     */
    public static <T> CursorPageResult<T> of(List<T> records, Function<T, String> cursorEncoder, long pageSize) {
        if (records == null || records.isEmpty()) {
            return empty();
        }
        boolean hasMore = records.size() > pageSize;
        List<T> pageRecords = hasMore ? records.subList(0, (int) pageSize) : records;
        String nextCursor = hasMore ? cursorEncoder.apply(pageRecords.get(pageRecords.size() - 1)) : null;
        return new CursorPageResult<>(pageRecords, nextCursor, hasMore);
    }
}
