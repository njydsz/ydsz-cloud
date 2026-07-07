package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务 SLA Mapper（P2-7 SLA 管理）。
 *
 * <p>对应 {@code pmis_job_sla} 表，提供 SLA 规则的查询能力。
 * CRUD 操作由 {@link BaseMapper} 提供，本接口仅扩展自定义查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobSlaMapper extends BaseMapper<JobSlaDO> {

    /**
     * 查询所有启用的 SLA 规则（SlaScanner 周期性扫描使用）。
     *
     * @return 启用的 SLA 规则列表
     */
    @Select("SELECT id, job_id, job_key, max_duration_ms, max_fail_rate, min_success_rate, "
            + "alert_level, enabled, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_sla "
            + "WHERE deleted = 0 AND enabled = 1")
    List<JobSlaDO> selectAllEnabled();

    /**
     * 按任务 ID 查询 SLA 规则（含禁用的，便于详情展示）。
     *
     * @param jobId 任务 ID
     * @return SLA 规则；不存在返回 null
     */
    @Select("SELECT id, job_id, job_key, max_duration_ms, max_fail_rate, min_success_rate, "
            + "alert_level, enabled, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_sla "
            + "WHERE job_id = #{jobId} AND deleted = 0")
    JobSlaDO selectByJobId(@Param("jobId") String jobId);
}
