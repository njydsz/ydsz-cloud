package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.schedule.GlueCodeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * GLUE 在线编码 Mapper（P1-2 GLUE 在线编码）。
 *
 * <p>提供按 jobId 查询最新版本、查询全部版本等操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface GlueCodeMapper extends BaseMapper<GlueCodeDO> {

    /**
     * 查询指定任务的最新版本 GLUE 代码。
     *
     * <p>按 version 降序取第一条未删除记录。
     *
     * @param jobId 任务 ID
     * @return 最新版本 GLUE 代码；不存在时返回 null
     */
    @Select("SELECT id, job_id, source_code, language, version, remark, "
            + "       created_by, created_at, deleted "
            + "FROM pmis_job_glue "
            + "WHERE job_id = #{jobId} AND deleted = 0 "
            + "ORDER BY version DESC "
            + "LIMIT 1")
    GlueCodeDO selectLatestByJobId(@Param("jobId") String jobId);
}
