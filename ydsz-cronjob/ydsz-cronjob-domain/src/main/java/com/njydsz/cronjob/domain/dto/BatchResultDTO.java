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
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResultDTO<T> {

  /** 总处理条数 */
  private int total;

  /** 成功条数 */
  private int successCount;

  /** 失败条数 */
  private int failureCount;

  /** 明细列表 */
  private List<ItemResult<T>> details;

  // 显式 getter/setter（避免 Lombok @Data 差异）
  public int getTotal() {
    return total;
  }
  public void setTotal(int total) {
    this.total = total;
  }
  public int getSuccessCount() {
    return successCount;
  }
  public void setSuccessCount(int successCount) {
    this.successCount = successCount;
  }
  public int getFailureCount() {
    return failureCount;
  }
  public void setFailureCount(int failureCount) {
    this.failureCount = failureCount;
  }
  public List<ItemResult<T>> getDetails() {
    return details;
  }
  public void setDetails(List<ItemResult<T>> details) {
    this.details = details;
  }

  /**
   * 单个批量操作结果。
   *
   * @param <T> 操作项类型
   */
  public static class ItemResult<T> {
    /** 操作项标识 */
    private T item;

    /** 是否成功 */
    private boolean success;

    /** 失败原因（成功时为 null） */
    private String error;

    // 显式 getter/setter
    public T getItem() {
    return item;
  }
    public void setItem(T item) {
    this.item = item;
  }
    public boolean isSuccess() {
    return success;
  }
    public void setSuccess(boolean success) {
    this.success = success;
  }
    public String getError() {
    return error;
  }
    public void setError(String error) {
    this.error = error;
  }

    public static <T> ItemResult<T> success(T item) {
      ItemResult<T> r = new ItemResult<>();
      r.setItem(item);
      r.setSuccess(true);
      r.setError(null);
      return r;
    }

    public static <T> ItemResult<T> failure(T item, String error) {
      ItemResult<T> r = new ItemResult<>();
      r.setItem(item);
      r.setSuccess(false);
      r.setError(error);
      return r;
    }
  }

  /**
   * 构造成功的批量结果（全部成功）。
   *
   * @param <T> 批量操作项类型
   * @param total 总处理条数
   * @return 全部成功的批量结果
   */
  public static <T> BatchResultDTO<T> allSuccess(int total) {
    BatchResultDTO<T> r = new BatchResultDTO<>();
    r.setTotal(total);
    r.setSuccessCount(total);
    r.setFailureCount(0);
    r.setDetails(Collections.emptyList());
    return r;
  }

  /**
   * 构造批量结果（含成功数，无明细）。
   *
   * @param <T> 批量操作项类型
   * @param total 总处理条数
   * @param successCount 成功条数
   * @return 批量结果（失败数 = total - successCount）
   */
  public static <T> BatchResultDTO<T> of(int total, int successCount) {
    BatchResultDTO<T> r = new BatchResultDTO<>();
    r.setTotal(total);
    r.setSuccessCount(successCount);
    r.setFailureCount(total - successCount);
    r.setDetails(Collections.emptyList());
    return r;
  }
}
