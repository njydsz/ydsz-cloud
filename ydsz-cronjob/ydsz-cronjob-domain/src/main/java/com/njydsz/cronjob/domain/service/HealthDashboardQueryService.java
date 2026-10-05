package com.njydsz.cronjob.domain.service;

import java.util.Map;

/**
 * 健康仪表盘查询 Service 接口（domain 层）。
 *
 * <p>封装系统资源、任务概览、DAG 工作流、调度器状态等多维度健康指标，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface HealthDashboardQueryService {

  /**
   * 获取系统整体健康状态。
   *
   * <p>聚合所有维度的健康指标，返回前端仪表盘所需的完整数据。
   *
   * @return 健康仪表盘数据
   */
  Map<String, Object> getHealth();
}
