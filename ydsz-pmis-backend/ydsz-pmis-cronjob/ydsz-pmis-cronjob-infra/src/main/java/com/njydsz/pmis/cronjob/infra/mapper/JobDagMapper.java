package com.njydsz.pmis.cronjob.infra.mapper.dag;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;

/**
 * DAG 工作流定义 Mapper（P2 DAG 增强）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobDagMapper extends BaseMapper<JobDagDO> {

    /**
     * 根据 dag_key 查询 DAG 定义。
     */
    @Select("SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
            + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
            + "       fire_count, success_count, fail_count, version, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag WHERE dag_key = #{dagKey} AND deleted = 0")
    JobDagDO selectByDagKey(@Param("dagKey") String dagKey);

    /**
     * 查询所有启用状态（ENABLED）且触发类型为 CRON 的 DAG（调度器扫描用）。
     */
    @Select("SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
            + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
            + "       fire_count, success_count, fail_count, version, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag "
            + "WHERE status = 'ENABLED' AND trigger_type = 'CRON' AND deleted = 0")
    List<JobDagDO> selectCronEnabledDags();

    /**
     * 查询所有 ENABLED 状态的 DAG。
     */
    @Select("SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
            + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
            + "       fire_count, success_count, fail_count, version, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag WHERE status = 'ENABLED' AND deleted = 0")
    List<JobDagDO> selectEnabledDags();

    /**
     * 更新 DAG 触发统计（fire_count +1，last_fire_time 更新）。
     */
    @Update("UPDATE pmis_job_dag SET fire_count = fire_count + 1, last_fire_time = #{fireTime}, "
            + "       next_fire_time = #{nextFireTime}, version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{dagId} AND deleted = 0")
    int updateFireStats(@Param("dagId") String dagId,
                        @Param("fireTime") LocalDateTime fireTime,
                        @Param("nextFireTime") LocalDateTime nextFireTime);

    /**
     * 更新 DAG 成功/失败计数（DAG 实例结束时调用）。
     *
     * @param dagId     DAG 定义 ID
     * @param success   true=成功 +1, false=失败 +1
     */
    @Update("UPDATE pmis_job_dag SET "
            + "       success_count = success_count + CASE WHEN #{success} = 1 THEN 1 ELSE 0 END, "
            + "       fail_count = fail_count + CASE WHEN #{success} = 0 THEN 1 ELSE 0 END, "
            + "       version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{dagId} AND deleted = 0")
    int updateResultStats(@Param("dagId") String dagId,
                          @Param("success") boolean success);
}
