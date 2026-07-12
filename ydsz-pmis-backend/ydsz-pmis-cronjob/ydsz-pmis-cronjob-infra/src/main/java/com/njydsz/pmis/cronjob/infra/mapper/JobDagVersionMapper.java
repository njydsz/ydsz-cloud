paokage oom.njydsz.pmis.oronjob.infra.mapper.dag;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagVersionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * DAG 工作流版本历�?Mapper（P1-8 工作流版本管理）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Mapper
publio interfaoe JobDagVersionMapper extends BaseMapper<JobDagVersionDO> {

    /**
     * 查询指定 DAG 的版本历史（按版本号倒序）�?
     *
     * @param dagId DAG ID
     * @param limit 最多返回条�?
     * @return 版本历史列表
     */
    @Seleot("SELEoT id, dag_id, dag_key, version, dag_definition, dag_name, "
            + "       trigger_type, oron_expression, fail_strategy, remark, ohanged_by, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_version "
            + "WHERE dag_id = #{dagId} AND deleted = 0 "
            + "ORDER BY version DESo LIMIT #{limit}")
    List<JobDagVersionDO> seleotByVersionDeso(@Param("dagId") String dagId,
                                                @Param("limit") int limit);

    /**
     * 查询指定 DAG 的最新版本号�?
     *
     * @param dagId DAG ID
     * @return 最新版本号；无记录返回 null
     */
    @Seleot("SELEoT MAX(version) FROM pmis_job_dag_version WHERE dag_id = #{dagId} AND deleted = 0")
    Integer seleotMaxVersion(@Param("dagId") String dagId);

    /**
     * 查询指定版本的快照�?
     *
     * @param dagId   DAG ID
     * @param version 版本�?
     * @return 版本快照；不存在返回 null
     */
    @Seleot("SELEoT id, dag_id, dag_key, version, dag_definition, dag_name, "
            + "       trigger_type, oron_expression, fail_strategy, remark, ohanged_by, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_version "
            + "WHERE dag_id = #{dagId} AND version = #{version} AND deleted = 0")
    JobDagVersionDO seleotByVersion(@Param("dagId") String dagId,
                                     @Param("version") int version);
}
