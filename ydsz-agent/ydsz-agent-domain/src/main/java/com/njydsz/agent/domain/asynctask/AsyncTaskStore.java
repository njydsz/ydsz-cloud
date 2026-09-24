package com.njydsz.agent.domain.asynctask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.agent.domain.entity.AsyncTask;

/**
 * 异步任务存储接口（网关层）
 *
 * <p>管理异步任务的持久化和状态流转。实现可选择内存、Redis 或 JDBC 持久化。
 *
 * <p><b>DDD 合规</b>：接口定义在 domain 层，实现位于 infra 层。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface AsyncTaskStore {

  /**
   * 提交任务（初始状态 PENDING）。
   *
   * @param task 异步任务实体
   * @return 保存后的任务（含生成的 ID）
   */
  AsyncTask submit(AsyncTask task);

  /**
   * 根据 ID 查询任务。
   *
   * @param taskId 任务 ID
   * @return 任务实体
   */
  Optional<AsyncTask> findById(Long taskId);

  /**
   * 更新任务状态。
   *
   * @param taskId    任务 ID
   * @param newStatus 新状态
   * @return 更新后的任务
   */
  AsyncTask updateStatus(Long taskId, AsyncTaskStatus newStatus);

  /**
   * 更新任务进度。
   *
   * @param taskId   任务 ID
   * @param percent  进度百分比（0-100）
   * @param workerId Worker 标识
   */
  void updateProgress(Long taskId, int percent, String workerId);

  /**
   * 获取待分配的任务列表（用于 Worker 拉取）。
   *
   * @param taskType 任务类型（null 表示不过滤）
   * @param limit    最多返回条数
   * @return 待处理任务列表
   */
  List<AsyncTask> pollPending(String taskType, int limit);

  /**
   * 认领任务（PENDING → RUNNING）。
   *
   * @param taskId   任务 ID
   * @param workerId Worker 标识
   * @return 竞争成功返回任务实体，已被其他 Worker 认领返回 empty
   */
  Optional<AsyncTask> claim(Long taskId, String workerId);

  /**
   * 释放超时的 RUNNING 任务（重新置为 PENDING 供其他 Worker 认领）。
   *
   * @param timeoutThreshold 超时阈值（早于此时间的 RUNNING 视为超时）
   * @return 释放的任务数
   */
  int releaseTimeoutTasks(LocalDateTime timeoutThreshold);

  /**
   * 取消任务。
   *
   * @param taskId 任务 ID
   * @return 取消后的任务
   */
  AsyncTask cancel(Long taskId);

  /**
   * 保存/更新任务（全字段）。
   *
   * @param task 任务实体
   */
  void save(AsyncTask task);

  /**
   * 查询租户下的活跃任务。
   *
   * @param tenantCode 租户编码
   * @return 活跃任务列表
   */
  List<AsyncTask> listActiveByTenant(String tenantCode);

  /**
   * 判断当前是否允许接受新任务（配额检查）。
   *
   * @param taskType   任务类型
   * @param tenantCode 租户编码
   * @return true=允许
   */
  boolean isAdmissionAllowed(String taskType, String tenantCode);

  /**
   * 存储类型标识。
   *
   * @return 如 "memory"、"redis"、"jdbc"
   */
  String getType();
}
