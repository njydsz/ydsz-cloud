package com.njydsz.pmis.cronjob.mapper.dag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.dag.JobDagVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * DAG 工作流版本历史 Mapper（P1-8 工作流版本管理）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface JobDagVersionMapper extends BaseMapper<JobDagVersionDO> {

    /**
     * 查询指定 DAG 的版本历史（按版本号倒序）。
     *
     * @param dagId DAG ID
     * @param limit 最多返回条数
     * @return 版本历史列表
     */
    @Select("SELECT id, dag_id, dag_key, version, dag_definition, dag_name, "
            + "       trigger_type, cron_expression, fail_strategy, remark, changed_by, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_version "
            + "WHERE dag_id = #{dagId} AND deleted = 0 "
            + "ORDER BY version DESC LIMIT #{limit}")
    List<JobDagVersionDO> selectByVersionDesc(@Param("dagId") String dagId,
                                                @Param("limit") int limit);

    /**
     * 查询指定 DAG 的最新版本号。
     *
     * @param dagId DAG ID
     * @return 最新版本号；无记录返回 null
     */
    @Select("SELECT MAX(version) FROM pmis_job_dag_version WHERE dag_id = #{dagId} AND deleted = 0")
    Integer selectMaxVersion(@Param("dagId") String dagId);

    /**
     * 查询指定版本的快照。
     *
     * @param dagId   DAG ID
     * @param version 版本号
     * @return 版本快照；不存在返回 null
     */
    @Select("SELECT id, dag_id, dag_key, version, dag_definition, dag_name, "
            + "       trigger_type, cron_expression, fail_strategy, remark, changed_by, "
            + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
            + "FROM pmis_job_dag_version "
            + "WHERE dag_id = #{dagId} AND version = #{version} AND deleted = 0")
    JobDagVersionDO selectByVersion(@Param("dagId") String dagId,
                                     @Param("version") int version);
}
