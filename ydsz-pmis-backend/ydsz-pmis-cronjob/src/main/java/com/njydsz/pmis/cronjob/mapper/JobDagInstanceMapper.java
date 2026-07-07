package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DAG 工作流实例 Mapper（P2 DAG 增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobDagInstanceMapper extends BaseMapper<JobDagInstanceDO> {

    /**
     * 根据 DAG 定义 ID 查询实例列表（按创建时间倒序）。
     */
    @Select("SELECT id, dag_id, dag_key, status, trigger_type, trigger_by, trigger_trace_id, "
            + "       context_json, started_at, finished_at, duration_ms, error_message, "
            + "       total_nodes, success_nodes, failed_nodes, skipped_nodes, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_instance "
            + "WHERE dag_id = #{dagId} AND deleted = 0 "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    List<JobDagInstanceDO> selectByDagId(@Param("dagId") String dagId, @Param("limit") int limit);

    /**
     * 查询指定状态的 DAG 实例（如查询 RUNNING 状态用于超时检测）。
     */
    @Select("SELECT id, dag_id, dag_key, status, trigger_type, trigger_by, trigger_trace_id, "
            + "       context_json, started_at, finished_at, duration_ms, error_message, "
            + "       total_nodes, success_nodes, failed_nodes, skipped_nodes, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_instance "
            + "WHERE status = #{status} AND deleted = 0 "
            + "ORDER BY created_at ASC")
    List<JobDagInstanceDO> selectByStatus(@Param("status") String status);

    /**
     * 原子更新 DAG 实例状态（CAS，避免并发覆盖）。
     *
     * @param instanceId 实例 ID
     * @param fromStatus 期望的当前状态（CAS 条件）
     * @param toStatus   目标状态
     * @return 受影响行数（0 表示状态已变，CAS 失败）
     */
    @Update("UPDATE pmis_job_dag_instance SET status = #{toStatus}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{instanceId} AND status = #{fromStatus} AND deleted = 0")
    int casUpdateStatus(@Param("instanceId") String instanceId,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus);

    /**
     * 标记 DAG 实例开始执行（PENDING → RUNNING）。
     */
    @Update("UPDATE pmis_job_dag_instance SET status = 'RUNNING', started_at = #{startedAt}, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{instanceId} AND status = 'PENDING' AND deleted = 0")
    int markRunning(@Param("instanceId") String instanceId,
                    @Param("startedAt") LocalDateTime startedAt);

    /**
     * 标记 DAG 实例结束（SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELED）。
     */
    @Update("UPDATE pmis_job_dag_instance SET status = #{finalStatus}, finished_at = #{finishedAt}, "
            + "       duration_ms = #{durationMs}, error_message = #{errorMessage}, "
            + "       total_nodes = #{totalNodes}, success_nodes = #{successNodes}, "
            + "       failed_nodes = #{failedNodes}, skipped_nodes = #{skippedNodes}, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{instanceId} AND status = 'RUNNING' AND deleted = 0")
    int markFinished(@Param("instanceId") String instanceId,
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
     */
    @Update("UPDATE pmis_job_dag_instance SET context_json = #{contextJson}, "
            + "       updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{instanceId} AND deleted = 0")
    int updateContext(@Param("instanceId") String instanceId,
                      @Param("contextJson") String contextJson);

    /**
     * 统计指定 DAG 的活跃（RUNNING/PAUSED）实例数（并发控制用）。
     */
    @Select("SELECT COUNT(1) FROM pmis_job_dag_instance "
            + "WHERE dag_id = #{dagId} AND status IN ('RUNNING', 'PAUSED') AND deleted = 0")
    int countActiveInstances(@Param("dagId") String dagId);
}
