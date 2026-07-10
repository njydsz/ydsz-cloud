package com.njydsz.pmis.cronjob.mapper.job;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.job.JobHistoryDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务配置历史版本 Mapper（P1-6 任务版本管理）。
 *
 * <p>提供按 jobId 查询全部历史版本（降序）和查询指定版本等操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobHistoryMapper extends BaseMapper<JobHistoryDO> {

    /**
     * 查询指定任务的所有历史版本（按版本号降序）。
     *
     * @param jobId 任务 ID
     * @return 历史版本列表（版本号降序）；无记录时返回空列表
     */
    @Select("SELECT id, job_id, version, snapshot, change_type, before_snapshot, change_remark, "
            + "       job_name, job_key, handler, cron_expression, params_json, remark, "
            + "       changed_by, changed_at, deleted "
            + "FROM pmis_job_history "
            + "WHERE job_id = #{jobId} AND deleted = 0 "
            + "ORDER BY version DESC")
    List<JobHistoryDO> selectByJobIdOrderByVersionDesc(@Param("jobId") String jobId);

    /**
     * 查询指定任务的指定历史版本。
     *
     * @param jobId   任务 ID
     * @param version 版本号
     * @return 历史版本记录；不存在时返回 null
     */
    @Select("SELECT id, job_id, version, snapshot, change_type, before_snapshot, change_remark, "
            + "       job_name, job_key, handler, cron_expression, params_json, remark, "
            + "       changed_by, changed_at, deleted "
            + "FROM pmis_job_history "
            + "WHERE job_id = #{jobId} AND version = #{version} AND deleted = 0")
    JobHistoryDO selectByVersion(@Param("jobId") String jobId,
                                 @Param("version") Integer version);

    /**
     * P2-2: 批量清理过期任务配置历史版本（硬删除）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_history "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_job_history "
            + "  WHERE changed_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}
