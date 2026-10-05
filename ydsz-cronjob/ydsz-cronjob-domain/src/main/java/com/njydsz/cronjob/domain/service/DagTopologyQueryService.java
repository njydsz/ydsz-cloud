package com.njydsz.cronjob.domain.service;

import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * DAG 拓扑查询 Service 接口（domain 层）。
 *
 * <p>封装 TaskTopologyController 所需的 DAG 实例拓扑数据查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface DagTopologyQueryService {

  /**
   * 查询 DAG 实例的执行拓扑图数据。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 拓扑图数据 Map；实例不存在时返回 null
   */
  Map<String, Object> getDagInstanceTopology(String dagInstanceId);

  /**
   * 查询 DAG 实例的 Cytoscape.js 兼容可视化数据。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return Cytoscape.js 兼容的节点/边数据；实例不存在时返回 null
   */
  Map<String, Object> getDagInstanceCytoscape(String dagInstanceId);

  /**
   * 查询任务的执行历史拓扑（最近 N 次执行）。
   *
   * @param jobKey 任务 KEY
   * @param limit 最多返回条数
   * @return 执行历史列表
   */
  List<JobLogVO> getJobExecutionHistory(String jobKey, int limit);
}
