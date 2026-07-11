package com.njydsz.pmis.common.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 游标分页结果（P2-8 深翻优化）
 *
 * <p>与传统 {@code Page<T>} 不同，游标分页不返回 total/pages（COUNT 在大表上很慢），
 * 而是通过 {@link #nextCursor} 和 {@link #hasMore} 指示是否还有更多数据。
 *
 * @param <T> 记录类型
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class CursorPageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> list;

    /** 下一页游标（null 表示已到最后一页） */
    private String nextCursor;

    /** 是否还有更多数据 */
    private boolean hasMore;

    /** 当前页大小 */
    private long size;

    public CursorPageResult() {
    }

    public CursorPageResult(List<T> list, String nextCursor, boolean hasMore, long size) {
        this.list = list;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.size = size;
    }

    /**
     * 构建游标分页结果
     *
     * <p>传入多查出的 1 条记录（size + 1），自动截断到 size 条并计算 nextCursor。
     *
     * @param records 查询结果（应查 size + 1 条用于判断 hasMore）
     * @param cursorEncoder 游标编码器（从最后一条记录生成 cursor）
     * @param requestedSize 请求的页大小
     * @param <T> 记录类型
     * @return 游标分页结果
     */
    public static <T> CursorPageResult<T> of(List<T> records,
                                              java.util.function.Function<T, String> cursorEncoder,
                                              long requestedSize) {
        boolean hasMore = records.size() > requestedSize;
        List<T> page = hasMore
                ? records.subList(0, (int) requestedSize)
                : records;
        String nextCursor = hasMore
                ? cursorEncoder.apply(page.get(page.size() - 1))
                : null;
        return new CursorPageResult<>(page, nextCursor, hasMore, requestedSize);
    }
}
