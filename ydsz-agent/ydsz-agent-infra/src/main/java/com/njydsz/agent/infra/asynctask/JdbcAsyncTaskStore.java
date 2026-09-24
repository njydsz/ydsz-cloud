package com.njydsz.agent.infra.asynctask;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.domain.entity.AsyncTask;
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.AsyncTaskPO;
import com.njydsz.agent.infra.mapper.AsyncTaskMapper;

/**
 * 基于 PostgreSQL 的异步任务持久化实现 — 生产环境首选。
 *
 * <p>使用 MyBatis Plus Mapper 操作 {@code ydsz_agt_async_task} 表。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO（含 MP 注解），
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 实体。
 *
 * <p><b>DDD 分层：</b> domain 层 AsyncTask 为纯净 POJO；PO 层承载 MP 注解。
 * <b>生产就绪</b>：多副本部署安全（数据库行级锁保证 Worker 认领互斥）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcAsyncTaskStore implements AsyncTaskStore {

  /** 存储后端标识 */
  private static final String STORE_TYPE = "jdbc";

  /** 活跃状态编码列表 */
  private static final List<String> ACTIVE_STATUSES;

  static {
    List<String> statuses = new ArrayList<>();
    for (AsyncTaskStatus s : AsyncTaskStatus.values()) {
      if (!AsyncTaskStatus.isTerminal(s.getCode())) {
        statuses.add(s.getCode());
      }
    }
    ACTIVE_STATUSES = Collections.unmodifiableList(statuses);
  }

  private final AsyncTaskMapper asyncTaskMapper;

  private final AgentPoConverter poConverter;

  @Override
  public AsyncTask submit(AsyncTask task) {
    Objects.requireNonNull(task, "task 不能为 null");
    task.setStatus(AsyncTaskStatus.PENDING.getCode());
    task.setProgressPercent(0);
    task.setRetryCount(task.getRetryCount() != null ? task.getRetryCount() : 0);
    AsyncTaskPO po = poConverter.domainToPo(task);
    asyncTaskMapper.insert(po);
    task.setId(po.getId());
    log.debug("[AsyncTask-JDBC] 提交任务: id={}, type={}", task.getId(), task.getTaskType());
    return task;
  }

  @Override
  public Optional<AsyncTask> findById(Long taskId) {
    if (taskId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(asyncTaskMapper.selectById(taskId))
        .map(poConverter::poToDomain);
  }

  @Override
  public AsyncTask updateStatus(Long taskId, AsyncTaskStatus newStatus) {
    Objects.requireNonNull(newStatus, "newStatus 不能为 null");
    AsyncTaskPO existingPo = asyncTaskMapper.selectById(taskId);
    if (existingPo == null) {
      throw new IllegalStateException("任务不存在: id=" + taskId);
    }
    AsyncTask existing = poConverter.poToDomain(existingPo);
    existing.setStatus(newStatus.getCode());
    if (AsyncTaskStatus.isTerminal(newStatus.getCode())) {
      existing.setCompletedAt(LocalDateTime.now());
    }
    asyncTaskMapper.updateById(poConverter.domainToPo(existing));
    log.debug("[AsyncTask-JDBC] 更新状态: id={}, status={}", taskId, newStatus.getCode());
    return existing;
  }

  @Override
  public void updateProgress(Long taskId, int percent, String workerId) {
    int safePercent = Math.clamp(percent, 0, 100);
    int rows = asyncTaskMapper.updateTaskProgress(taskId, safePercent, workerId);
    if (rows == 0) {
      log.warn("[AsyncTask-JDBC] 更新进度失败（任务不存在或 Worker 不匹配）: id={}", taskId);
    }
  }

  @Override
  public List<AsyncTask> pollPending(String taskType, int limit) {
    int safeLimit = Math.max(limit, 1);
    List<AsyncTaskPO> poList = asyncTaskMapper.selectPendingTasks(taskType, safeLimit);
    return poList.stream()
        .map(poConverter::poToDomain)
        .toList();
  }

  @Override
  public Optional<AsyncTask> claim(Long taskId, String workerId) {
    Objects.requireNonNull(workerId, "workerId 不能为 null");
    LocalDateTime now = LocalDateTime.now();
    int rows = asyncTaskMapper.claimTask(taskId, workerId, now);
    if (rows == 0) {
      log.debug("[AsyncTask-JDBC] 认领失败（已被其他 Worker 认领或任务不存在）: id={}", taskId);
      return Optional.empty();
    }
    log.debug("[AsyncTask-JDBC] 认领任务: id={}, worker={}", taskId, workerId);
    return Optional.ofNullable(asyncTaskMapper.selectById(taskId))
        .map(poConverter::poToDomain);
  }

  @Override
  public int releaseTimeoutTasks(LocalDateTime timeoutThreshold) {
    Objects.requireNonNull(timeoutThreshold, "timeoutThreshold 不能为 null");
    int count = asyncTaskMapper.releaseTimeoutTasks(timeoutThreshold);
    if (count > 0) {
      log.info("[AsyncTask-JDBC] 释放超时任务: count={}", count);
    }
    return count;
  }

  @Override
  public AsyncTask cancel(Long taskId) {
    AsyncTaskPO existingPo = asyncTaskMapper.selectById(taskId);
    if (existingPo == null) {
      throw new IllegalStateException("任务不存在: id=" + taskId);
    }
    AsyncTask existing = poConverter.poToDomain(existingPo);
    existing.setStatus(AsyncTaskStatus.CANCELED.getCode());
    existing.setCompletedAt(LocalDateTime.now());
    asyncTaskMapper.updateById(poConverter.domainToPo(existing));
    log.debug("[AsyncTask-JDBC] 取消任务: id={}", taskId);
    return existing;
  }

  @Override
  public void save(AsyncTask task) {
    Objects.requireNonNull(task, "task 不能为 null");
    AsyncTaskPO po = poConverter.domainToPo(task);
    if (task.getId() == null) {
      asyncTaskMapper.insert(po);
      task.setId(po.getId());
    } else {
      asyncTaskMapper.updateById(po);
    }
  }

  @Override
  public List<AsyncTask> listActiveByTenant(String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      return List.of();
    }
    List<AsyncTaskPO> poList = asyncTaskMapper.selectList(
        new LambdaQueryWrapper<AsyncTaskPO>()
            .eq(AsyncTaskPO::getTenantCode, tenantCode)
            .in(AsyncTaskPO::getStatus, ACTIVE_STATUSES)
            .orderByAsc(AsyncTaskPO::getCreatedAt));
    return poList.stream()
        .map(poConverter::poToDomain)
        .toList();
  }

  @Override
  public boolean isAdmissionAllowed(String taskType, String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      return true;
    }
    int count = asyncTaskMapper.countActiveByTenant(tenantCode, ACTIVE_STATUSES);
    return count < 10;
  }

  @Override
  public String getType() {
    return STORE_TYPE;
  }
}
