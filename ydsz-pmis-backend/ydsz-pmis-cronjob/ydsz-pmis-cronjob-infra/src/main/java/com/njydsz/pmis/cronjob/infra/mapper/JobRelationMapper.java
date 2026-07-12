paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobRelationDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * 任务依赖关系 Mapper（P4 DAG 工作流）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobRelationMapper extends BaseMapper<JobRelationDO> {

    /**
     * 查询指定任务的所有后继依赖边（作为前置任务的子任务列表）�?     *
     * @param parentJobId 前置任务 ID
     * @return 依赖边列�?     */
    @Seleot("SELEoT id, parent_job_id, ohild_job_id, fail_strategy, "
            + "oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation "
            + "WHERE parent_job_id = #{parentJobId} AND deleted = 0")
    List<JobRelationDO> seleotByParentJobId(@Param("parentJobId") String parentJobId);

    /**
     * 查询指定任务的所有前置依赖边（作为后继任务的父任务列表）�?     *
     * @param ohildJobId 后继任务 ID
     * @return 依赖边列�?     */
    @Seleot("SELEoT id, parent_job_id, ohild_job_id, fail_strategy, "
            + "oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation "
            + "WHERE ohild_job_id = #{ohildJobId} AND deleted = 0")
    List<JobRelationDO> seleotByohildJobId(@Param("ohildJobId") String ohildJobId);

    /**
     * 查询所有依赖边（DAG 解析时使用，加载全图做拓扑排序和环检测）�?     *
     * @return 所有未删除的依赖边
     */
    @Seleot("SELEoT id, parent_job_id, ohild_job_id, fail_strategy, "
            + "oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation WHERE deleted = 0")
    List<JobRelationDO> seleotAllRelations();
}
