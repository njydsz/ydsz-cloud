package com.njydsz.workflow.server.excel.vo;


import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 流程效率对比 Excel 导出 VO
 *
 * <p>P3-1: 实现 FlowAnalyticsServiceImpl Javadoc 声明的「导出为 Excel」能力。
 * 置于 server/excel/vo 避免反向依赖 + domain 层纯净。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Data
public class FlowEfficiencyExportVO {

  /** 流程编码 */
  @ExcelProperty(value = "流程编码", order = 1, width = 20)
  private String flowCode;

  /** 流程名称 */
  @ExcelProperty(value = "流程名称", order = 2, width = 20)
  private String flowName;

  /** 任务总数 */
  @ExcelProperty(value = "任务总数", order = 3, width = 12)
  private long totalCount;

  /** 已完成数量 */
  @ExcelProperty(value = "已完成数", order = 4, width = 12)
  private long completedCount;

  /** 平均耗时（小时） */
  @ExcelProperty(value = "平均耗时(h)", order = 5, width = 12)
  private double avgDurationHours;

  /** 驳回率（百分比） */
  @ExcelProperty(value = "驳回率(%)", order = 6, width = 12)
  private double rejectionRatePercent;

  /** 逾期率（百分比） */
  @ExcelProperty(value = "逾期率(%)", order = 7, width = 12)
  private double overdueRatePercent;
}
