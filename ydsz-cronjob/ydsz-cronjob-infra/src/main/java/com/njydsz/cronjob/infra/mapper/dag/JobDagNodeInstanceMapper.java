package com.njydsz.cronjob.infra.mapper.dag;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;

/**
 * 任务 DAG 节点实例 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_dag_node_instance</code>。
 *
 * <p>节点实例是 DAG 执行的最小单元，记录每个节点的状态、输入、输出、耗时。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_instance_id — DAG 实例维度查询索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance 节点实例实体
 * @see com.njydsz.cronjob.server.service.JobDagService DAG Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobDagNodeInstanceMapper extends BaseMapper<JobDagNodeInstance> {

  /**
   * 根据 DAG 实例 ID 查询所有节点实例。
   *
   * @param dagInstanceId 参数说明
   * @return 返回值说明
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE dag_instance_id = #{dagInstanceId} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<JobDagNodeInstance> selectByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询节点实例（唯一）。
   * 
   * <p>注意：LOOP 场景下同一 (dagInstanceId, jobId) 可能存在多个实例 （原始 body 节点 + N 个 iter 实例），本方法仅返回其中一条（不确定）。
   * LOOP 相关的批量查询请使用 {@link #selectAllByDagInstanceAndJob}。
   *
   * @param dagInstanceId 参数说明
   * @param jobId 参数说明
   * @return 返回值说明
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE dag_instance_id = #{dagInstanceId} AND job_id = #{jobId} AND deleted = 0")
  JobDagNodeInstance selectByDagInstanceAndJob(
      @Param("dagInstanceId") String dagInstanceId, @Param("jobId") String jobId);

  /**
   * P0-4: 根据 DAG 实例 ID 和任务 ID 查询全部节点实例（含 LOOP iter 实例）。
   * 
   * <p>LOOP 场景下同一 (dagInstanceId, jobId) 会存在多个实例：
   * 
   * <ul>
   * <li>原始 body 节点实例（jobKey 无后缀，由 doExecute 创建）
   * <li>N 个 iter 实例（jobKey 带 {@code #loop} 后缀，LOOP 节点已废弃，保留注释仅兼容旧数据）
   * </ul>
   * 
   * 本方法返回全部实例，供 LOOP iter 完成处理逻辑聚合判断使用。
   *
   * @param dagInstanceId 参数说明
   * @param jobId 参数说明
   * @return 返回值说明
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE dag_instance_id = #{dagInstanceId} AND job_id = #{jobId} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<JobDagNodeInstance> selectAllByDagInstanceAndJob(
      @Param("dagInstanceId") String dagInstanceId, @Param("jobId") String jobId);

  /**
   * P1-P4: 根据任务 ID 查询所有活跃（PENDING/RUNNING）状态的节点实例。
   *
   * <p>供 DAG 节点完成事件（{@code onTaskCompleted}）快速定位匹配节点。原实现遍历所有 RUNNING 实例后
   * 逐实例查询，复杂度 O(D×N)（D=运行中实例数，N=每实例节点数），大量并发 DAG 时性能差。
   * 本方法单条 SQL 按 job_id 直接过滤（建议部署侧为 {@code ydsz_job_dag_node_instance} 增加
   * {@code (job_id, node_status)} 联合索引），复杂度 O(1) 查询。
   *
   * <p>注意：PAUSED 实例中的 PENDING 节点也会被返回，由调用方（DagInstanceExecutor）的实例状态
   * 检查兜底跳过，保证与原语义一致。
   *
   * @param jobId 任务 ID
   * @return 活跃状态的节点实例列表（按创建时间升序）
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE job_id = #{jobId} AND node_status IN ('PENDING', 'RUNNING') AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<JobDagNodeInstance> selectActiveByJobId(@Param("jobId") String jobId);

  /**
   * 标记节点开始执行（PENDING → RUNNING）。
   *
   * @param id 参数说明
   * @param startedAt 参数说明
   * @return 返回值说明
   */
  @Update(
      "UPDATE ydsz_job_dag_node_instance SET node_status = 'RUNNING', started_at = #{startedAt}, "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
  int markRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

  /**
   * 标记节点执行结束（SUCCESS / FAILED / SKIPPED）。
   *
   * @param id 参数说明
   * @param finalStatus 参数说明
   * @param finishedAt 参数说明
   * @param durationMs 参数说明
   * @param resultJson 参数说明
   * @param errorMessage 参数说明
   * @param logId 参数说明
   * @return 返回值说明
   */
  @Update(
      "UPDATE ydsz_job_dag_node_instance SET node_status = #{finalStatus}, "
          + "       finished_at = #{finishedAt}, duration_ms = #{durationMs}, "
          + "       result_json = #{resultJson}, error_message = #{errorMessage}, "
          + "       log_id = #{logId}, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{id} AND node_status = 'RUNNING' AND deleted = 0")
  int markFinished(
      @Param("id") String id,
      @Param("finalStatus") String finalStatus,
      @Param("finishedAt") LocalDateTime finishedAt,
      @Param("durationMs") long durationMs,
      @Param("resultJson") String resultJson,
      @Param("errorMessage") String errorMessage,
      @Param("logId") String logId);

  /**
   * 标记节点为 SKIPPED（前置失败且 FAIL_FAST 时跳过）。
   *
   * @param id 参数说明
   * @return 返回值说明
   */
  @Update(
      "UPDATE ydsz_job_dag_node_instance SET node_status = 'SKIPPED', "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
  int markSkipped(@Param("id") String id);

  /**
   * 标记节点重试（FAILED → RETRYING → PENDING，由 DAG 执行器重新触发）。
   *
   * @param id 参数说明
   * @return 返回值说明
   */
  @Update(
      "UPDATE ydsz_job_dag_node_instance SET node_status = 'PENDING', "
          + "       retry_count = retry_count + 1, "
          + "       updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{id} AND node_status = 'FAILED' AND deleted = 0 "
          + "       AND retry_count < max_retries")
  int markRetry(@Param("id") String id);

  /**
   * 根据 DAG 实例 ID 和任务 KEY 查询节点实例（唯一，按 KEY 精确查找）。
   * 
   * <p>与 {@link #selectByDagInstanceAndJob} 区别：本方法按 jobKey 而非 jobId 查找。
   *
   * @param dagInstanceId 参数说明
   * @param jobKey 参数说明
   * @return 返回值说明
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE dag_instance_id = #{dagInstanceId} AND job_key = #{jobKey} AND deleted = 0 "
          + "LIMIT 1")
  JobDagNodeInstance selectByDagInstanceAndJobKey(
      @Param("dagInstanceId") String dagInstanceId, @Param("jobKey") String jobKey);

  /**
   * 根据 DAG 实例 ID 和节点状态查询节点实例列表。
   *
   * @param dagInstanceId 参数说明
   * @param status 参数说明
   * @return 返回值说明
   */
  @Select(
      "SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
          + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
          + "       result_json, error_message, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag_node_instance "
          + "WHERE dag_instance_id = #{dagInstanceId} AND node_status = #{status} AND deleted = 0 "
          + "ORDER BY created_at ASC")
  List<JobDagNodeInstance> selectByDagInstanceIdAndStatus(
      @Param("dagInstanceId") String dagInstanceId, @Param("status") String status);

  /**
   * 批量插入节点实例。
   *
   * @param nodes 节点实例列表
   */
  default void insertBatch(List<JobDagNodeInstance> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return;
    }
    for (JobDagNodeInstance node : nodes) {
      insert(node);
    }
  }
}
