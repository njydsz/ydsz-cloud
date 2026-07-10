package com.njydsz.pmis.cronjob.mapper.job;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.job.JobVersionHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务版本历史 Mapper（P2-7）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface JobVersionHistoryMapper extends BaseMapper<JobVersionHistoryDO> {

    /**
     * 按任务 ID 查询版本历史（倒序）。
     */
    @Select("SELECT id, job_id, job_key, version, change_type, before_snapshot, after_snapshot, "
            + "       change_remark, changed_by, changed_at "
            + "FROM pmis_job_version_history "
            + "WHERE job_id = #{jobId} "
            + "ORDER BY version DESC LIMIT #{limit}")
    List<JobVersionHistoryDO> selectByJobId(@Param("jobId") String jobId, @Param("limit") int limit);

    /**
     * 查询指定版本的历史记录。
     */
    @Select("SELECT id, job_id, job_key, version, change_type, before_snapshot, after_snapshot, "
            + "       change_remark, changed_by, changed_at "
            + "FROM pmis_job_version_history "
            + "WHERE job_id = #{jobId} AND version = #{version}")
    JobVersionHistoryDO selectByJobIdAndVersion(@Param("jobId") String jobId,
                                                  @Param("version") int version);
}
