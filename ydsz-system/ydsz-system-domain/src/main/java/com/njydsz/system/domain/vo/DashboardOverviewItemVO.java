package com.njydsz.system.domain.vo;

import lombok.Data;

/**
 * 工作台概览统计项 VO
 *
 * <p>供 {@code GET /api/dashboard/overview} 返回，对齐前端
 * {@code OverviewItem} 契约（analytics 页顶部统计卡片）。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.system.server.service.DashboardService 数据来源
 */
@Data
public class DashboardOverviewItemVO {

  /** 统计项标题（如「租户总数」） */
  private String title;

  /** 累计总值标题（如「累计租户」） */
  private String totalTitle;

  /** 累计总值 */
  private long totalValue;

  /** 今日/增量值 */
  private long value;
}
