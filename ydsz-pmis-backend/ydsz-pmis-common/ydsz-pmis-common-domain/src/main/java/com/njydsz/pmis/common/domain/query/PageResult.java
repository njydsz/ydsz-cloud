package com.njydsz.pmis.common.domain.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页查询结果封装类
 *
 * <p>用于封装分页查询的返回结果，包括数据列表和分页信息。
 *
 * @param <T> 数据类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据列表
     */
    private transient List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private int pageNum;

    /**
     * 每页记录数
     */
    private int pageSize;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有上一页
     */
    private boolean hasPrevious;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 当前页起始行号（从1开始）
     */
    private int startRow;

    /**
     * 当前页结束行号
     */
    private int endRow;

    /**
     * 创建分页结果
     *
     * @param records  当前页数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param <T>      数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 0);
        int recordCount = records != null ? records.size() : 0;
        int startRow = recordCount > 0 ? (safePageNum - 1) * safePageSize + 1 : 0;
        int endRow = recordCount > 0 ? startRow + recordCount - 1 : 0;
        return new PageResult<>(
                records != null ? records : Collections.emptyList(),
                total,
                safePageNum,
                safePageSize,
                totalPages,
                safePageNum > 1,
                safePageNum < totalPages,
                startRow,
                endRow
        );
    }

    /**
     * 创建空的分页结果
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param <T>      数据类型
     * @return 空的分页结果
     */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(Collections.emptyList(), 0L, pageNum, pageSize);
    }

    /**
     * 将当前分页结果的数据列表进行类型转换
     *
     * <p>适用于 DO → VO 转换场景，避免手动重新构造分页对象。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * PageResult<UserDO> doPage = userService.page(query);
     * PageResult<UserVO> voPage = doPage.convert(user -> new UserVO(user));
     * }</pre>
     *
     * @param converter 转换函数
     * @param <R>       目标类型
     * @return 转换后的分页结果
     */
    public <R> PageResult<R> convert(Function<T, R> converter) {
        List<R> convertedRecords = records != null
                ? records.stream().map(converter).collect(Collectors.toList())
                : Collections.emptyList();
        return of(convertedRecords, total, pageNum, pageSize);
    }

    /**
     * 判断当前页数据是否为空
     *
     * @return 为空返回 true
     */
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}
