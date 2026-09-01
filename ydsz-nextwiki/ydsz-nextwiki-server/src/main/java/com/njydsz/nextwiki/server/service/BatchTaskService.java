package com.njydsz.nextwiki.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.server.service.FileApplicationService.BatchResultDTO;
import com.njydsz.nextwiki.server.websocket.BatchProgressNotifier;

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
 * <p><b>状态存储（P0-2 修复）：</b>任务状态持久化到 Redis（key {@code nextwiki:batch:task:{taskId}}，
 * TTL 24 小时自动过期），支持多实例部署下跨节点查询任务状态；任务 ID 改用
 * {@link SnowflakeIdGenerator} 生成，与项目统一 ID 规范对齐（不再使用 {@code UUID.randomUUID()}）。
 *
 * <p><b>线程池：</b>使用 ydsz-common-thread 统一管理的线程池（{@code nextwikiTaskExecutor}），
 * 避免直接创建原生线程池（符合云顶编码规范 15.4 节）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class BatchTaskService {

  /** 任务状态 Redis Key 前缀 */
  private static final String KEY_TASK = "nextwiki:batch:task:";

  /** 任务状态保留时长（24 小时，过期自动清理，不再依赖内存遍历） */
  private static final Duration TASK_TTL = Duration.ofHours(24);

  /** Redis 字符串操作（任务状态读写） */
  private final RedisStringOps stringOps;

  /** 分布式 ID 生成器（P0-2：替代 UUID，符合项目 ID 规范） */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** FileApplicationService 提供者（延迟查找避免循环依赖） */
  private final ObjectProvider<FileApplicationService> fileServiceProvider;

  /** WebSocket 进度推送器（S3-P2-04） */
  private final BatchProgressNotifier progressNotifier;

  /**
   * 构造方法注入依赖。
   *
   * @param stringOps Redis 字符串操作
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @param fileServiceProvider FileApplicationService 提供者
   * @param progressNotifier WebSocket 进度推送器
   */
  public BatchTaskService(
      RedisStringOps stringOps,
      SnowflakeIdGenerator snowflakeIdGenerator,
      ObjectProvider<FileApplicationService> fileServiceProvider,
      BatchProgressNotifier progressNotifier) {
    this.stringOps = stringOps;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    this.fileServiceProvider = fileServiceProvider;
    this.progressNotifier = progressNotifier;
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
    saveTaskStatus(status);

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
  public String submitBatchMove(List<String> nodeIds, String targetParentId, String userId) {
    String taskId = generateTaskId();
    BatchTaskStatus status = new BatchTaskStatus(taskId, "batch_move", nodeIds.size());
    saveTaskStatus(status);

    executeAsync(taskId, nodeIds, userId, "move:" + targetParentId);
    return taskId;
  }

  /**
   * 查询批量任务状态（支持多实例部署，从 Redis 读取）。
   *
   * @param taskId 任务 ID
   * @return 任务状态（不存在或已过期时返回 {@code null}）
   */
  public BatchTaskStatus getTaskStatus(String taskId) {
    String json = stringOps.get(KEY_TASK + taskId, String.class);
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      return YdszJson.fromJson(json, BatchTaskStatus.class);
    } catch (Exception e) {
      log.warn("[BatchTaskService] 任务状态反序列化失败: taskId={}, err={}", taskId, e.getMessage());
      return null;
    }
  }

  /**
   * 清理统计占位（保留定时入口，实际清理由 Redis TTL 自动完成）。
   *
   * <p>P0-2 迁移 Redis 后，任务 key 自带 24 小时 TTL，不再需要内存遍历清理。
   * 本方法保留 @Scheduled 入口仅为兼容调度约定，返回 0 表示无显式清理动作。
   *
   * @return 清理的任务数量（恒为 0，TTL 自动清理）
   */
  @Scheduled(cron = "0 0 * * * *")
  public int cleanupExpiredTasks() {
    log.debug("[BatchTaskService] 任务状态由 Redis TTL 自动清理，无需显式遍历");
    return 0;
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
  public void executeAsync(String taskId, List<String> nodeIds, String userId, String operation) {
    BatchTaskStatus status = getTaskStatus(taskId);
    if (status == null) {
      log.warn("[BatchTaskService] 任务不存在或已过期: taskId={}", taskId);
      return;
    }
    status.setStatus(BatchTaskStatus.TaskStatus.RUNNING);
    status.setStartedAt(LocalDateTime.now());

    // 推送任务开始通知（S3-P2-04）
    progressNotifier.notifyTaskStarted(userId, taskId, status.getTaskType(), nodeIds.size());

    try {
      FileApplicationService fileService = getService();
      BatchResultDTO result;
      if (operation.startsWith("move:")) {
        String targetParentId = operation.substring(5);
        result = fileService.batchMove(nodeIds, targetParentId, userId);
      } else {
        result = fileService.batchDelete(nodeIds, userId);
      }

      status.setResult(BatchResultView.from(result));
      status.setProcessedCount(result.successCount() + result.failedItems().size());
      status.setStatus(BatchTaskStatus.TaskStatus.COMPLETED);

      // 推送任务完成通知（S3-P2-04）
      progressNotifier.notifyTaskCompleted(
          userId, taskId, status.getTaskType(), nodeIds.size(),
          result.successCount(), result.failedItems().size());

      log.info(
          "[BatchTaskService] 批量任务完成: taskId={}, operation={}, success={}, failed={}",
          taskId,
          operation,
          result.successCount(),
          result.failedItems().size());
    } catch (Exception e) {
      status.setStatus(BatchTaskStatus.TaskStatus.FAILED);
      status.setErrorMessage(e.getMessage());

      // 推送任务失败通知（S3-P2-04）
      progressNotifier.notifyTaskFailed(userId, taskId, status.getTaskType(), e.getMessage());

      log.error("[BatchTaskService] 批量任务失败: taskId={}, operation={}", taskId, operation, e);
    } finally {
      status.setCompletedAt(LocalDateTime.now());
      saveTaskStatus(status);
    }
  }

  /**
   * 持久化任务状态到 Redis（TTL 24 小时）。
   *
   * @param status 任务状态
   */
  private void saveTaskStatus(BatchTaskStatus status) {
    try {
      stringOps.set(KEY_TASK + status.getTaskId(), YdszJson.toJson(status), TASK_TTL);
    } catch (Exception e) {
      // 状态存储失败不阻塞任务执行，仅记录告警（后续查询可能失败，但业务不受影响）
      log.error(
          "[BatchTaskService] 任务状态持久化失败: taskId={}, err={}",
          status.getTaskId(),
          e.getMessage(),
          e);
    }
  }

  /**
   * 生成任务 ID。
   *
   * @return 唯一任务 ID（Snowflake，P0-2：替代 UUID 并去掉无效的 replace 调用）
   */
  private String generateTaskId() {
    return String.valueOf(snowflakeIdGenerator.nextId());
  }

  /**
   * 获取 FileApplicationService（通过 ObjectProvider 延迟查找避免循环依赖）。
   *
   * <p>延迟查找解决 @Async 中的循环引用问题。
   */
  private FileApplicationService getService() {
    return fileServiceProvider.getIfAvailable();
  }

  /** 批量任务状态实体（Redis 序列化存储）。 */
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

    /** 执行结果（完成后才有值，普通 POJO 保证自研 JSON 引擎可序列化） */
    private BatchResultView result;

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

  /**
   * 批量任务结果视图（普通 POJO）。
   *
   * <p>由 {@link FileApplicationService.BatchResultDTO}（record）转换而来，保证 ydsz-common-json
   * 自研引擎可正常序列化/反序列化（record 无无参构造，自研引擎不保证支持）。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  @lombok.Data
  @lombok.Builder
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class BatchResultView {

    /** 成功数量 */
    private int successCount;

    /** 失败明细列表 */
    private List<FailedItemView> failedItems;

    /**
     * 从领域批量结果转换。
     *
     * @param result 领域批量结果（record）
     * @return 可序列化视图
     */
    public static BatchResultView from(BatchResultDTO result) {
      if (result == null) {
        return null;
      }
      return BatchResultView.builder()
          .successCount(result.successCount())
          .failedItems(
              result.failedItems().stream()
                  .map(f -> FailedItemView.builder().itemId(f.itemId()).reason(f.reason()).build())
                  .toList())
          .build();
    }
  }

  /** 失败项明细视图（普通 POJO）。 */
  @lombok.Data
  @lombok.Builder
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class FailedItemView {

    /** 失败项 ID */
    private String itemId;

    /** 失败原因 */
    private String reason;
  }
}
