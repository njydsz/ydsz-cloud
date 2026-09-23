package com.njydsz.workflow.server.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量操作结果。
 *
 * <p>统一封装所有批量操作的执行结果（批量通过、批量驳回、批量转办、批量催办等）。
 * 结构固定为 totalCount / successCount / failCount + 失败详情列表 + 耗时。
 *
 * <p><b>响应标准化约定（P0-7）：所有批量操作 API 返回此结构或其子类的 data 字段，
 *
 * <pre>{@code
 * {
 *   "code": 200,
 *   "data": {
 *     "totalCount": 10,
 *     "successCount": 8,
 *     "failCount": 2,
 *     "failures": [{"targetId": "t1", "reason": "已审批"}],
 *     "durationMs": 340
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 总操作数量 */
  private int totalCount;

  /** 成功数量 */
  private int successCount;

  /** 失败数量 */
  private int failCount;

  /** 失败详情列表 */
  private List<FailureDetail> failures;

  /** 操作耗时（毫秒） */
  private long durationMs;

  /** 是否全部成功（计算属性：failCount == 0） */
  public boolean isAllSuccess() {
    return failCount == 0;
  }

  // ============================== 工厂方法 ==============================

  /**
   * 创建全部成功结果（单参数简写，total = successCount = failCount = 0）。
   *
   * <p>兼容旧代码：{@code success(0, 0)} 表示空成功。
   *
   * @param totalCount 总数（= 成功数）
   * @param successCount 成功数
   * @return 批量操作结果
   */
  public static BatchOperationResult success(int totalCount, int successCount) {
    BatchOperationResult result = new BatchOperationResult();
    result.setTotalCount(totalCount);
    result.setSuccessCount(successCount);
    result.setFailCount(totalCount - successCount);
    result.setFailures(Collections.emptyList());
    result.setDurationMs(0L);
    return result;
  }

  /**
   * 创建全部成功结果（含耗时）。
   *
   * @param totalCount 总数
   * @param durationMs 耗时毫秒
   * @return 批量操作结果
   */
  public static BatchOperationResult allSuccess(int totalCount, long durationMs) {
    BatchOperationResult result = new BatchOperationResult();
    result.setTotalCount(totalCount);
    result.setSuccessCount(totalCount);
    result.setFailCount(0);
    result.setFailures(Collections.emptyList());
    result.setDurationMs(durationMs);
    return result;
  }

  /**
   * 创建部分失败结果。
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @param failures 失败详情
   * @param durationMs 耗时毫秒
   * @return 批量操作结果
   */
  public static BatchOperationResult partial(int totalCount, int successCount,
      List<FailureDetail> failures, long durationMs) {
    BatchOperationResult result = new BatchOperationResult();
    result.setTotalCount(totalCount);
    result.setSuccessCount(successCount);
    result.setFailCount(totalCount - successCount);
    result.setFailures(failures);
    result.setDurationMs(durationMs);
    return result;
  }

  /**
   * 便捷方法：构建一个失败详情列表（ Builder 风格用法）。
   *
   * @return 失败详情列表（可变，可 add 后传入 partial 工厂方法）
   */
  public static List<FailureDetail> newFailureList() {
    return new ArrayList<>(8);
  }

  // ============================== 失败详情 ==============================

  /**
   * 批量操作失败详情。
   *
   * <p>targetId 为操作目标标识（任务 ID / 实例 ID / 定义 ID 等），reason 为失败原因。
   */
  @Data
  @NoArgsConstructor
  public static class FailureDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作目标标识（任务 ID / 实例 ID / 定义 ID 等，由调用方赋值） */
    private String targetId;

    /** 失败原因 */
    private String reason;

    /**
     * 构造失败详情。
     *
     * @param targetId 目标标识
     * @param reason 失败原因
     */
    public FailureDetail(String targetId, String reason) {
      this.targetId = targetId;
      this.reason = reason;
    }
  }
}
