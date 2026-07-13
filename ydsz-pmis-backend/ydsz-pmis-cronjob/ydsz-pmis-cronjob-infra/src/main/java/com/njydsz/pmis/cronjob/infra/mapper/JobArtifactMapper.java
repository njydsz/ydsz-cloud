package com.njydsz.pmis.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.domain.entity.job.JobArtifactDO;

/**
 * 执行产物 Mapper（P2-8）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobArtifactMapper extends BaseMapper<JobArtifactDO> {

    /**
     * 按日志 ID 查询产物列表。
     */
    @Select("SELECT id, job_id, log_id, job_key, artifact_name, artifact_type, storage_path, "
            + "       size_bytes, content_type, metadata, expire_at, created_at, deleted "
            + "FROM pmis_job_artifact "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY created_at DESC")
    List<JobArtifactDO> selectByLogId(@Param("logId") String logId);

    /**
     * 清理过期产物（硬删除）。
     */
    @Delete("DELETE FROM pmis_job_artifact WHERE expire_at IS NOT NULL AND expire_at < #{before} LIMIT #{limit}")
    int cleanExpired(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
