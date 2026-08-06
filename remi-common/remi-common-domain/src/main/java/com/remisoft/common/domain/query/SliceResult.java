package com.remisoft.common.domain.query;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.remisoft.common.json.annotation.JsonClass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果（输出）。
 *
 * <p>对标 Spring Data {@code Slice<T>} 的纯结果语义，
 * 仅包含<b>查询结果数据</b>和<b>翻页导航状态</b>，不承载输入参数。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Offset 分页结果
 * SliceResult<User> offset = SliceResult.of(users, pageNum, pageSize, hasNext);
 *
 * // Cursor 分页结果
 * SliceResult<User> cursor = SliceResult.of(users, nextCursor, hasNext);
 *
 * // 类型转换
 * SliceResult<UserVO> vo = result.convert(UserVO::new);
 * }</pre>
 *
 * <p><b>设计参考：</b>Spring Data {@code Slice<T>} — 纯结果对象，hasNext/hasContent。
 *
 * @param <T> 数据类型
 * @author remi-team
 * @since 1.8.0
 * @see SliceQuery 分页查询请求参数（输入）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClass(description = "分页查询结果（纯输出，不承载输入参数）")
public class SliceResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据列表。
     */
    private List<T> records;

    /**
     * 是否有下一页。
     */
    private boolean hasNext;

    /**
     * 是否有上一页。
     */
    private boolean hasPrevious;

    /**
     * 下一页游标值（Cursor 分页时有效，null 表示末页）。
     */
    private String nextCursor;

    /**
     * 上一页游标值（可选，支持双向滚动）。
     */
    private String prevCursor;

    /**
     * 创建 Offset 分页结果（由 Service 层计算 hasNext）。
     *
     * @param records  当前页数据列表
     * @param pageNum  当前页码（用于 UI 展示）
     * @param pageSize 每页记录数（用于 UI 展示）
     * @param hasNext  是否有下一页
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> SliceResult<T> of(List<T> records, int pageNum, int pageSize, boolean hasNext) {
        return SliceResult.<T>builder()
                .records(records != null ? records : Collections.emptyList())
                .hasNext(hasNext)
                .hasPrevious(pageNum > 1)
                .build();
    }

    /**
     * 创建 Offset 分页结果（含 total 总数）。     *
     * @param records  当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param total    总记录数
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> SliceResult<T> of(List<T> records, int pageNum, int pageSize, long total) {
        boolean hasNext = (long) ((pageNum - 1) * pageSize + (records != null ? records.size() : 0)) < total;
        return of(records, pageNum, pageSize, hasNext);
    }

    /**
     * 创建 Cursor 分页结果（单向无限滚动）。
     *
     * @param records    当前页数据列表
     * @param nextCursor 下一页游标
     * @param hasNext    是否有更多数据
     * @param <T>        数据类型
     * @return Cursor 分页结果
     */
    public static <T> SliceResult<T> of(List<T> records, String nextCursor, boolean hasNext) {
        return SliceResult.<T>builder()
                .records(records != null ? records : Collections.emptyList())
                .hasNext(hasNext)
                .hasPrevious(false)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * 创建 Cursor 分页结果（双向滚动）。
     *
     * @param records     当前页数据列表
     * @param nextCursor  下一页游标
     * @param prevCursor  上一页游标
     * @param hasNext     是否有更多数据
     * @param hasPrevious 是否有上一页
     * @param <T>         数据类型
     * @return Cursor 分页结果
     */
    public static <T> SliceResult<T> of(List<T> records, String nextCursor, String prevCursor,
                                         boolean hasNext, boolean hasPrevious) {
        return SliceResult.<T>builder()
                .records(records != null ? records : Collections.emptyList())
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .build();
    }

    /**
     * 创建空结果（通用）。
     *
     * @param <T> 数据类型
     * @return 空结果
     */
    public static <T> SliceResult<T> empty() {
        return SliceResult.<T>builder()
                .records(Collections.emptyList())
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    // ======================== 类型转换 ========================

    /**
     * 将当前结果的数据列表进行类型转换。
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的结果
     */
    public <R> SliceResult<R> convert(Function<T, R> converter) {
        List<R> converted = records != null
                ? records.stream().map(converter).collect(Collectors.toList())
                : Collections.emptyList();
        return SliceResult.<R>builder()
                .records(converted)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .build();
    }

    // ======================== 便捷方法 ========================

    /**
     * 判断当前页数据是否为空。
     */
    public boolean hasContent() {
        return records != null && !records.isEmpty();
    }

    /**
     * 获取当前页实际记录数。
     */
    public int getNumberOfElements() {
        return records != null ? records.size() : 0;
    }

    /**
     * 判断是否为 Cursor 分页模式。
     */
    public boolean isCursorBased() {
        return nextCursor != null || prevCursor != null;
    }

    /**
     * 判断是否为 Offset 分页模式。
     */
    public boolean isOffsetBased() {
        return !isCursorBased();
    }

    /**
     * 判断是否为第一页（无前置记录）。
     */
    public boolean isFirst() {
        return !hasPrevious;
    }

    /**
     * 判断是否为最后一页（无后续记录）。
     */
    public boolean isLast() {
        return !hasNext;
    }
}
