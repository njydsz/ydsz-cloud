package com.njydsz.common.domain.query;

import java.io.Serializable;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询请求参数（输入）。
 *
 * <p>仅承载<b>查询输入参数</b>，不混杂结果状态。
 * 统一封装 offset 分页和 cursor 分页的请求语义。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Offset 分页请求
 * SliceQuery offset = SliceQuery.of(1, 20);
 *
 * // Cursor 分页请求
 * SliceQuery cursor = SliceQuery.ofNext("eyJpZCI6MTAwfQ==", 20);
 * }</pre>
 *
 * <p><b>设计参考：</b>Spring Data {@code Pageable} — 纯输入参数对象。
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see SliceResult 分页查询结果（输出）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClass(description = "分页查询请求参数（纯输入，不混杂结果状态）")
public class SliceQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（从1开始，Offset 分页时有效）。     */
    private int pageNum;

    /**
     * 每页记录数。     */
    private int pageSize;

    /**
     * 游标值（Cursor 分页时有效，null 表示从第一条开始）。
     */
    private String cursor;

    /**
     * 游标方向（默认 NEXT）。
     */
    @Builder.Default
    private CursorDirection cursorDirection = CursorDirection.NEXT;

    /**
     * 创建 Offset 分页请求。     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 分页查询请求
     */
    public static SliceQuery of(int pageNum, int pageSize) {
        return SliceQuery.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .cursorDirection(CursorDirection.NEXT)
                .build();
    }

    /**
     * 创建 Cursor 下一页请求。
     *
     * @param nextCursor 下一页游标（null 表示从第一条开始）
     * @param pageSize   每页记录数
     * @return 分页查询请求
     */
    public static SliceQuery ofNext(String nextCursor, int pageSize) {
        return SliceQuery.builder()
                .cursor(nextCursor)
                .pageSize(pageSize)
                .cursorDirection(CursorDirection.NEXT)
                .build();
    }

    /**
     * 创建 Cursor 上一页请求。
     *
     * @param prevCursor 上一页游标
     * @param pageSize   每页记录数
     * @return 分页查询请求
     */
    public static SliceQuery ofPrev(String prevCursor, int pageSize) {
        return SliceQuery.builder()
                .cursor(prevCursor)
                .pageSize(pageSize)
                .cursorDirection(CursorDirection.PREV)
                .build();
    }

    /**
     * 判断是否为 Cursor 分页模式。
     */
    @JsonIgnore
    public boolean isCursorBased() {
        return cursor != null && !cursor.isBlank();
    }

    /**
     * 判断是否为 Offset 分页模式。
     */
    @JsonIgnore
    public boolean isOffsetBased() {
        return !isCursorBased();
    }
}
