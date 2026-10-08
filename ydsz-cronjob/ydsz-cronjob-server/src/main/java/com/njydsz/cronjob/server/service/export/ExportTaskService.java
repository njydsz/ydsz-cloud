package com.njydsz.cronjob.server.service.export;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.entity.export.ExportTask;
import com.njydsz.cronjob.domain.enums.export.ExportTaskStatusEnum;
import com.njydsz.cronjob.domain.repository.export.ExportTaskRepository;
import com.njydsz.cronjob.server.worker.export.ExportWorkerDelegate;

/**
 * 异步导出任务服务（server 层，业务编排）。
 *
 * <p>提供任务提交、查询、取消、状态更新等编排能力。
 * 具体导出逻辑由 {@link ExportWorkerDelegate} 委托执行。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskService {

  private static final int DEFAULT_MAX_RETRY = 3;
  private static final int DEFAULT_EXPIRE_HOURS = 24;
  private static final int MAX_TASK_PER_USER = 10;

  private final ExportTaskRepository exportTaskRepository;
  private final SnowflakeIdGenerator idGenerator;
  private final ExportWorkerDelegate exportWorkerDelegate;

  /**
   * 提交异步导出任务。
   *
   * @param module   来源模块
   * @param taskType 导出类型
   * @param taskName 任务名称
   * @param params   导出参数（将被序列化为 JSON）
   * @param userId   提交用户 ID
   * @param tenantId 租户 ID
   * @return 任务 ID
   */
  public String submitTask(String module, String taskType, String taskName,
                           Map<String, Object> params, String userId, String tenantId) {
    ExportTask task = new ExportTask();
    task.setId(String.valueOf(idGenerator.nextId()));
    task.setModule(module);
    task.setTaskType(taskType);
    task.setTaskName(taskName);
    task.setParams(YdszJson.toJson(params));
    task.setStatus(ExportTaskStatusEnum.PENDING.getCode());
    task.setProgressPercent(0);
    task.setRetryCount(0);
    task.setMaxRetry(DEFAULT_MAX_RETRY);
    task.setTenantId(tenantId);
    task.setCreatedBy(userId);
    task.setExpireAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRE_HOURS));

    exportTaskRepository.saveOrUpdate(task);

    log.info("提交导出任务 id={} module={} type={} user={}",
        task.getId(), module, taskType, userId);

    // 委托 Worker 异步处理
    exportWorkerDelegate.submit(task.getId());

    return task.getId();
  }

  /**
   * 查询任务状态。
   *
   * @param id       任务 ID
   * @param tenantId 租户 ID（数据隔离）
   * @return 任务实体
   */
  public ExportTask getTask(String id, String tenantId) {
    return exportTaskRepository.findById(id)
        .filter(task -> tenantId == null || tenantId.equals(task.getTenantId()))
        .orElse(null);
  }

  /**
   * 查询用户最近的导出任务列表。
   *
   * @param tenantId 租户 ID
   * @param userId   用户 ID
   * @param limit    返回数量上限
   * @return 任务列表
   */
  public List<ExportTask> listUserTasks(String tenantId, String userId, int limit) {
    return exportTaskRepository.listByUser(tenantId, userId, Math.min(limit, 50));
  }

  /**
   * 取消任务（仅 PENDING 状态可取消）。
   *
   * @param id       任务 ID
   * @param userId   操作用户
   * @param tenantId 租户 ID
   * @return true=取消成功
   */
  public boolean cancelTask(String id, String userId, String tenantId) {
    ExportTask task = getTask(id, tenantId);
    if (task == null || ExportTaskStatusEnum.isTerminal(task.getStatus())) {
      return false;
    }
    task.setStatus(ExportTaskStatusEnum.CANCELED.getCode());
    task.setCompletedAt(LocalDateTime.now());
    task.setUpdatedBy(userId);
    return exportTaskRepository.updateWithVersion(task);
  }

  /**
   * 更新任务状态和进度（Worker 回调）。
   *
   * @param id       任务 ID
   * @param status   新状态
   * @param progress 进度百分比
   */
  public void updateProgress(String id, ExportTaskStatusEnum status, int progress) {
    exportTaskRepository.findById(id).ifPresent(task -> {
      task.setStatus(status.getCode());
      task.setProgressPercent(Math.clamp(progress, 0, 100));
      if (ExportTaskStatusEnum.PROCESSING.getCode().equals(status.getCode())
          && task.getStartedAt() == null) {
        task.setStartedAt(LocalDateTime.now());
      }
      if (ExportTaskStatusEnum.isTerminal(status.getCode())) {
        task.setCompletedAt(LocalDateTime.now());
      }
      exportTaskRepository.updateWithVersion(task);
    });
  }

  /**
   * 更新任务结果（Worker 完成后调用）。
   *
   * @param id         任务 ID
   * @param fileName   文件名称
   * @param fileSize   文件大小
   * @param bucketName 存储桶
   * @param storageKey 存储键
   */
  public void completeTask(String id, String fileName, Long fileSize,
                           String bucketName, String storageKey) {
    exportTaskRepository.findById(id).ifPresent(task -> {
      task.setStatus(ExportTaskStatusEnum.SUCCEEDED.getCode());
      task.setProgressPercent(100);
      task.setFileName(fileName);
      task.setFileSize(fileSize);
      task.setStorageBucket(bucketName);
      task.setStorageKey(storageKey);
      task.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      task.setCompletedAt(LocalDateTime.now());
      exportTaskRepository.updateWithVersion(task);
      log.info("导出任务完成 id={} file={} size={}", id, fileName, fileSize);
    });
  }

  /**
   * 标记任务失败。
   *
   * @param id      任务 ID
   * @param message 失败原因
   */
  public void failTask(String id, String message) {
    exportTaskRepository.findById(id).ifPresent(task -> {
      task.setStatus(ExportTaskStatusEnum.FAILED.getCode());
      task.setErrorMessage(message);
      task.setCompletedAt(LocalDateTime.now());
      exportTaskRepository.updateWithVersion(task);
      log.warn("导出任务失败 id={} reason={}", id, message);
    });
  }
}
