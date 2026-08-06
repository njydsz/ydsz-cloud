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
 * 轻量分页结果（无 total count）。
 *
 * <p>适用于不需要总记录数的分页场景（如无限滚动、流式加载）。
 *
 * @param <T> 数据类型
 * @author remi-team
 * @since 1.6.0
 * <p><b>v1.7.0 变更：</b>已合并至统一 {@link Slice} 类，此类仅作过渡兼容，将在 2.0.0 移除。
 *
 * @deprecated 1.7.0 使用 {@link Slice} 替代
 * @see Slice 统一分页结果类
 * @see PageResult 带 total 的分页结果
 * @see CursorPage 游标分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonClass
@Deprecated(since = "1.7.0", forRemoval = true)
public class PageSlice<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据列表。
     */
    private List<T> records;

    /**
     * 当前页码（从 1 开始）。
     */
    private int pageNum;

    /**
     * 每页记录数。
     */
    private int pageSize;

    /**
     * 是否有下一页。
     *
     * <p>由 Service 层根据实际查询结果判断：
     * <ul>
     *   <li>true：records.size() >= pageSize（可能还有更多）</li>
     *   <li>false：records.size() < pageSize（已是最后一页）</li>
     * </ul>
     */
    private boolean hasNext;

    /**
     * 创建分页结果。
     *
     * @param records  当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param hasNext  是否有下一页
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageSlice<T> of(List<T> records, int pageNum, int pageSize, boolean hasNext) {
        List<T> safeRecords = records != null ? records : Collections.emptyList();
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 0);
        return new PageSlice<>(safeRecords, safePageNum, safePageSize, hasNext);
    }

    /**
     * 创建空的分页结果。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param <T>      数据类型
     * @return 空的分页结果
     */
    public static <T> PageSlice<T> empty(int pageNum, int pageSize) {
        return of(Collections.emptyList(), pageNum, pageSize, false);
    }

    /**
     * 将当前分页结果的数据列表进行类型转换。
     *
     * <p>适用。DO 。VO 转换场景，避免手动重新构造分页对象。
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的分页结果
     */
    public <R> PageSlice<R> convert(Function<T, R> converter) {
        List<R> convertedRecords = records != null
                ? records.stream().map(converter).collect(Collectors.toList())
                : Collections.emptyList();
        return of(convertedRecords, pageNum, pageSize, hasNext);
    }

    /**
     * 判断当前页数据是否为。
     *
     * @return 为空返回 true
     */
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }

    /**
     * 判断是否有上一页。
     *
     * @return 非首页返回 true
     */
    public boolean hasPrevious() {
        return pageNum > 1;
    }

    /**
     * 获取当前页实际记录数。
     *
     * @return 实际记录数
     */
    public int getRecordCount() {
        return records != null ? records.size() : 0;
    }

    /**
     * 计算起始行号（从 1 开始）。
     *
     * @return 起始行号，空页返回 0
     */
    public int getStartRow() {
        return records != null && !records.isEmpty() ? (pageNum - 1) * pageSize + 1 : 0;
    }

    /**
     * 计算结束行号。
     *
     * @return 结束行号，空页返回 0
     */
    public int getEndRow() {
        return records != null && !records.isEmpty() ? getStartRow() + records.size() - 1 : 0;
    }
}
