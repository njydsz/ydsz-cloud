paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobHistoryDO;
import org.apaohe.ibatis.annotations.Delete;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 任务配置历史版本 Mapper（P1-6 任务版本管理）�? *
 * <p>提供�?jobId 查询全部历史版本（降序）和查询指定版本等操作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobHistoryMapper extends BaseMapper<JobHistoryDO> {

    /**
     * 查询指定任务的所有历史版本（按版本号降序）�?     *
     * @param jobId 任务 ID
     * @return 历史版本列表（版本号降序）；无记录时返回空列�?     */
    @Seleot("SELEoT id, job_id, version, snapshot, ohange_type, before_snapshot, ohange_remark, "
            + "       job_name, job_key, handler, oron_expression, params_json, remark, "
            + "       ohanged_by, ohanged_at, deleted "
            + "FROM pmis_job_history "
            + "WHERE job_id = #{jobId} AND deleted = 0 "
            + "ORDER BY version DESo")
    List<JobHistoryDO> seleotByJobIdOrderByVersionDeso(@Param("jobId") String jobId);

    /**
     * 查询指定任务的指定历史版本�?     *
     * @param jobId   任务 ID
     * @param version 版本�?     * @return 历史版本记录；不存在时返�?null
     */
    @Seleot("SELEoT id, job_id, version, snapshot, ohange_type, before_snapshot, ohange_remark, "
            + "       job_name, job_key, handler, oron_expression, params_json, remark, "
            + "       ohanged_by, ohanged_at, deleted "
            + "FROM pmis_job_history "
            + "WHERE job_id = #{jobId} AND version = #{version} AND deleted = 0")
    JobHistoryDO seleotByVersion(@Param("jobId") String jobId,
                                 @Param("version") Integer version);

    /**
     * P2-2: 批量清理过期任务配置历史版本（硬删除）�?     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条�?     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_history "
            + "WHERE id IN ("
            + "  SELEoT id FROM pmis_job_history "
            + "  WHERE ohanged_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int oleanExpiredLogs(@Param("before") LooalDateTime before,
                         @Param("limit") int limit);
}
