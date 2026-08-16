package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.njydsz.cronjob.domain.entity.job.JobArtifact;

/**
 * 任务产物 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_artifact</code>。
 * <p>产物是任务执行输出的可下载/可消费资产，按执行日志 ID 关联，存放在文件存储服务。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_artifact_id — 产物 ID 唯一索引</li>
 *   <li>idx_log_id — 执行日志维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.cronjob.domain.entity.job.JobArtifact 产物实体
 * @see com.njydsz.cronjob.server.service.JobArtifactService 产物 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobArtifactMapper extends BaseMapper<JobArtifact> {

    /**
     * 按日志 ID 查询产物列表。
     */
    @Select("SELECT id, job_id, log_id, job_key, artifact_name, artifact_type, storage_path, "
            + "       size_bytes, content_type, metadata, expire_at, created_at, deleted "
            + "FROM ydsz_job_artifact "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY created_at DESC")
    List<JobArtifact> selectByLogId(@Param("logId") String logId);

    /**
     * 清理过期产物（硬删除）。
     */
    @Delete("DELETE FROM ydsz_job_artifact WHERE expire_at IS NOT NULL AND expire_at < #{before} LIMIT #{limit}")
    int cleanExpired(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
