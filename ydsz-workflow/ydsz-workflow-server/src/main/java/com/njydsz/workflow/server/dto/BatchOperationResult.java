package com.njydsz.workflow.server.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量操作结果
 *
 * <p>封装批量审批操作的执行结果，包含成功/失败计数和失败详情。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 总操作数量 */
  private int totalCount;

  /** 成功数量 */
  private int successCount;

  /** 失败数量 */
  private int failCount;

  /** 失败详情列表 */
  private List<FailureDetail> failures;

  /**
   * 创建成功结果
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @return 批量操作结果
   */
  public static BatchOperationResult success(int totalCount, int successCount) {
    BatchOperationResult result = new BatchOperationResult();
    result.setTotalCount(totalCount);
    result.setSuccessCount(successCount);
    result.setFailCount(totalCount - successCount);
    return result;
  }

  /**
   * 创建失败结果
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @param failures 失败详情
   * @return 批量操作结果
   */
  public static BatchOperationResult withFailures(
      int totalCount, int successCount, List<FailureDetail> failures) {
    BatchOperationResult result = new BatchOperationResult();
    result.setTotalCount(totalCount);
    result.setSuccessCount(successCount);
    result.setFailCount(totalCount - successCount);
    result.setFailures(failures);
    return result;
  }

  /**
   * 失败详情
   */
  @Data
  @NoArgsConstructor
  public static class FailureDetail implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 失败原因 */
    private String reason;

    
    /**
     * 构造批量失败明细。
     *
     * @param taskId 失败的任务 ID
     * @param reason 失败原因
     */
    public FailureDetail(String taskId, String reason) {
      this.taskId = taskId;
      this.reason = reason;
    }
  }
}
