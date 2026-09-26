package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.agent.domain.asynctask.AsyncTaskType;
import com.njydsz.agent.domain.entity.AsyncTask;

/**
 * 异步任务视图对象
 *
 * <p>用于向前端返回异步任务的当前状态、进度和执行结果。
 * 由 {@link AsyncTask} 实体转换而来，仅暴露前端必要字段。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "异步任务详情")
public class AsyncTaskVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 任务唯一 ID */
  @Schema(description = "任务唯一 ID")
  private Long id;

  /** 任务类型编码 */
  @Schema(description = "任务类型编码")
  private String taskType;

  /** 任务类型描述 */
  @Schema(description = "任务类型描述")
  private String taskTypeDesc;

  /** 当前状态编码 */
  @Schema(description = "当前状态编码（PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED/EXPIRED）")
  private String status;

  /** 当前状态描述 */
  @Schema(description = "当前状态中文描述")
  private String statusDesc;

  /** 租户编码 */
  @Schema(description = "租户编码")
  private String tenantCode;

  /** 触发用户 ID */
  @Schema(description = "触发用户 ID")
  private String userId;

  /** 当前进度百分比（0-100） */
  @Schema(description = "当前进度百分比")
  private Integer progressPercent;

  /** 执行结果（已完成时非空） */
  @Schema(description = "执行结果 JSON")
  private String outputPayload;

  /** 失败原因（失败时非空） */
  @Schema(description = "失败原因")
  private String errorMessage;

  /** 已重试次数 */
  @Schema(description = "已重试次数")
  private Integer retryCount;

  /** 最大重试次数 */
  @Schema(description = "最大重试次数")
  private Integer maxRetry;

  /** 任务过期时间 */
  @Schema(description = "任务过期时间")
  private LocalDateTime expireAt;

  /** 任务开始执行时间 */
  @Schema(description = "任务开始执行时间")
  private LocalDateTime startedAt;

  /** 任务完成时间 */
  @Schema(description = "任务完成时间")
  private LocalDateTime completedAt;

  /** 创建时间 */
  @Schema(description = "创建时间")
  private LocalDateTime createdAt;

  /** 更新时间 */
  @Schema(description = "更新时间")
  private LocalDateTime updatedAt;

  /** 是否处于终态 */
  @Schema(description = "是否已到达终态")
  private boolean isTerminal;

  /** 是否可重试 */
  @Schema(description = "是否可重试")
  private boolean isRetryable;

  /**
   * 从异步任务实体转换为 VO。
   *
   * @param task 异步任务实体
   @return 任务视图对象
   */
  public static AsyncTaskVO fromEntity(AsyncTask task) {
    if (task == null) {
      return null;
    }
    AsyncTaskVO vo = new AsyncTaskVO();
    vo.setId(task.getId());
    vo.setTaskType(task.getTaskType());
    vo.setStatus(task.getStatus());

    // 枚举描述填充
    AsyncTaskType type = AsyncTaskType.fromCode(task.getTaskType());
    vo.setTaskTypeDesc(type != null ? type.getDescription() : task.getTaskType());
    AsyncTaskStatus status = AsyncTaskStatus.fromCode(task.getStatus());
    vo.setStatusDesc(status != null ? status.getDescription() : task.getStatus());

    vo.setTenantCode(task.getTenantId());
    vo.setUserId(task.getUserId());
    vo.setProgressPercent(task.getProgressPercent());
    vo.setOutputPayload(task.getOutputPayload());
    vo.setErrorMessage(task.getErrorMessage());
    vo.setRetryCount(task.getRetryCount());
    vo.setMaxRetry(task.getMaxRetry());
    vo.setExpireAt(task.getExpireAt());
    vo.setStartedAt(task.getStartedAt());
    vo.setCompletedAt(task.getCompletedAt());
    vo.setCreatedAt(task.getCreatedAt());
    vo.setUpdatedAt(task.getUpdatedAt());

    // 计算业务标志
    vo.setTerminal(task.isTerminal());
    vo.setRetryable(task.isRetryable());

    return vo;
  }
}
