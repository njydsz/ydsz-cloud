package com.njydsz.cronjob.infra.repository;

import java.util.List;

import com.njydsz.cronjob.infra.entity.dag.JobDagVersion;

/**
 * DAG 版本历史 Repository。
 *
 * <p>封装 {@code ydsz_job_dag_version} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagVersionRepository {

  /**
   * 根据 DAG ID 查询版本列表（按版本号降序）。
   *
   * @param dagId DAG ID
   * @return 版本列表
   */
  List<JobDagVersion> selectByVersionDesc(String dagId);

  /**
   * 查询指定 DAG 的最大版本号。
   *
   * @param dagId DAG ID
   * @return 最大版本号，无记录时返回 null
   */
  Integer selectMaxVersion(String dagId);

  /**
   * 根据 DAG ID 和版本号查询版本记录。
   *
   * @param dagId DAG ID
   * @param version 版本号
   * @return 版本记录，不存在时返回 null
   */
  JobDagVersion selectByVersion(String dagId, Integer version);
}
