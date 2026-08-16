package com.njydsz.cronjob.infra.mapper.dag;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.domain.entity.dag.JobDag;

/**
 * 任务 DAG Mapper
 *
 * <p>对应数据表 <code>ydsz_job_dag</code>。
 *
 * <p>DAG 把多个 Job 编排为有向无环图，支持串行/并行/条件分支，是复杂任务的编排核心。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_dag_code — DAG 编码唯一索引
 *   <li>idx_status — 状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.dag.JobDag DAG 实体
 * @see com.njydsz.cronjob.server.service.JobDagService DAG Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobDagMapper extends BaseMapper<JobDag> {

  /** 根据 dag_key 查询 DAG 定义。 */
  @Select(
      "SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
          + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
          + "       fire_count, success_count, fail_count, version, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag WHERE dag_key = #{dagKey} AND deleted = 0")
  JobDag selectByDagKey(@Param("dagKey") String dagKey);

  /** 查询所有启用状态（ENABLED）且触发类型为 CRON 的 DAG（调度器扫描用）。 */
  @Select(
      "SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
          + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
          + "       fire_count, success_count, fail_count, version, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag "
          + "WHERE status = 'ENABLED' AND trigger_type = 'CRON' AND deleted = 0")
  List<JobDag> selectCronEnabledDags();

  /** 查询所有 ENABLED 状态的 DAG。 */
  @Select(
      "SELECT id, dag_key, dag_name, dag_definition, status, trigger_type, cron_expression, "
          + "       max_concurrent_instances, fail_strategy, description, next_fire_time, last_fire_time, "
          + "       fire_count, success_count, fail_count, version, "
          + "       created_by, created_at, updated_by, updated_at, deleted, tenant_id "
          + "FROM ydsz_job_dag WHERE status = 'ENABLED' AND deleted = 0")
  List<JobDag> selectEnabledDags();

  /** 更新 DAG 触发统计（fire_count +1，last_fire_time 更新）。 */
  @Update(
      "UPDATE ydsz_job_dag SET fire_count = fire_count + 1, last_fire_time = #{fireTime}, "
          + "       next_fire_time = #{nextFireTime}, version = version + 1, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{dagId} AND deleted = 0")
  int updateFireStats(
      @Param("dagId") String dagId,
      @Param("fireTime") LocalDateTime fireTime,
      @Param("nextFireTime") LocalDateTime nextFireTime);

  /**
   * 更新 DAG 成功/失败计数（DAG 实例结束时调用）。
   *
   * @param dagId DAG 定义 ID
   * @param success true=成功 +1, false=失败 +1
   */
  @Update(
      "UPDATE ydsz_job_dag SET "
          + "       success_count = success_count + CASE WHEN #{success} = 1 THEN 1 ELSE 0 END, "
          + "       fail_count = fail_count + CASE WHEN #{success} = 0 THEN 1 ELSE 0 END, "
          + "       version = version + 1, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{dagId} AND deleted = 0")
  int updateResultStats(@Param("dagId") String dagId, @Param("success") boolean success);
}
