package com.njydsz.pmis.cronjob.mapper.job;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.job.JobTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MapReduce 子任务 Mapper（P0-4）。
 *
 * <p>对应 {@code pmis_job_task} 表，提供按 logId 查询子任务、统计指定状态子任务数、
 * 更新子任务状态等操作，供 {@code MapTaskExecutor} 管理子任务生命周期。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobTaskMapper extends BaseMapper<JobTaskDO> {

    /**
     * 按 logId 查询所有子任务（含 ROOT 和 SUB_TASK，按创建时间升序）。
     *
     * @param logId 执行日志 ID
     * @return 子任务列表
     */
    @Select("SELECT id, job_id, log_id, job_key, task_name, task_params, task_type, status, "
            + "       result, error_message, exec_node_id, created_at, updated_at, deleted "
            + "FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY created_at ASC")
    List<JobTaskDO> selectByLogId(@Param("logId") String logId);

    /**
     * 按 logId 查询 PENDING 状态的子任务（待执行）。
     *
     * @param logId 执行日志 ID
     * @return PENDING 子任务列表
     */
    @Select("SELECT id, job_id, log_id, job_key, task_name, task_params, task_type, status, "
            + "       result, error_message, exec_node_id, created_at, updated_at, deleted "
            + "FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND status = 'PENDING' AND deleted = 0 "
            + "ORDER BY created_at ASC")
    List<JobTaskDO> selectPendingByLogId(@Param("logId") String logId);

    /**
     * 统计指定 logId 下指定状态的子任务数。
     *
     * @param logId  执行日志 ID
     * @param status 子任务状态（PENDING/RUNNING/SUCCESS/FAILED）
     * @return 子任务数
     */
    @Select("SELECT COUNT(1) FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND status = #{status} AND deleted = 0")
    int countByLogIdAndStatus(@Param("logId") String logId,
                              @Param("status") String status);

    /**
     * 更新子任务状态（含结果、错误信息、更新时间）。
     *
     * <p>由 {@code MapTaskExecutor} 在子任务执行完成后调用：
     * <ul>
     *   <li>执行前：status=PENDING → RUNNING（result/errorMessage 为 null）</li>
     *   <li>执行后：status=RUNNING → SUCCESS/FAILED（填充 result/errorMessage）</li>
     * </ul>
     *
     * @param id           子任务 ID
     * @param status       新状态（RUNNING/SUCCESS/FAILED）
     * @param result       执行结果 JSON（成功时填充）
     * @param errorMessage 错误信息（失败时填充）
     * @param now          更新时间
     * @return 受影响行数
     */
    @Update("UPDATE pmis_job_task "
            + "SET status = #{status}, result = #{result}, error_message = #{errorMessage}, "
            + "    updated_at = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("result") String result,
                     @Param("errorMessage") String errorMessage,
                     @Param("now") LocalDateTime now);

    /**
     * P0-1: 更新子任务执行节点 ID（分布式并行执行时记录子任务在哪个节点执行）。
     *
     * @param id       子任务 ID
     * @param execNodeId 执行节点 ID
     * @param now      更新时间
     * @return 受影响行数
     */
    @Update("UPDATE pmis_job_task SET exec_node_id = #{execNodeId}, updated_at = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int updateExecNodeId(@Param("id") String id,
                         @Param("execNodeId") String execNodeId,
                         @Param("now") LocalDateTime now);

    /**
     * P2-2: 批量清理过期 MapReduce 子任务记录（硬删除）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_task "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_job_task "
            + "  WHERE created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}
