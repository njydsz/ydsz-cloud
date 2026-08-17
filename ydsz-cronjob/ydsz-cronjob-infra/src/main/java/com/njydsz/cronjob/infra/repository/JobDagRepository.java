package com.njydsz.cronjob.infra.repository;

import java.util.List;

import com.njydsz.cronjob.domain.entity.dag.JobDag;

/**
 * DAG 工作流定义 Repository。
 *
 * <p>封装 {@code ydsz_job_dag} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagRepository {

  /**
   * 根据 dagKey 查询 DAG 定义。
   *
   * @param dagKey DAG KEY
   * @return DAG 定义，不存在时返回 null
   */
  JobDag selectByDagKey(String dagKey);

  /**
   * 查询所有启用 CRON 触发的 DAG 定义。
   *
   * @return CRON 触发的 DAG 列表
   */
  List<JobDag> selectCronEnabledDags();

  /**
   * 查询所有启用状态的 DAG 定义。
   *
   * @return 启用的 DAG 列表
   */
  List<JobDag> selectEnabledDags();

  /**
   * 更新 DAG 触发统计字段。
   *
   * @param dagId DAG ID
   * @param lastFireTime 上次触发时间
   * @param nextFireTime 下次触发时间
   * @return 受影响行数
   */
  int updateFireStats(String dagId, java.time.LocalDateTime lastFireTime, java.time.LocalDateTime nextFireTime);

  /**
   * 更新 DAG 执行结果统计字段。
   *
   * @param dagId DAG ID
   * @param success 是否成功
   * @return 受影响行数
   */
  int updateResultStats(String dagId, boolean success);
}
