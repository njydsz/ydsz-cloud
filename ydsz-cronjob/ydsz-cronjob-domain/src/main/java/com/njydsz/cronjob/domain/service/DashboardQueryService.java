package com.njydsz.cronjob.domain.service;

import java.util.Map;

/**
 * Dashboard 查询 Service 接口（domain 层）。
 *
 * <p>封装 DashboardController 所需的任务状态分布和分组统计查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface DashboardQueryService {

  /**
   * 查询 Dashboard 概览数据。
   *
   * <p>返回任务状态分布、分组统计、调度类型分布等聚合数据。
   *
   * @return Dashboard 数据（statusDistribution / groupStats / scheduleTypeStats / summary）
   */
  Map<String, Object> getOverview();
}
