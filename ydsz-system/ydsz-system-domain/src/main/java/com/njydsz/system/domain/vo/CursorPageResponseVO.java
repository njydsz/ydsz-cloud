package com.njydsz.system.domain.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页响应 VO
 *
 * <p>用于游标分页模式（seek method），返回数据列表和下一页游标。
 *
 * <p>游标分页优势：
 *
 * <ul>
 *   <li>避免深度分页的 offset 扫描开销
 *   <li>适合大数据量连续翻页场景
 *   <li>性能稳定，不受页码增长影响
 * </ul>
 *
 * @param <T> 数据类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageResponseVO<T> {

  /** 数据列表 */
  private List<T> records;

  /** 下一页游标（null 表示已无更多数据） */
  private String nextCursor;

  /** 是否有下一页 */
  private boolean hasMore;

  /** 本次返回数据条数 */
  private int size;

  /**
   * 创建游标分页响应
   *
   * @param records 数据列表
   * @param nextCursor 下一页游标
   * @param <T> 数据类型
   * @return 游标分页响应
   */
  public static <T> CursorPageResponse<T> of(List<T> records, String nextCursor) {
    return CursorPageResponseVO.<T>builder()
        .records(records)
        .nextCursor(nextCursor)
        .hasMore(nextCursor != null)
        .size(records != null ? records.size() : 0)
        .build();
  }

  /**
   * 创建空响应
   *
   * @param <T> 数据类型
   * @return 空游标分页响应
   */
  public static <T> CursorPageResponse<T> empty() {
    return CursorPageResponseVO.<T>builder()
        .records(new ArrayList<>())
        .nextCursor(null)
        .hasMore(false)
        .size(0)
        .build();
  }
}
