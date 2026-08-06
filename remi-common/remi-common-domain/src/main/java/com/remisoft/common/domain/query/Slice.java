package com.remisoft.common.domain.query;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.remisoft.common.json.annotation.JsonClass;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一分页结果（融合 PageSlice + CursorPage 语义）。
 *
 * <p>对标 Spring Data 的 {@code Slice<T>} 接口和 GitHub Cursor API 设计，
 * 统一承载两种分页场景：
 * <ul>
 *   <li><b>Offset 分页：</b>带 pageNum/pageSize，无 cursor</li>
 *   <li><b>Cursor 分页：</b>带 nextCursor/prevCursor，无 pageNum</li>
 * </ul>
 *
 * <p><b>v1.8.0 变更：</b>因混合了输入参数（pageNum/pageSize）和输出状态（hasNext/nextCursor），
 * 语义混乱，已拆分为：
 * <ul>
 *   <li>{@link SliceQuery} — 分页查询请求参数（输入）</li>
 *   <li>{@link SliceResult} — 分页查询结果（输出）</li>
 * </ul>
 *
 * @param <T> 数据类型
 * @author remi-team
 * @since 1.7.0
 * @deprecated 1.8.0 使用 {@link SliceQuery} + {@link SliceResult} 替代，2.0.0 移除
 * @see SliceQuery
 * @see SliceResult
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonClass
@Deprecated(since = "1.8.0", forRemoval = true)
public class Slice<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据列表。
     */
    private List<T> records;

    /**
     * 当前页码（从1开始，Offset 分页时有效）。
     */
    private int pageNum;

    /**
     * 每页记录数（Offset 分页时有效）。
     */
    private int pageSize;

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

    // ======================== 工厂方法 ========================

    /**
     * 创建 Offset 分页结果。
     *
     * @param records  当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param hasNext  是否有下一页
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> Slice<T> of(List<T> records, int pageNum, int pageSize, boolean hasNext) {
        List<T> safeRecords = records != null ? records : Collections.emptyList();
        return new Slice<>(safeRecords, pageNum, pageSize, hasNext, pageNum > 1, null, null);
    }

    /**
     * 创建 Cursor 分页结果（单向）。
     *
     * @param records    当前页数据列表
     * @param nextCursor 下一页游标（可为 null，表示末页）
     * @param hasNext    是否有更多数据
     * @param <T>        数据类型
     * @return Cursor 分页结果
     */
    public static <T> Slice<T> of(List<T> records, String nextCursor, boolean hasNext) {
        List<T> safeRecords = records != null ? records : Collections.emptyList();
        return new Slice<>(safeRecords, 0, 0, hasNext, false, nextCursor, null);
    }

    /**
     * 创建 Cursor 分页结果（双向）。
     *
     * @param records    当前页数据列表
     * @param nextCursor 下一页游标
     * @param prevCursor 上一页游标
     * @param hasNext    是否有更多数据
     * @param hasPrevious 是否有上一页
     * @param <T>        数据类型
     * @return Cursor 分页结果
     */
    public static <T> Slice<T> of(List<T> records, String nextCursor, String prevCursor,
                                   boolean hasNext, boolean hasPrevious) {
        List<T> safeRecords = records != null ? records : Collections.emptyList();
        return new Slice<>(safeRecords, 0, 0, hasNext, hasPrevious, nextCursor, prevCursor);
    }

    /**
     * 创建空结果（Offset 分页）。
     */
    public static <T> Slice<T> empty(int pageNum, int pageSize) {
        return of(Collections.emptyList(), pageNum, pageSize, false);
    }

    /**
     * 创建空结果（Cursor 分页）。
     */
    public static <T> Slice<T> empty() {
        return of(Collections.emptyList(), null, false);
    }

    // ======================== 类型转换 ========================

    /**
     * 将当前分页结果的数据列表进行类型转换。
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的分页结果
     */
    public <R> Slice<R> convert(Function<T, R> converter) {
        List<R> convertedRecords = records != null
                ? records.stream().map(converter).collect(Collectors.toList())
                : Collections.emptyList<>();
        return new Slice<>(convertedRecords, pageNum, pageSize, hasNext, hasPrevious, nextCursor, prevCursor);
    }

    // ======================== 便捷方法 ========================

    /**
     * 判断当前页数据是否为空。
     */
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }

    /**
     * 获取当前页实际记录数。
     */
    public int getRecordCount() {
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
     * 计算起始行号（从1开始，仅 Offset 分页有效）。
     */
    public int getStartRow() {
        if (isCursorBased() || records == null || records.isEmpty()) {
            return 0;
        }
        return (pageNum - 1) * pageSize + 1;
    }

    /**
     * 计算结束行号（仅 Offset 分页有效）。
     */
    public int getEndRow() {
        if (isCursorBased() || records == null || records.isEmpty()) {
            return 0;
        }
        return getStartRow() + records.size() - 1;
    }

    /**
     * 从 Collection 创建空结果。
     */
    public static <T> Slice<T> emptyCollection() {
        return new Slice<>(Collections.emptyList(), 0, 0, false, false, null, null);
    }

    /**
     * 创建单页结果（无分页信息）。
     *
     * @param records 数据列表
     * @param <T>     数据类型
     * @return 单页结果
     */
    public static <T> Slice<T> of(List<T> records) {
        List<T> safeRecords = records != null ? records : Collections.emptyList<>();
        return new Slice<>(safeRecords, 1, safeRecords.size(), safeRecords.isEmpty(), false, null, null);
    }

    /**
     * 分页切片（Offset 模式）。
     *
     * @param records  当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param total    总记录数
     * @param <T>      数据类型
     * @return Offset 分页结果
     */
    public static <T> Slice<T> of(List<T> records, int pageNum, int pageSize, long total) {
        List<T> safeRecords = records != null ? records : Collections.emptyList<>();
        boolean hasNext = (long) ((pageNum - 1) * pageSize + safeRecords.size()) < total;
        return new Slice<>(safeRecords, pageNum, pageSize, hasNext, pageNum > 1, null, null);
    }
}
