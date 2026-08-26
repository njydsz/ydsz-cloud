package com.njydsz.cronjob.infra.mapper.dag;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.infra.entity.dag.JobDagContext;

/**
 * DAG 实例节点上下文 Mapper（ydsz_job_dag_context 表）。
 *
 * <p>提供节点级结果的 CRUD 操作，避免 CAS 更新整行 context_json。
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Mapper
public interface JobDagContextMapper extends BaseMapper<JobDagContext> {

  /**
   * 根据 DAG 实例 ID 查询所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点上下文列表
   */
  List<JobDagContext> selectByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和节点 KEY 查询单个节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param nodeKey 节点 KEY
   * @return 节点上下文，不存在返回 null
   */
  JobDagContext selectByDagInstanceAndNodeKey(
      @Param("dagInstanceId") String dagInstanceId, @Param("nodeKey") String nodeKey);

  /**
   * 批量插入节点上下文（忽略已存在记录）。
   *
   * @param contexts 节点上下文列表
   * @return 插入行数
   */
  int insertBatch(@Param("list") List<JobDagContext> contexts);

  /**
   * 根据 DAG 实例 ID 删除所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 删除行数
   */
  int deleteByDagInstanceId(@Param("dagInstanceId") String dagInstanceId);
}
