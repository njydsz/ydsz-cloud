package com.njydsz.common.domain.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页结果（兼容旧 com.njydsz.common.entity.CursorPageResult）。
 *
 * <p>用于基于游标的分页查询场景，替代传统 offset 分页。
 * 在大数据量场景下性能更优。
 *
 * @param <T> 结果数据类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class CursorPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列表*/
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

    /**
     * 基于记录列表和游标编码器创建游标分页结果。
     *
     * <p>取前 {@code pageSize} 条作为当前页数据。
     * 如果列表长度大于 {@code pageSize}，则表示还有更多数据。
     * 下一页游标由最后一条记录编码生成。
     *
     * @param records       查询结果列表（已多查 1 条用于判断 hasMore。
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
        List<T> pageRecords = hasMore ? new ArrayList<>(records.subList(0, (int) pageSize)) : records;
        String nextCursor = hasMore ? cursorEncoder.apply(pageRecords.get(pageRecords.size() - 1)) : null;
        return new CursorPageResult<>(pageRecords, nextCursor, hasMore);
    }

    /**
     * 将当前游标分页结果的数据列表进行类型转换
     *
     * <p>适用于 DO -> VO 转换场景，避免手动重新构造游标分页对象。
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的游标分页结果
     */
    public <R> CursorPageResult<R> convert(Function<T, R> converter) {
        List<R> convertedRecords = records != null
                ? records.stream().map(converter).collect(Collectors.toList())
                : Collections.emptyList();
        return new CursorPageResult<>(convertedRecords, nextCursor, hasMore);
    }
}
