package com.njydsz.cronjob.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagContextVO;

/**
 * DAG 实例节点上下文 Repository（domain 层契约，P0-13 优化）。
 *
 * <p>提供节点级结果的存储与查询能力，避免 CAS 更新整行 context_json。
 *
 * @author ydsz-team
 * @since 1.0.2
 */
public interface DagContextRepository {

  /**
   * 保存节点上下文（INSERT 或 UPSERT）。
   *
   * @param vo 节点上下文 VO
   */
  void save(JobDagContextVO vo);

  /**
   * 根据 DAG 实例 ID 和节点 KEY 查询节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param nodeKey 节点 KEY
   * @return 节点上下文，不存在返回 Optional.empty()
   */
  Optional<JobDagContextVO> findByDagInstanceAndNodeKey(String dagInstanceId, String nodeKey);

  /**
   * 根据 DAG 实例 ID 查询所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点上下文列表
   */
  List<JobDagContextVO> findByDagInstanceId(String dagInstanceId);

  /**
   * 批量保存节点上下文。
   *
   * @param vos 节点上下文列表
   */
  void saveBatch(List<JobDagContextVO> vos);

  /**
   * 根据 DAG 实例 ID 删除所有节点上下文。
   *
   * @param dagInstanceId DAG 实例 ID
   */
  void deleteByDagInstanceId(String dagInstanceId);
}
