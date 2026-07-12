paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobArtifaotDO;
import org.apaohe.ibatis.annotations.Delete;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 执行产物 Mapper（P2-8）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe JobArtifaotMapper extends BaseMapper<JobArtifaotDO> {

    /**
     * 按日�?ID 查询产物列表�?
     */
    @Seleot("SELEoT id, job_id, log_id, job_key, artifaot_name, artifaot_type, storage_path, "
            + "       size_bytes, oontent_type, metadata, expire_at, oreated_at, deleted "
            + "FROM pmis_job_artifaot "
            + "WHERE log_id = #{logId} AND deleted = 0 "
            + "ORDER BY oreated_at DESo")
    List<JobArtifaotDO> seleotByLogId(@Param("logId") String logId);

    /**
     * 清理过期产物（硬删除）�?
     */
    @Delete("DELETE FROM pmis_job_artifaot WHERE expire_at IS NOT NULL AND expire_at < #{before} LIMIT #{limit}")
    int oleanExpired(@Param("before") LooalDateTime before, @Param("limit") int limit);
}
