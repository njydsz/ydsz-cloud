package com.njydsz.common.domain.query;

import java.io.Serializable;

import lombok.Getter;

/**
 * 深度分页异常。
 *
 * <p>当 offset 分页的偏移量超过阈值（{@code ydsz.domain.page.cursor-reject-threshold}）时抛出，
 * 强制调用方改用游标分页（SliceQuery / SliceResult 游标模式）。
 *
 * <p>对齐阿里巴巴 Java 开发手册（嵩山版）的深度分页治理建议：超过 10w 条记录的表，禁止 offset > 10000。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Getter
public class DeepPaginationException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前请求的 offset 值 */
    private final long offset;

    /** pageNum 和 pageSize */
    private final int pageNum;

    private final int pageSize;

    /** 拒绝阈值 */
    private final long threshold;

    public DeepPaginationException(long offset, int pageNum, int pageSize, long threshold) {
        super(String.format(
                "Deep pagination rejected: offset=%d exceeds threshold=%d (pageNum=%d, pageSize=%d). " +
                "Please switch to cursor-based pagination (SliceQuery / SliceResult).",
                offset, threshold, pageNum, pageSize));
        this.offset = offset;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.threshold = threshold;
    }
}
