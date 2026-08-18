package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.server.service.FileApplicationService.BatchResult;

/**
 * 批量任务异步执行服务。
 *
 * <p>封装批量删除/移动的异步执行与状态跟踪，返回任务 ID 供前端轮询结果。
 *
 * <p><b>任务生命周期：</b>
 *
 * <pre>
 *   PENDING → RUNNING → COMPLETED / FAILED
 * </pre>
 *
 * <p><b>线程池：</b>使用 ydsz-common-thread 统一管理的线程池（{@code nextwikiTaskExecutor}）， 避免直接创建原生线程池（符合云顶编码规范
 * 15.4 节）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class BatchTaskService {

  /** 任务状态存储（生产环境可替换为 Redis 或 DB 持久化） */
  private final Map<String, BatchTaskStatus> taskStore = new ConcurrentHashMap<>();

  /** FileApplicationService 提供者（延迟查找避免循环依赖） */
  private final ObjectProvider<FileApplicationService> fileServiceProvider;

  /**
   * 构造方法注入 ObjectProvider。
   *
   * @param fileServiceProvider FileApplicationService 提供者
   */
  public BatchTaskService(ObjectProvider<FileApplicationService> fileServiceProvider) {
    this.fileServiceProvider = fileServiceProvider;
  }

  /**
   * 提交批量删除任务（异步执行）。
   *
   * @param nodeIds 待删除节点 ID 列表
   * @param userId 操作人 ID
   * @return 任务 ID（用于后续查询执行状态）
   */
  public String submitBatchDelete(List<String> nodeIds, String userId) {
    String taskId = generateTaskId();
    BatchTaskStatus status = new BatchTaskStatus(taskId, "batch_delete", nodeIds.size());
    taskStore.put(taskId, status);

    executeAsync(taskId, nodeIds, userId, "delete");
    return taskId;
  }

  /**
   * 提交批量移动任务（异步执行）。
   *
   * @param nodeIds 待移动节点 ID 列表
   * @param targetParentId 目标父目录 ID
   * @param userId 操作人 ID
   * @return 任务 ID（用于后续查询执行状态）
   */
  public String submitBatchMove(
      List<String> nodeIds, String targetParentId, String userId) {
    String taskId = generateTaskId();
    BatchTaskStatus status = new BatchTaskStatus(taskId, "batch_move", nodeIds.size());
    taskStore.put(taskId, status);

    executeAsync(taskId, nodeIds, userId, "move:" + targetParentId);
    return taskId;
  }

  /**
   * 查询批量任务状态。
   *
   * @param taskId 任务 ID
   * @return 任务状态（不存在时返回 {@code null}）
   */
  public BatchTaskStatus getTaskStatus(String taskId) {
    return taskStore.get(taskId);
  }

  /**
   * 清理已完成/失败的任务（防止内存泄漏）。
   *
   * <p>每小时自动执行一次，清理超过 24 小时的已完成/失败任务。
   *
   * @return 清理的任务数量
   */
  @Scheduled(cron = "0 0 * * * *")
  public int cleanupExpiredTasks() {
    LocalDateTime threshold = LocalDateTime.now().minusHours(24);
    int count = 0;
    for (Map.Entry<String, BatchTaskStatus> entry : taskStore.entrySet()) {
      BatchTaskStatus task = entry.getValue();
      if (task.getStatus() != BatchTaskStatus.TaskStatus.RUNNING
          && task.getCompletedAt() != null
          && task.getCompletedAt().isBefore(threshold)) {
        taskStore.remove(entry.getKey());
        count++;
      }
    }
    if (count > 0) {
      log.info("[BatchTaskService] 清理过期批量任务: count={}", count);
    }
    return count;
  }

  // ==================== 私有方法 ====================

  /**
   * 异步执行批量任务。
   *
   * @param taskId 任务 ID
   * @param nodeIds 节点 ID 列表
   * @param userId 操作人 ID
   * @param operation 操作类型（delete 或 move:targetParentId）
   */
  @Async("nextwikiTaskExecutor")
  public void executeAsync(
      String taskId, List<String> nodeIds, String userId, String operation) {
    BatchTaskStatus status = taskStore.get(taskId);
    if (status == null) {
      return;
    }
    status.setStatus(BatchTaskStatus.TaskStatus.RUNNING);
    status.setStartedAt(LocalDateTime.now());

    try {
      FileApplicationService fileService = getService();
      BatchResult result;
      if (operation.startsWith("move:")) {
        String targetParentId = operation.substring(5);
        result = fileService.batchMove(nodeIds, targetParentId, userId);
      } else {
        result = fileService.batchDelete(nodeIds, userId);
      }

      status.setResult(result);
      status.setProcessedCount(result.successCount() + result.failedItems().size());
      status.setStatus(BatchTaskStatus.TaskStatus.COMPLETED);
      log.info(
          "[BatchTaskService] 批量任务完成: taskId={}, operation={}, success={}, failed={}",
          taskId,
          operation,
          result.successCount(),
          result.failedItems().size());
    } catch (Exception e) {
      status.setStatus(BatchTaskStatus.TaskStatus.FAILED);
      status.setErrorMessage(e.getMessage());
      log.error("[BatchTaskService] 批量任务失败: taskId={}, operation={}", taskId, operation, e);
    } finally {
      status.setCompletedAt(LocalDateTime.now());
    }
  }

  /**
   * 生成任务 ID。
   *
   * @return 唯一任务 ID（去掉短横线的 UUID）
   */
  private String generateTaskId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * 获取 FileApplicationService（通过 ObjectProvider 延迟查找避免循环依赖）。
   *
   * <p>延迟查找解决 @Async 中的循环引用问题。
   */
  private FileApplicationService getService() {
    return fileServiceProvider.getIfAvailable();
  }

  /** 批量任务状态实体。 */
  @lombok.Data
  @lombok.Builder
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class BatchTaskStatus {

    /** 任务 ID */
    private String taskId;

    /** 任务类型（batch_delete / batch_move） */
    private String taskType;

    /** 待处理总数 */
    private int totalCount;

    /** 已处理数量 */
    private int processedCount;

    /** 任务状态 */
    private TaskStatus status;

    /** 执行结果（完成后才有值） */
    private BatchResult result;

    /** 错误信息（失败时才有值） */
    private String errorMessage;

    /** 提交时间 */
    @lombok.Builder.Default private LocalDateTime submittedAt = LocalDateTime.now();

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 任务状态枚举 */
    public enum TaskStatus {
      /** 待执行 */
      PENDING,
      /** 执行中 */
      RUNNING,
      /** 已完成 */
      COMPLETED,
      /** 失败 */
      FAILED
    }

    /** 便捷构造方法。 */
    public BatchTaskStatus(String taskId, String taskType, int totalCount) {
      this.taskId = taskId;
      this.taskType = taskType;
      this.totalCount = totalCount;
      this.status = TaskStatus.PENDING;
      this.submittedAt = LocalDateTime.now();
    }
  }
}
