package com.njydsz.agent.infra.asynctask;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.domain.entity.AsyncTask;

/**
 * 基于内存的异步任务存储实现 — 开发环境默认 fallback。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储任务，{@link AtomicLong} 生成自增 ID。
 * 任务数据在应用重启后丢失，且多副本部署时各副本互相不可见。
 * 适用于单实例部署或开发/测试环境。
 *
 * <p><b>并发安全</b>：所有状态变更方法内部使用 {@code synchronized} 块保护，
 * 保证 claim/release 操作的原子性。
 *
 * <p><b>装配条件</b>：由 {@link AgentAsyncTaskAutoConfiguration} 按条件注册，
 * 非直接 {@code @Component}，避免与 JDBC 实现产生重复 Bean。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class InMemoryAsyncTaskStore implements AsyncTaskStore {

  /** 存储后端标识 */
  private static final String STORE_TYPE = "memory";

  /** 默认每租户并发任务上限 */
  private static final int DEFAULT_MAX_ACTIVE_PER_TENANT = 10;

  /** 任务内存存储 */
  private final ConcurrentHashMap<Long, AsyncTask> taskStore = new ConcurrentHashMap<>(32);

  /** ID 生成器 */
  private final AtomicLong idGenerator = new AtomicLong(1);

  /** 按创建时间正序的比较器 */
  private static final Comparator<AsyncTask> CREATED_AT_ASC =
      (a, b) -> {
        if (a.getCreatedAt() == null && b.getCreatedAt() == null) {
          return 0;
        }
        if (a.getCreatedAt() == null) {
          return -1;
        }
        if (b.getCreatedAt() == null) {
          return 1;
        }
        return a.getCreatedAt().compareTo(b.getCreatedAt());
      };

  @Override
  public AsyncTask submit(AsyncTask task) {
    Objects.requireNonNull(task, "task 不能为 null");
    long id = idGenerator.getAndIncrement();
    task.setId(id);
    task.setStatus(AsyncTaskStatus.PENDING.getCode());
    task.setProgressPercent(0);
    task.setCreatedAt(LocalDateTime.now());
    taskStore.put(id, task);
    log.debug("[AsyncTask-Memory] 提交任务: id={}, type={}", id, task.getTaskType());
    return task;
  }

  @Override
  public Optional<AsyncTask> findById(Long taskId) {
    if (taskId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(taskStore.get(taskId));
  }

  @Override
  public AsyncTask updateStatus(Long taskId, AsyncTaskStatus newStatus) {
    Objects.requireNonNull(newStatus, "newStatus 不能为 null");
    AsyncTask task = taskStore.get(taskId);
    if (task == null) {
      throw new IllegalStateException("任务不存在: id=" + taskId);
    }
    synchronized (task) {
      task.setStatus(newStatus.getCode());
      if (AsyncTaskStatus.isTerminal(newStatus.getCode())) {
        task.setCompletedAt(LocalDateTime.now());
      }
    }
    log.debug("[AsyncTask-Memory] 更新状态: id={}, status={}", taskId, newStatus.getCode());
    return task;
  }

  @Override
  public void updateProgress(Long taskId, int percent, String workerId) {
    AsyncTask task = taskStore.get(taskId);
    if (task == null) {
      log.warn("[AsyncTask-Memory] 更新进度时任务不存在: id={}", taskId);
      return;
    }
    synchronized (task) {
      if (!Objects.equals(task.getWorkerId(), workerId)) {
        log.warn("[AsyncTask-Memory] Worker 不匹配，跳过进度更新: id={}, expected={}, actual={}",
            taskId, task.getWorkerId(), workerId);
        return;
      }
      task.setProgressPercent(Math.clamp(percent, 0, 100));
    }
  }

  @Override
  public List<AsyncTask> pollPending(String taskType, int limit) {
    int safeLimit = Math.max(limit, 1);
    return taskStore.values().stream()
        .filter(t -> AsyncTaskStatus.PENDING.getCode().equals(t.getStatus()))
        .filter(t -> taskType == null || taskType.equals(t.getTaskType()))
        .sorted(CREATED_AT_ASC)
        .limit(safeLimit)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public Optional<AsyncTask> claim(Long taskId, String workerId) {
    Objects.requireNonNull(workerId, "workerId 不能为 null");
    AsyncTask task = taskStore.get(taskId);
    if (task == null) {
      return Optional.empty();
    }
    synchronized (task) {
      if (!AsyncTaskStatus.PENDING.getCode().equals(task.getStatus())) {
        log.debug("[AsyncTask-Memory] 任务已被认领: id={}, status={}", taskId, task.getStatus());
        return Optional.empty();
      }
      task.setStatus(AsyncTaskStatus.RUNNING.getCode());
      task.setWorkerId(workerId);
      task.setStartedAt(LocalDateTime.now());
    }
    log.debug("[AsyncTask-Memory] 认领任务: id={}, worker={}", taskId, workerId);
    return Optional.of(task);
  }

  @Override
  public int releaseTimeoutTasks(LocalDateTime timeoutThreshold) {
    Objects.requireNonNull(timeoutThreshold, "timeoutThreshold 不能为 null");
    int count = 0;
    for (AsyncTask task : taskStore.values()) {
      if (!AsyncTaskStatus.RUNNING.getCode().equals(task.getStatus())) {
        continue;
      }
      if (task.getExpireAt() != null && task.getExpireAt().isBefore(timeoutThreshold)) {
        synchronized (task) {
          if (AsyncTaskStatus.RUNNING.getCode().equals(task.getStatus())) {
            task.setStatus(AsyncTaskStatus.PENDING.getCode());
            task.setWorkerId(null);
            task.setProgressPercent(0);
            task.setStartedAt(null);
            count++;
          }
        }
      }
    }
    if (count > 0) {
      log.info("[AsyncTask-Memory] 释放超时任务: count={}", count);
    }
    return count;
  }

  @Override
  public AsyncTask cancel(Long taskId) {
    AsyncTask task = taskStore.get(taskId);
    if (task == null) {
      throw new IllegalStateException("任务不存在: id=" + taskId);
    }
    synchronized (task) {
      task.setStatus(AsyncTaskStatus.CANCELED.getCode());
      task.setCompletedAt(LocalDateTime.now());
    }
    log.debug("[AsyncTask-Memory] 取消任务: id={}", taskId);
    return task;
  }

  @Override
  public void save(AsyncTask task) {
    Objects.requireNonNull(task, "task 不能为 null");
    if (task.getId() == null) {
      long id = idGenerator.getAndIncrement();
      task.setId(id);
      task.setCreatedAt(LocalDateTime.now());
    }
    taskStore.put(task.getId(), task);
  }

  @Override
  public List<AsyncTask> listActiveByTenant(String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      return List.of();
    }
    return taskStore.values().stream()
        .filter(t -> tenantCode.equals(t.getTenantCode()))
        .filter(t -> !AsyncTaskStatus.isTerminal(t.getStatus()))
        .sorted(CREATED_AT_ASC)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public boolean isAdmissionAllowed(String taskType, String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      return true;
    }
    long activeCount = taskStore.values().stream()
        .filter(t -> tenantCode.equals(t.getTenantCode()))
        .filter(t -> !AsyncTaskStatus.isTerminal(t.getStatus()))
        .count();
    return activeCount < DEFAULT_MAX_ACTIVE_PER_TENANT;
  }

  @Override
  public String getType() {
    return STORE_TYPE;
  }
}
