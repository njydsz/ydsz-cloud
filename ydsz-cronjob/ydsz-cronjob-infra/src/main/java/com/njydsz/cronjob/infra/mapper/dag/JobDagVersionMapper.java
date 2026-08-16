package com.njydsz.cronjob.infra.mapper.dag;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;

/**
 * 任务 DAG 版本历史 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_dag_version</code>。
 * <p>DAG 每次修改生成新版本，支持回滚、对比、A/B 实验。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_dag_version — (DAG+版本号) 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.cronjob.domain.entity.dag.JobDagVersion DAG 版本实体
 * @see com.njydsz.cronjob.server.service.JobDagService DAG Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobDagVersionMapper extends BaseMapper<JobDagVersion> {

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
            + "FROM ydsz_job_dag_version "
            + "WHERE dag_id = #{dagId} AND deleted = 0 "
            + "ORDER BY version DESC LIMIT #{limit}")
    List<JobDagVersion> selectByVersionDesc(@Param("dagId") String dagId,
                                                @Param("limit") int limit);

    /**
     * 查询指定 DAG 的最新版本号。
     *
     * @param dagId DAG ID
     * @return 最新版本号；无记录返回 null
     */
    @Select("SELECT MAX(version) FROM ydsz_job_dag_version WHERE dag_id = #{dagId} AND deleted = 0")
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
            + "FROM ydsz_job_dag_version "
            + "WHERE dag_id = #{dagId} AND version = #{version} AND deleted = 0")
    JobDagVersion selectByVersion(@Param("dagId") String dagId,
                                     @Param("version") int version);
}
