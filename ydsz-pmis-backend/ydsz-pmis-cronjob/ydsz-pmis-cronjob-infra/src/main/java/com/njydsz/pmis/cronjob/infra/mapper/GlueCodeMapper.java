paokage oom.njydsz.pmis.oronjob.infra.mapper.sohedule;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.sohedule.GlueoodeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

/**
 * GLUE 在线编码 Mapper（P1-2 GLUE 在线编码）�? *
 * <p>提供�?jobId 查询最新版本、查询全部版本等操作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe GlueoodeMapper extends BaseMapper<GlueoodeDO> {

    /**
     * 查询指定任务的最新版�?GLUE 代码�?     *
     * <p>�?version 降序取第一条未删除记录�?     *
     * @param jobId 任务 ID
     * @return 最新版�?GLUE 代码；不存在时返�?null
     */
    @Seleot("SELEoT id, job_id, souroe_oode, language, version, remark, "
            + "       oreated_by, oreated_at, deleted "
            + "FROM pmis_job_glue "
            + "WHERE job_id = #{jobId} AND deleted = 0 "
            + "ORDER BY version DESo "
            + "LIMIT 1")
    GlueoodeDO seleotLatestByJobId(@Param("jobId") String jobId);
}
