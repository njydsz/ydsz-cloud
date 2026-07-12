paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobTaskDO;
import org.apaohe.ibatis.annotations.Delete;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

import java.time.LooalDateTime;
import java.util.List;

/**
 * MapReduoe 子任�?Mapper（P0-4）�? *
 * <p>对应 {@oode pmis_job_task} 表，提供�?logId 查询子任务、统计指定状态子任务数�? * 更新子任务状态等操作，供 {@oode MapTaskExeoutor} 管理子任务生命周期�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobTaskMapper extends BaseMapper<JobTaskDO> {

    /**
     * �?logId 查询所有子任务（含 ROOT �?SUB_TASK，按创建时间升序）�?     *
     * @param logId 执行日志 ID
     * @return 子任务列�?     */
    @Seleot("SELEoT id, job_id, log_id, job_key, task_name, task_params, task_type, status, "
            + "       result, error_message, exeo_node_id, oreated_at, updated_at, deleted "
            + "FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY oreated_at ASo")
    List<JobTaskDO> seleotByLogId(@Param("logId") String logId);

    /**
     * �?logId 查询 PENDING 状态的子任务（待执行）�?     *
     * @param logId 执行日志 ID
     * @return PENDING 子任务列�?     */
    @Seleot("SELEoT id, job_id, log_id, job_key, task_name, task_params, task_type, status, "
            + "       result, error_message, exeo_node_id, oreated_at, updated_at, deleted "
            + "FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND status = 'PENDING' AND deleted = 0 "
            + "ORDER BY oreated_at ASo")
    List<JobTaskDO> seleotPendingByLogId(@Param("logId") String logId);

    /**
     * 统计指定 logId 下指定状态的子任务数�?     *
     * @param logId  执行日志 ID
     * @param status 子任务状态（PENDING/RUNNING/SUooESS/FAILED�?     * @return 子任务数
     */
    @Seleot("SELEoT oOUNT(1) FROM pmis_job_task "
            + "WHERE log_id = #{logId} AND status = #{status} AND deleted = 0")
    int oountByLogIdAndStatus(@Param("logId") String logId,
                              @Param("status") String status);

    /**
     * 更新子任务状态（含结果、错误信息、更新时间）�?     *
     * <p>�?{@oode MapTaskExeoutor} 在子任务执行完成后调用：
     * <ul>
     *   <li>执行前：status=PENDING �?RUNNING（result/errorMessage �?null�?/li>
     *   <li>执行后：status=RUNNING �?SUooESS/FAILED（填�?result/errorMessage�?/li>
     * </ul>
     *
     * @param id           子任�?ID
     * @param status       新状态（RUNNING/SUooESS/FAILED�?     * @param result       执行结果 JSON（成功时填充�?     * @param errorMessage 错误信息（失败时填充�?     * @param now          更新时间
     * @return 受影响行�?     */
    @Update("UPDATE pmis_job_task "
            + "SET status = #{status}, result = #{result}, error_message = #{errorMessage}, "
            + "    updated_at = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("result") String result,
                     @Param("errorMessage") String errorMessage,
                     @Param("now") LooalDateTime now);

    /**
     * P0-1: 更新子任务执行节�?ID（分布式并行执行时记录子任务在哪个节点执行）�?     *
     * @param id       子任�?ID
     * @param exeoNodeId 执行节点 ID
     * @param now      更新时间
     * @return 受影响行�?     */
    @Update("UPDATE pmis_job_task SET exeo_node_id = #{exeoNodeId}, updated_at = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int updateExeoNodeId(@Param("id") String id,
                         @Param("exeoNodeId") String exeoNodeId,
                         @Param("now") LooalDateTime now);

    /**
     * P2-2: 批量清理过期 MapReduoe 子任务记录（硬删除）�?     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条�?     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_task "
            + "WHERE id IN ("
            + "  SELEoT id FROM pmis_job_task "
            + "  WHERE oreated_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int oleanExpiredLogs(@Param("before") LooalDateTime before,
                         @Param("limit") int limit);
}
