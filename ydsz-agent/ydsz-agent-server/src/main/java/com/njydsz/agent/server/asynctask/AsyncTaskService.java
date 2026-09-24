package com.njydsz.agent.server.asynctask;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.asynctask.AsyncTaskStatus;
import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.domain.asynctask.AsyncTaskType;
import com.njydsz.agent.domain.entity.AsyncTask;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 异步任务管理服务
 *
 * <p>封装异步任务的提交、查询、取消等业务编排逻辑。
 * 作为 Server 层服务，仅依赖 Domain 层网关接口 {@link AsyncTaskStore}，
 * 不直接访问 Infra 层持久化实现。
 *
 * <p><b>职责边界</b>：
 * <ul>
 *   <li>submitTask — 准入校验、设置默认超时、计算过期时间、持久化</li>
 *   <li>getTask — 查询任务详情（含状态/进度/结果/错误）</li>
 *   <li>cancelTask — 取消任务（仅非终态可取消）</li>
 *   <li>listActiveTasks — 列出租户下所有活跃任务</li>
 *   <li>getTaskStatus — 轻量查询状态（返回完整实体供上层转换）</li>
 * </ul>
 *
 * <p><b>安全性</b>：准入检查由底层 {@link AsyncTaskStore#isAdmissionAllowed} 实现，
 * 默认每租户并发上限 10 条。超出时抛出 {@link BusinessException} 拒绝提交。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Service
public class AsyncTaskService {

  /** 默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRY = 3;

  private final AsyncTaskStore taskStore;

  /**
   * 构造器注入异步任务存储网关。
   *
   * @param taskStore 异步任务存储接口（由 Infra 层实现注入）
   */
  public AsyncTaskService(AsyncTaskStore taskStore) {
    this.taskStore = taskStore;
    log.info("[AsyncTask] 异步任务服务已初始化，当前存储类型: {}", taskStore.getType());
  }

  /**
   * 提交异步任务。
   *
   * <p>执行流程：
   * <ol>
   *   <li>校验任务类型合法性（必须为 {@link AsyncTaskType} 内定义的类型）</li>
   *   <li>检查租户准入配额</li>
   *   <li>设置默认超时秒数（未指定时使用类型默认值）</li>
   *   <li>计算过期时间并持久化</li>
   * </ol>
   *
   * @param taskType     任务类型编码（不可为 null 或空）
   * @param tenantCode   租户编码（可选）
   * @param userId       用户 ID（可选）
   * @param inputPayload 输入参数 JSON（可选）
   * @return 生成的任务 ID
   * @throws BusinessException 任务类型无效或准入配额不足时抛出
   */
  public Long submitTask(
      String taskType, String tenantCode, String userId, String inputPayload) {
    if (taskType == null || taskType.isBlank()) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_TYPE_INVALID")
          .message("任务类型不能为空")
          .build();
    }

    // 校验任务类型合法性
    AsyncTaskType type = AsyncTaskType.fromCode(taskType);
    if (type == null) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_TYPE_INVALID")
          .message("不支持的任务类型: " + taskType)
          .build();
    }

    // 准入检查
    if (!taskStore.isAdmissionAllowed(taskType, tenantCode)) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_ADMISSION_DENIED")
          .message("租户活跃任务数已达上限，请稍后重试")
          .build();
    }

    // 构建任务实体
    AsyncTask task = new AsyncTask(taskType, tenantCode, userId, inputPayload);

    // 超时与过期时间
    long timeoutSecs = type.getDefaultTimeoutSeconds();
    task.setTimeoutSeconds(timeoutSecs);
    task.setExpireAt(LocalDateTime.now().plusSeconds(timeoutSecs));

    // 设置默认重试策略
    task.setMaxRetry(DEFAULT_MAX_RETRY);
    task.setRetryCount(0);

    // 持久化
    taskStore.submit(task);
    log.info("[AsyncTask] 任务提交成功: id={}, type={}, tenant={}, timeout={}s",
        task.getId(), taskType, tenantCode, timeoutSecs);

    return task.getId();
  }

  /**
   * 查询任务详情。
   *
   * @param taskId 任务 ID
   * @return 任务实体
   * @throws BusinessException 任务不存在时抛出
   */
  public AsyncTask getTask(Long taskId) {
    if (taskId == null) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_ID_INVALID")
          .message("任务 ID 不能为空")
          .build();
    }
    return taskStore.findById(taskId)
        .orElseThrow(() -> BusinessException.builder()
            .code("ASYNC_TASK_NOT_FOUND")
            .message("任务不存在: id=" + taskId)
            .build());
  }

  /**
   * 取消任务。
   *
   * <p>仅允许取消非终态任务。若任务已完成/已取消/已过期，则抛出异常。
   *
   * @param taskId 任务 ID
   * @return 取消后的任务实体
   * @throws BusinessException 任务不存在或已处于终态时抛出
   */
  public AsyncTask cancelTask(Long taskId) {
    AsyncTask task = getTask(taskId);
    if (AsyncTaskStatus.isTerminal(task.getStatus())) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_CANCEL_FORBIDDEN")
          .message(String.format("任务已处于终态 %s，无法取消: id=%d",
              task.getStatus(), taskId))
          .build();
    }
    taskStore.cancel(taskId);
    log.info("[AsyncTask] 任务已取消: id={}, prevStatus={}", taskId, task.getStatus());
    return taskStore.findById(taskId).orElse(task);
  }

  /**
   * 列出租户下所有活跃（非终态）任务。
   *
   * @param tenantCode 租户编码（不可为 null 或空）
   * @return 活跃任务列表
   * @throws BusinessException 租户编码为空时抛出
   */
  public List<AsyncTask> listActiveTasks(String tenantCode) {
    if (tenantCode == null || tenantCode.isBlank()) {
      throw BusinessException.builder()
          .code("ASYNC_TASK_TENANT_INVALID")
          .message("租户编码不能为空")
          .build();
    }
    return taskStore.listActiveByTenant(tenantCode);
  }

  /**
   * 轻量查询任务状态（含进度和结果）。
   *
   * <p>与 {@link #getTask} 返回相同实体，但语义上用于轮询场景。
   *
   * @param taskId 任务 ID
   * @return 任务状态实体
   * @throws BusinessException 任务不存在时抛出
   */
  public AsyncTask getTaskStatus(Long taskId) {
    return getTask(taskId);
  }
}
