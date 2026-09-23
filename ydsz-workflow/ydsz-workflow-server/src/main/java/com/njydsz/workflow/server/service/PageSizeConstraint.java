package com.njydsz.workflow.server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 分页大小约束工具。
 *
 * <p>统一约束所有分页查询的 pageSize 上限，防止大 pageSize 导致 OOM 或慢查询。
 *
 * <p><b>约束规则：</b>
 *
 * <ul>
 *   <li>列表查询默认 pageSize = 20，最大 pageSize = 100</li>
 *   <li>导出查询最大 pageSize = 500（批量导出场景）</li>
 *   <li>pageSize &lt;= 0 时使用默认值 20</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageSizeConstraint {

  /** 默认分页大小 */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /** 列表查询最大分页大小 */
  public static final int MAX_PAGE_SIZE = 100;

  /** 导出查询最大分页大小 */
  public static final int MAX_EXPORT_PAGE_SIZE = 500;

  /**
   * 约束列表查询的 pageSize。
   *
   * @param pageSize 请求的 pageSize（可为 null）
   * @return 安全的 pageSize 值
   */
  public static int constrain(Integer pageSize) {
    return constrain(pageSize, MAX_PAGE_SIZE);
  }

  /**
   * 约束导出查询的 pageSize。
   *
   * @param pageSize 请求的 pageSize（可为 null）
   * @return 安全的 pageSize 值
   */
  public static int constrainForExport(Integer pageSize) {
    return constrain(pageSize, MAX_EXPORT_PAGE_SIZE);
  }

  /**
   * 通用 pageSize 约束方法。
   *
   * @param pageSize 请求的 pageSize（可为 null）
   * @param maxSize 允许的最大值
   * @return 安全的 pageSize 值
   */
  public static int constrain(Integer pageSize, int maxSize) {
    if (pageSize == null || pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, maxSize);
  }

  /**
   * 约束 pageNo（最小为 1）。
   *
   * @param pageNo 请求的 pageNo（可为 null）
   * @return 安全的 pageNo 值
   */
  public static int constrainPageNo(Integer pageNo) {
    return (pageNo == null || pageNo < 1) ? 1 : pageNo;
  }
}
