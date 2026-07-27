package com.njydsz.cronjob.infra.mapper.dag;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;

/**
 * DAG 节点实例 Mapper（P2 DAG 增强）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface JobDagNodeInstanceMapper extends BaseMapper<JobDagNodeInstance> {

    /**
     * 根据 DAG 实例 ID 查询所有节点实例。
     */
    @Select("SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
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
     * <p>注意：LOOP 场景下同一 (dagInstanceId, jobId) 可能存在多个实例
     * （原始 body 节点 + N 个 iter 实例），本方法仅返回其中一条（不确定）。
     * LOOP 相关的批量查询请使用 {@link #selectAllByDagInstanceAndJob}。
     */
    @Select("SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM ydsz_job_dag_node_instance "
            + "WHERE dag_instance_id = #{dagInstanceId} AND job_id = #{jobId} AND deleted = 0")
    JobDagNodeInstance selectByDagInstanceAndJob(@Param("dagInstanceId") String dagInstanceId,
                                                    @Param("jobId") String jobId);

    /**
     * P0-4: 根据 DAG 实例 ID 和任务 ID 查询全部节点实例（含 LOOP iter 实例）。
     *
     * <p>LOOP 场景下同一 (dagInstanceId, jobId) 会存在多个实例：
     * <ul>
     *   <li>原始 body 节点实例（jobKey 无后缀，由 doExecute 创建）</li>
     *   <li>N 个 iter 实例（jobKey 带 {@code #loop<i>} 后缀，由 dispatchLoopNode 创建）</li>
     * </ul>
     * 本方法返回全部实例，供 LOOP iter 完成处理逻辑聚合判断使用。
     */
    @Select("SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM ydsz_job_dag_node_instance "
            + "WHERE dag_instance_id = #{dagInstanceId} AND job_id = #{jobId} AND deleted = 0 "
            + "ORDER BY created_at ASC")
    List<JobDagNodeInstance> selectAllByDagInstanceAndJob(@Param("dagInstanceId") String dagInstanceId,
                                                              @Param("jobId") String jobId);

    /**
     * 标记节点开始执行（PENDING → RUNNING）。
     */
    @Update("UPDATE ydsz_job_dag_node_instance SET node_status = 'RUNNING', started_at = #{startedAt}, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * 标记节点执行结束（SUCCESS / FAILED / SKIPPED）。
     */
    @Update("UPDATE ydsz_job_dag_node_instance SET node_status = #{finalStatus}, "
            + "       finished_at = #{finishedAt}, duration_ms = #{durationMs}, "
            + "       result_json = #{resultJson}, error_message = #{errorMessage}, "
            + "       log_id = #{logId}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'RUNNING' AND deleted = 0")
    int markFinished(@Param("id") String id,
                     @Param("finalStatus") String finalStatus,
                     @Param("finishedAt") LocalDateTime finishedAt,
                     @Param("durationMs") long durationMs,
                     @Param("resultJson") String resultJson,
                     @Param("errorMessage") String errorMessage,
                     @Param("logId") String logId);

    /**
     * 标记节点为 SKIPPED（前置失败且 FAIL_FAST 时跳过）。
     */
    @Update("UPDATE ydsz_job_dag_node_instance SET node_status = 'SKIPPED', "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markSkipped(@Param("id") String id);

    /**
     * 标记节点重试（FAILED → RETRYING → PENDING，由 DAG 执行器重新触发）。
     */
    @Update("UPDATE ydsz_job_dag_node_instance SET node_status = 'PENDING', "
            + "       retry_count = retry_count + 1, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'FAILED' AND deleted = 0 "
            + "       AND retry_count < max_retries")
    int markRetry(@Param("id") String id);

    /**
     * 批量插入节点实例。
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
