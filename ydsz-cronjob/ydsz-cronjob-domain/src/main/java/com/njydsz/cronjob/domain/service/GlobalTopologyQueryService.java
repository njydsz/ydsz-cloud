package com.njydsz.cronjob.domain.service;

import java.util.Map;

/**
 * 全局拓扑查询 Service 接口（domain 层）。
 *
 * <p>封装 GlobalTopologyController 所需的全局任务拓扑图数据查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface GlobalTopologyQueryService {

  /**
   * 查询全局任务拓扑图数据。
   *
   * @return 拓扑图数据（nodes/links/stats）
   */
  Map<String, Object> getGlobalTopology();
}
