package com.njydsz.cronjob.domain.dto;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量操作结果（P2-F4）。
 *
 * <p>提供比简单 count 更详细的批量操作结果，包含每个条目的处理状态与失败原因， 前端可据此展示"部分成功"详情弹窗，而非仅显示"成功 N 条"。
 *
 * <h3>典型返回结构</h3>
 *
 * <pre>{@code
 * {
 *   "total": 5,
 *   "successCount": 3,
 *   "failureCount": 2,
 *   "details": [
 *     {"item": "job-001", "success": true,  "error": null},
 *     {"item": "job-002", "success": false, "error": "任务不存在"},
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * @param <T> 批量操作项的类型（通常为 String，表示 jobId）
 * @author ydsz-team
 * @since 1.5.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResult<T> {

  /** 总处理条数 */
  private int total;

  /** 成功条数 */
  private int successCount;

  /** 失败条数 */
  private int failureCount;

  /** 明细列表 */
  private List<ItemResult<T>> details;

  /** 单个批量操作结果。 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ItemResult<T> {
    /** 操作项标识 */
    private T item;

    /** 是否成功 */
    private boolean success;

    /** 失败原因（成功时为 null） */
    private String error;

    public static <T> ItemResult<T> success(T item) {
      return new ItemResult<>(item, true, null);
    }

    public static <T> ItemResult<T> failure(T item, String error) {
      return new ItemResult<>(item, false, error);
    }
  }

  /** 构造成功的批量结果（全部成功）。 */
  public static <T> BatchResult<T> allSuccess(int total) {
    return new BatchResult<>(total, total, 0, Collections.emptyList());
  }

  /** 构造批量结果（含成功数，无明细）。 */
  public static <T> BatchResult<T> of(int total, int successCount) {
    return new BatchResult<>(total, successCount, total - successCount, Collections.emptyList());
  }
}
