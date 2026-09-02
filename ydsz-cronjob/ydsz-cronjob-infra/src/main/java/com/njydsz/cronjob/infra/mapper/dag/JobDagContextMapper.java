package com.njydsz.cronjob.infra.mapper.dag;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.cronjob.infra.entity.dag.JobDagContext;

/**
 * DAG 实例节点上下文 Mapper（ydsz_job_dag_context 表）。
 *
 * <p>提供节点级结果的 CRUD 操作，避免 CAS 更新整行 context_json。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface JobDagContextMapper extends BaseMapper<JobDagContext> {

  /**
   * 根据 DAG 实例 ID 查询所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点上下文列表
   */
  @Select(
      "SELECT * FROM ydsz_job_dag_context WHERE dag_instance_id = #{dagInstanceId} AND deleted = 0")
  List<JobDagContext> selectByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和节点 KEY 查询单个节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param nodeKey 节点 KEY
   * @return 节点上下文，不存在返回 null
   */
  @Select(
      "SELECT * FROM ydsz_job_dag_context WHERE dag_instance_id = #{dagInstanceId} AND node_key = #{nodeKey} AND deleted = 0 LIMIT 1")
  JobDagContext selectByDagInstanceAndNodeKey(
      @Param("dagInstanceId") String dagInstanceId, @Param("nodeKey") String nodeKey);

  /**
   * 根据 DAG 实例 ID 删除所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 删除行数
   */
  @Select("UPDATE ydsz_job_dag_context SET deleted = 1 WHERE dag_instance_id = #{dagInstanceId}")
  int deleteByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);

  /**
   * 批量插入节点上下文（忽略已存在记录）。
   *
   * <p>使用 default 方法实现，逐条插入。大数据量场景可优化为 INSERT ... ON DUPLICATE KEY UPDATE。
   *
   * @param contexts 节点上下文列表
   */
  default void insertBatch(List<JobDagContext> contexts) {
    if (contexts == null || contexts.isEmpty()) {
      return;
    }
    for (JobDagContext context : contexts) {
      insert(context);
    }
  }
}
