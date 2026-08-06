package com.remisoft.common.domain.query;

import java.io.Serializable;
import java.util.List;

import com.remisoft.common.json.annotation.JsonClass;
import com.remisoft.common.json.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页结果包装器（扩展传统 {@link PageResult}，添加游标元信息）。
 *
 * <p>适用于无限滚动、信息流等场景，避免 offset 分页在大数据量下的性能劣化。
 * 与 {@link PageResult} 的关系：CursorPage = PageResult + hasNext + nextCursor。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Service 层组装
 * List<Feed> feeds = feedMapper.selectAfterCursor(query.getCursor(), query.getPageSize());
 * String nextCursor = feeds.isEmpty() ? null : encodeCursor(feeds.get(feeds.size() - 1));
 * CursorPage<Feed> page = CursorPage.of(feeds, nextCursor, feeds.size() >= query.getPageSize());
 *
 * // Controller 层返回
 * return CursorPage.of(feeds, nextCursor, hasMore);
 * }</pre>
 *
 * @param <T> 数据类型
 * @author remi-team
 * @since 1.5.0
 * @see PageResult
 * @see PageQuery#isCursorBased()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClass
public class CursorPage<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据列表。
     */
    private List<T> records;

    /**
     * 是否有更多数据。
     *
     * <p>由 Service 层根据实际查询结果判断：
     * <ul>
     *   <li>true：records.size() >= pageSize（可能还有更多）</li>
     *   <li>false：records.size() < pageSize（已是最后一页）</li>
     * </ul>
     */
    private boolean hasNext;

    /**
     * 下一页游标值（Base64 / 加密后的值，客户端无需理解其含义）。
     *
     * <p>为 null 表示已到最后一页。
     */
    private String nextCursor;

    /**
     * 上一页游标值（可选，支持双向滚动）。
     */
    private String prevCursor;

    /**
     * 每页记录数。
     */
    private int pageSize;

    /**
     * 创建游标分页结果（单向无限滚动）。
     *
     * @param records    当前页数据列表
     * @param nextCursor 下一页游标（可为 null，表示末页）
     * @param hasNext    是否有更多数据
     * @param <T>        数据类型
     * @return 游标分页结果
     */
    public static <T> CursorPage<T> of(List<T> records, String nextCursor, boolean hasNext) {
        return CursorPage.<T>builder()
                .records(records)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    /**
     * 创建空结果。
     *
     * @param <T> 数据类型
     * @return 空的游标分页结果
     */
    public static <T> CursorPage<T> empty() {
        return CursorPage.<T>builder()
                .records(List.of())
                .hasNext(false)
                .build();
    }

    /**
     * 判断当前页是否为空。
     *
     * @return 为空返回 true
     */
    @JsonIgnore
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}
