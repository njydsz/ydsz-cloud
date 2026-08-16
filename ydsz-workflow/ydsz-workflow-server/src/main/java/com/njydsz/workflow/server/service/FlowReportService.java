package com.njydsz.workflow.server.service;

import java.util.Map;

/**
 * 流程报表服务。
 *
 * <p>生成流程运行报表。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowReportService {

  /**
   * 生成周报数据。
   *
   * @param tenantId 租户 ID
   * @return 周报数据 Map
   */
  Map<String, Object> generateWeeklyReport(String tenantId);

  /**
   * 生成月报数据。
   *
   * @param tenantId 租户 ID
   * @return 月报数据 Map
   */
  Map<String, Object> generateMonthlyReport(String tenantId);

  /**
   * 生成并推送周报。
   *
   * @param tenantId 租户 ID
   * @return 是否推送成功
   */
  boolean sendWeeklyReport(String tenantId);

  /**
   * 生成并推送月报。
   *
   * @param tenantId 租户 ID
   * @return 是否推送成功
   */
  boolean sendMonthlyReport(String tenantId);
}
