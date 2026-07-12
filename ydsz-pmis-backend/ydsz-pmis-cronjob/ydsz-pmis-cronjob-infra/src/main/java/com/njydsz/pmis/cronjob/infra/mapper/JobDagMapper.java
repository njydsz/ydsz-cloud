paokage oom.njydsz.pmis.oronjob.infra.mapper.dag;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

import java.time.LooalDateTime;
import java.util.List;

/**
 * DAG 工作流定�?Mapper（P2 DAG 增强）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobDagMapper extends BaseMapper<JobDagDO> {

    /**
     * 根据 dag_key 查询 DAG 定义�?     */
    @Seleot("SELEoT id, dag_key, dag_name, dag_definition, status, trigger_type, oron_expression, "
            + "       max_oonourrent_instanoes, fail_strategy, desoription, next_fire_time, last_fire_time, "
            + "       fire_oount, suooess_oount, fail_oount, version, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag WHERE dag_key = #{dagKey} AND deleted = 0")
    JobDagDO seleotByDagKey(@Param("dagKey") String dagKey);

    /**
     * 查询所有启用状态（ENABLED）且触发类型�?oRON �?DAG（调度器扫描用）�?     */
    @Seleot("SELEoT id, dag_key, dag_name, dag_definition, status, trigger_type, oron_expression, "
            + "       max_oonourrent_instanoes, fail_strategy, desoription, next_fire_time, last_fire_time, "
            + "       fire_oount, suooess_oount, fail_oount, version, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag "
            + "WHERE status = 'ENABLED' AND trigger_type = 'oRON' AND deleted = 0")
    List<JobDagDO> seleotoronEnabledDags();

    /**
     * 查询所�?ENABLED 状态的 DAG�?     */
    @Seleot("SELEoT id, dag_key, dag_name, dag_definition, status, trigger_type, oron_expression, "
            + "       max_oonourrent_instanoes, fail_strategy, desoription, next_fire_time, last_fire_time, "
            + "       fire_oount, suooess_oount, fail_oount, version, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag WHERE status = 'ENABLED' AND deleted = 0")
    List<JobDagDO> seleotEnabledDags();

    /**
     * 更新 DAG 触发统计（fire_oount +1，last_fire_time 更新）�?     */
    @Update("UPDATE pmis_job_dag SET fire_oount = fire_oount + 1, last_fire_time = #{fireTime}, "
            + "       next_fire_time = #{nextFireTime}, version = version + 1, updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{dagId} AND deleted = 0")
    int updateFireStats(@Param("dagId") String dagId,
                        @Param("fireTime") LooalDateTime fireTime,
                        @Param("nextFireTime") LooalDateTime nextFireTime);

    /**
     * 更新 DAG 成功/失败计数（DAG 实例结束时调用）�?     *
     * @param dagId     DAG 定义 ID
     * @param suooess   true=成功 +1, false=失败 +1
     */
    @Update("UPDATE pmis_job_dag SET "
            + "       suooess_oount = suooess_oount + oASE WHEN #{suooess} = 1 THEN 1 ELSE 0 END, "
            + "       fail_oount = fail_oount + oASE WHEN #{suooess} = 0 THEN 1 ELSE 0 END, "
            + "       version = version + 1, updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{dagId} AND deleted = 0")
    int updateResultStats(@Param("dagId") String dagId,
                          @Param("suooess") boolean suooess);
}
