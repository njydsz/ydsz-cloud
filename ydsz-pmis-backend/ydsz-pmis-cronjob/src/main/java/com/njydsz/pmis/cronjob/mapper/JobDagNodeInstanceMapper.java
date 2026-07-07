package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DAG 节点实例 Mapper（P2 DAG 增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobDagNodeInstanceMapper extends BaseMapper<JobDagNodeInstanceDO> {

    /**
     * 根据 DAG 实例 ID 查询所有节点实例。
     */
    @Select("SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_node_instance "
            + "WHERE dag_instance_id = #{dagInstanceId} AND deleted = 0 "
            + "ORDER BY created_at ASC")
    List<JobDagNodeInstanceDO> selectByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);

    /**
     * 根据 DAG 实例 ID 和任务 ID 查询节点实例（唯一）。
     */
    @Select("SELECT id, dag_instance_id, dag_id, job_id, job_key, node_status, log_id, "
            + "       retry_count, max_retries, started_at, finished_at, duration_ms, "
            + "       result_json, error_message, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_node_instance "
            + "WHERE dag_instance_id = #{dagInstanceId} AND job_id = #{jobId} AND deleted = 0")
    JobDagNodeInstanceDO selectByDagInstanceAndJob(@Param("dagInstanceId") String dagInstanceId,
                                                    @Param("jobId") String jobId);

    /**
     * 标记节点开始执行（PENDING → RUNNING）。
     */
    @Update("UPDATE pmis_job_dag_node_instance SET node_status = 'RUNNING', started_at = #{startedAt}, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markRunning(@Param("id") String id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * 标记节点执行结束（SUCCESS / FAILED / SKIPPED）。
     */
    @Update("UPDATE pmis_job_dag_node_instance SET node_status = #{finalStatus}, "
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
    @Update("UPDATE pmis_job_dag_node_instance SET node_status = 'SKIPPED', "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'PENDING' AND deleted = 0")
    int markSkipped(@Param("id") String id);

    /**
     * 标记节点重试（FAILED → RETRYING → PENDING，由 DAG 执行器重新触发）。
     */
    @Update("UPDATE pmis_job_dag_node_instance SET node_status = 'PENDING', "
            + "       retry_count = retry_count + 1, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND node_status = 'FAILED' AND deleted = 0 "
            + "       AND retry_count < max_retries")
    int markRetry(@Param("id") String id);

    /**
     * 批量插入节点实例。
     */
    default void insertBatch(List<JobDagNodeInstanceDO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (JobDagNodeInstanceDO node : nodes) {
            insert(node);
        }
    }
}
