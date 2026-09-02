package com.njydsz.cronjob.infra.mapper.dag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.infra.entity.dag.JobDagInstance;

/**
 * 任务 DAG 实例 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_dag_instance</code>。
 *
 * <p>DAG 实例记录一次完整执行的节点状态、上下文、耗时，是 DAG 运维的事实表。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_instance_id — 实例 ID 唯一索引
 *   <li>idx_dag_id — DAG 维度查询索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.cronjob.domain.entity.dag.JobDagInstance DAG 实例实体
 * @see com.njydsz.cronjob.server.service.JobDagService DAG Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobDagInstanceMapper extends BaseMapper<JobDagInstance> {

  /**
   * 根据 DAG 定义 ID 查询实例列表（按创建时间倒序）。
   *
   * @param dagId DAG 定义 ID
   * @param limit 最多返回条数
   * @return 该 DAG 的实例列表（按创建时间降序），无记录时返回空列表
   */
  @Select(
      "SELECT id, dag_id, dag_key, status, trigger_type, trigger_by, trigger_trace_id, "
          + "       context_json, started_at, finished_at, duration_ms, error_message, "
          + "       total_nodes, success_nodes, failed_nodes, skipped_nodes, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_instance "
          + "WHERE dag_id = #{dagId} AND deleted = 0 "
          + "ORDER BY created_at DESC LIMIT #{limit}")
  List<JobDagInstance> selectByDagId(@Param("dagId") String dagId, @Param("limit") int limit);

  /**
   * 查询指定状态的 DAG 实例（如查询 RUNNING 状态用于超时检测）。
   *
   * @param status 实例状态（RUNNING/PAUSED/SUCCESS/FAILED/CANCELED）
   * @return 指定状态的实例列表（按创建时间升序），无记录时返回空列表
   */
  @Select(
      "SELECT id, dag_id, dag_key, status, trigger_type, trigger_by, trigger_trace_id, "
          + "       context_json, started_at, finished_at, duration_ms, error_message, "
          + "       total_nodes, success_nodes, failed_nodes, skipped_nodes, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_instance "
          + "WHERE status = #{status} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<JobDagInstance> selectByStatus(@Param("status") String status);

  /**
   * 原子更新 DAG 实例状态（CAS，避免并发覆盖）。
   *
   * @param instanceId 实例 ID
   * @param fromStatus 期望的当前状态（CAS 条件）
   * @param toStatus 目标状态
   * @return 受影响行数（0 表示状态已变，CAS 失败）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = #{toStatus}, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = #{fromStatus} AND deleted = 0")
  int casUpdateStatus(
      @Param("instanceId") String instanceId,
      @Param("fromStatus") String fromStatus,
      @Param("toStatus") String toStatus);

  /**
   * 标记 DAG 实例开始执行（PENDING → RUNNING）。
   *
   * @param instanceId DAG 实例 ID
   * @param startedAt 节点开始执行时间
   * @return 受影响行数（0 表示非 PENDING 状态）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = 'RUNNING', started_at = #{startedAt}, "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = 'PENDING' AND deleted = 0")
  int markRunning(
      @Param("instanceId") String instanceId, @Param("startedAt") LocalDateTime startedAt);

  /**
   * 标记 DAG 实例结束（SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELED）。
   *
   * @param instanceId DAG 实例 ID
   * @param finalStatus 终态状态（SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELED）
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时（毫秒）
   * @param errorMessage 错误信息（成功时传 null）
   * @param totalNodes 节点总数
   * @param successNodes 成功节点数
   * @param failedNodes 失败节点数
   * @param skippedNodes 跳过节点数
   * @return 受影响行数（0 表示非 RUNNING 状态）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = #{finalStatus}, finished_at = #{finishedAt}, "
          + "       duration_ms = #{durationMs}, error_message = #{errorMessage}, "
          + "       total_nodes = #{totalNodes}, success_nodes = #{successNodes}, "
          + "       failed_nodes = #{failedNodes}, skipped_nodes = #{skippedNodes}, "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = 'RUNNING' AND deleted = 0")
  int markFinished(
      @Param("instanceId") String instanceId,
      @Param("finalStatus") String finalStatus,
      @Param("finishedAt") LocalDateTime finishedAt,
      @Param("durationMs") long durationMs,
      @Param("errorMessage") String errorMessage,
      @Param("totalNodes") int totalNodes,
      @Param("successNodes") int successNodes,
      @Param("failedNodes") int failedNodes,
      @Param("skippedNodes") int skippedNodes);

  /**
   * 更新 DAG 实例上下文 JSON（跨节点传参用）。
   *
   * @param instanceId DAG 实例 ID
   * @param contextJson 新的上下文 JSON 字符串
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET context_json = #{contextJson}, "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND deleted = 0")
  int updateContext(
      @Param("instanceId") String instanceId, @Param("contextJson") String contextJson);

  /**
   * 原子合并 DAG 实例上下文 JSON（P0-1 并发安全修复）。
   *
   * <p>使用 PostgreSQL {@code jsonb ||} 操作符在数据库层面原子合并， 消除 read-modify-write 竞态：并行网关多分支同时写 contextJson
   * 时不再丢失数据。
   *
   * <p>合并语义：{@code context_json = COALESCE(context_json, '{}'::jsonb) || #{mergeJson}::jsonb} 相同
   * key 的后写覆盖先写，不同 key 的各自保留。
   *
   * @param instanceId DAG 实例 ID
   * @param mergeJson 待合并的 JSON 片段（如 {@code {"nodeA":{"result":"ok"}}}）
   * @return 受影响行数（0 表示实例不存在或已删除）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance "
          + "SET context_json = COALESCE(context_json, '{}'::jsonb) || #{mergeJson}::jsonb, "
          + "    updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND deleted = 0")
  int mergeContextAtomic(
      @Param("instanceId") String instanceId, @Param("mergeJson") String mergeJson);

  /**
   * 统计指定 DAG 的活跃（RUNNING/PAUSED）实例数（并发控制用）。
   *
   * @param dagId DAG 定义 ID
   * @return 活跃（RUNNING/PAUSED）实例数量
   */
  @Select(
      "SELECT COUNT(1) FROM ydsz_job_dag_instance "
          + "WHERE dag_id = #{dagId} AND status IN ('RUNNING', 'PAUSED') AND deleted = 0")
  int countActiveInstances(@Param("dagId") String dagId);

  /**
   * P1-4: 暂停 DAG 实例（RUNNING → PAUSED）。
   *
   * <p>暂停后，所有 RUNNING 状态的节点实例保持当前状态， PENDING 状态的节点不会被派发，直到恢复。
   *
   * @param instanceId DAG 实例 ID
   * @return 受影响行数（0 表示非 RUNNING 状态，无法暂停）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = 'PAUSED', updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = 'RUNNING' AND deleted = 0")
  int markPaused(@Param("instanceId") String instanceId);

  /**
   * P1-4: 恢复 DAG 实例（PAUSED → RUNNING）。
   *
   * @param instanceId DAG 实例 ID
   * @return 受影响行数（0 表示非 PAUSED 状态，无法恢复）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = 'RUNNING', updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = 'PAUSED' AND deleted = 0")
  int markResumed(@Param("instanceId") String instanceId);

  /**
   * P1-4: 取消 DAG 实例（RUNNING/PAUSED → CANCELED）。
   *
   * @param instanceId DAG 实例 ID
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时（毫秒）
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET status = 'CANCELED', finished_at = #{finishedAt}, "
          + "       duration_ms = #{durationMs}, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status IN ('RUNNING', 'PAUSED') AND deleted = 0")
  int markCanceled(
      @Param("instanceId") String instanceId,
      @Param("finishedAt") LocalDateTime finishedAt,
      @Param("durationMs") long durationMs);

  /**
   * P1-2: 统计指定状态的 DAG 实例数量。
   *
   * @param status 实例状态
   * @return 实例数量
   */
  @Select("SELECT COUNT(1) FROM ydsz_job_dag_instance "
      + "WHERE status = #{status} AND deleted = 0")
  long countByStatus(@Param("status") String status);

  /**
   * P1-2: 统计指定日期触发的 DAG 实例数量。
   *
   * @param date 日期
   * @return 实例数量
   */
  @Select("SELECT COUNT(1) FROM ydsz_job_dag_instance "
      + "WHERE DATE(created_at) = #{date} AND deleted = 0")
  long countByDate(@Param("date") LocalDate date);

  /**
   * P1-2: 统计指定日期、指定状态的 DAG 实例数量。
   *
   * @param status 实例状态
   * @param date 日期
   * @return 实例数量
   */
  @Select("SELECT COUNT(1) FROM ydsz_job_dag_instance "
      + "WHERE status = #{status} AND DATE(created_at) = #{date} AND deleted = 0")
  long countByStatusAndDate(@Param("status") String status, @Param("date") LocalDate date);

  /**
   * 更新 DAG 定义的结果统计（成功/失败次数）。
   *
   * @param dagId DAG 定义 ID
   * @param success 是否成功
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_dag SET "
          + "fire_count = fire_count + 1, "
          + "success_count = success_count + CASE WHEN #{success} = true THEN 1 ELSE 0 END, "
          + "fail_count = fail_count + CASE WHEN #{success} = true THEN 0 ELSE 1 END, "
          + "updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{dagId}")
  int updateResultStats(@Param("dagId") String dagId, @Param("success") boolean success);

  /**
   * P1-11: 原子递增 DAG 实例的节点计数器。
   *
   * <p>在数据库层面直接递增，避免 read-modify-write 竞态。每个节点完成时调用一次，
   * 复杂度 O(1)。
   *
   * @param instanceId DAG 实例 ID
   * @param counter 计数器名称: success / failed / skipped
   * @return 受影响行数
   */
  @Update(
      "<script>"
          + "UPDATE ydsz_job_dag_instance SET "
          + "<choose>"
          + "  <when test=\"counter == 'success'\">success_nodes = success_nodes + 1</when>"
          + "  <when test=\"counter == 'failed'\">failed_nodes = failed_nodes + 1</when>"
          + "  <when test=\"counter == 'skipped'\">skipped_nodes = skipped_nodes + 1</when>"
          + "</choose>"
          + ", updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} AND status = 'RUNNING' AND deleted = 0"
          + "</script>")
  int incrementNodeCounter(
      @Param("instanceId") String instanceId, @Param("counter") String counter);

  /**
   * P1-11: 条件 CAS 标记 DAG 实例终态（仅当所有节点都已完成时生效）。
   *
   * <p>WHERE 条件 {@code total_nodes = success_nodes + failed_nodes + skipped_nodes} 保证
   * 只有当所有节点都已完成时才更新终态。利用数据库行锁原子性，多个 Leader 并发
   * 调用时只有一个能成功返回 1，其余返回 0，避免重复终结。
   *
   * @param instanceId DAG 实例 ID
   * @param finalStatus 终态状态: SUCCESS / FAILED / PARTIAL_SUCCESS
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时（毫秒）
   * @param errorMessage 错误信息
   * @return 受影响行数（1=终结成功，0=尚有节点未完成或已被其他 Leader 终结）
   */
  @Update(
      "UPDATE ydsz_job_dag_instance SET "
          + "status = #{finalStatus}, "
          + "finished_at = #{finishedAt}, "
          + "duration_ms = #{durationMs}, "
          + "error_message = #{errorMessage}, "
          + "updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{instanceId} "
          + "AND status = 'RUNNING' "
          + "AND total_nodes = success_nodes + failed_nodes + skipped_nodes "
          + "AND deleted = 0")
  int tryFinalizeInstance(
      @Param("instanceId") String instanceId,
      @Param("finalStatus") String finalStatus,
      @Param("finishedAt") LocalDateTime finishedAt,
      @Param("durationMs") long durationMs,
      @Param("errorMessage") String errorMessage);
}
